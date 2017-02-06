/*
Copyright 2017 Michael Chermside

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.mcherm.invalidationqueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;


/**
 * An implementation of the ComponentCache interface, using the provided Storage, which provides
 * the underlying key-value storage. Storage keys will be the session id, a colon (":"), and then
 * the field name.
 */
public class ComponentCacheImpl<CIE extends CacheInvalidationEvent, SD extends SessionData> implements ComponentCache<CIE>, StatelessSessionBean {
    private final String ENTRIES_JSON_FIELD_NAME = "entries";
    private final String NAME_JSON_FIELD_NAME = "name";
    private final String EVENTS_JSON_FIELD_NAME = "invalidatingEvents";
    private final String REFRESH_FUNCTION_JSON_FIELD_NAME = "refreshFunction";

    private static final String PLACE_IN_QUEUE_ENTRY_NAME = "invalidationqueuePosition";


    /**
     * An immutable data object containing the metadata about a single entry in the component
     * cache. It contains things like the field's name, the list of events that would render
     * the current value invalid, and (optionally), the function to call to obtain an up-to-date
     * value for the field.
     */
    protected static class EntryMetadata<CIE2 extends CacheInvalidationEvent> {
        private final String name;
        private final Set<CIE2> invalidators;
        private final Callable<String> refreshFunction;

        /** Constructor. */
        public EntryMetadata(String name, Set<CIE2> invalidators, Callable<String> refreshFunction) {
            this.name = name;
            this.invalidators = Collections.unmodifiableSet(invalidators);
            this.refreshFunction = refreshFunction;
        }

        /**
         * Returns the name of the entry.
         */
        public String getName() {
            return name;
        }

        /**
         * Retrieves the set of events which would render this field invalid. If any of the
         * events have occurred, then the ComponentCache will clear the entry.
         */
        public Set<CIE2> getInvalidators() {
            return invalidators;
        }

        /**
         * If there is a function to call which will retrieve the value of this field on-demand,
         * then this returns it. If not, then this returns null.
         */
        public Callable<String> getRefreshFunction() {
            return refreshFunction;
        }
    }


    private final StorageMechanism storageMechanism;
    private final SD sessionData;
    private final CacheInvalidationQueue<CIE> cacheInvalidationQueue;
    private final Map<String,EntryMetadata<CIE>> entryMetadataMap;


    /**
     * Constructor. It is passed all of the initialization information required to properly set up the
     * component cache.
     *
     * @param storageMechanism the actual implementation of a key-value store
     * @param sessionData the object that provides access to the (minimal) data that IS part of the
     *                    session. (Stored somehow, perhaps a cookie.)
     * @param entriesConfiguration an InputStream that will read the file containing the configuration
     *           for the cache, including the list of what entries can be stored in the cache. This
     *           file may be found on the classpath.
     * @param entriesFileName the filename of the file that entriesConfiguration was loaded from. Used
     *           for error reporting.
     * @param enumClass the actual class for the enum
     */
    public ComponentCacheImpl(
            StorageMechanism storageMechanism,
            SD sessionData,
            CacheInvalidationQueue<CIE> cacheInvalidationQueue,
            InputStream entriesConfiguration,
            String entriesFileName,
            Class<CIE> enumClass) {
        this.storageMechanism = storageMechanism;
        this.sessionData = sessionData;
        this.cacheInvalidationQueue = cacheInvalidationQueue;

        Collection<EntryMetadata<CIE>> entryMetadatas = readEntryMetadataFromFile(entriesConfiguration, entriesFileName, enumClass);
        this.entryMetadataMap = new HashMap<>();
        for (EntryMetadata<CIE> entryMetadata : entryMetadatas) {
            entryMetadataMap.put(entryMetadata.name, entryMetadata);
        }
    }


    /**
     * This reads the entries file and returns the content as a JSONObject.
     */
    private static JSONObject readEntriesFile(InputStream entriesConfiguration, String entriesFileName) {
        if (entriesConfiguration == null) {
            throw new RuntimeException("Could not read open file \"" + entriesFileName + "\".");
        }
        BufferedReader streamReader;
        try {
            streamReader = new BufferedReader(new InputStreamReader(entriesConfiguration, "UTF-8"));
        } catch (UnsupportedEncodingException err) {
            throw new Error("Required encoding not supported.", err); // JVM promises it can't happen so this is an Error
        }
        StringBuilder fileContents = new StringBuilder();

        try {
            String line = streamReader.readLine();
            while (line != null) {
                fileContents.append(line);
                line = streamReader.readLine();
            }
        } catch(IOException err) {
            throw new RuntimeException("Could not read config file \"" + entriesFileName + "\".", err);
        }

        return new JSONObject(fileContents.toString());
    }


    /**
     * This reads the entries file and converts it to a collection of EntryMetadata.
     */
    private Collection<EntryMetadata<CIE>> readEntryMetadataFromFile(InputStream entriesConfiguration, String entriesFileName, Class<CIE> enumClass) {
        JSONObject entriesJSON = readEntriesFile(entriesConfiguration, entriesFileName);
        try {
            List<EntryMetadata<CIE>> result = new ArrayList<>();
            JSONArray entriesList = entriesJSON.getJSONArray(ENTRIES_JSON_FIELD_NAME);
            for (int i=0; i<entriesList.length(); i++) {
                // -- read the JSON entry --
                JSONObject entryJSON = entriesList.getJSONObject(i);
                String name = entryJSON.getString(NAME_JSON_FIELD_NAME);
                JSONArray invalidatingEvents = entryJSON.getJSONArray(EVENTS_JSON_FIELD_NAME);
                String refreshFunctionBeanName;
                if (!entriesJSON.has(REFRESH_FUNCTION_JSON_FIELD_NAME) || entriesJSON.isNull(REFRESH_FUNCTION_JSON_FIELD_NAME)) {
                    refreshFunctionBeanName = null;
                } else {
                    refreshFunctionBeanName = entryJSON.getString(REFRESH_FUNCTION_JSON_FIELD_NAME);
                }

                // -- create list of events --
                Set<CIE> invalidators = new TreeSet<>();
                for (int j=0; j<invalidatingEvents.length(); j++) {
                    String eventName = invalidatingEvents.getString(j);
                    try {
                        CIE[] enumValues = enumClass.getEnumConstants();
                        for (CIE enumValue : enumValues) {
                            if (enumValue.toString().equals(eventName)) {
                                invalidators.add(enumValue);
                            }
                        }
                    } catch(IllegalArgumentException err) {
                        throw new RuntimeException("Could not parse config file \"" + entriesFileName +
                                "\". The value \"" + eventName + "\" is not a valid " +
                                enumClass.getName());
                    }
                }

                // -- find the actual function from the bean --
                // FIXME: Need to handle getting the bean for a refreshFunction. For now it's hard-coded to null
                Callable<String> refreshFunction = null;

                // -- create metadata object --
                EntryMetadata<CIE> entryMetadata =
                        new EntryMetadata<>(name, invalidators, refreshFunction);
                result.add(entryMetadata);
            }
            return result;
        } catch (JSONException err) {
            throw new RuntimeException("Could not parse config file \"" + entriesFileName + "\".", err);
        }
    }



    /**
     * Given a field name, returns the storage key.
     */
    private String storageKeyFromName(String name) {
        if (!entryMetadataMap.keySet().contains(name)) {
            throw new RuntimeException("The ComponentCache is not configured to support storing the field '" + name + "'.");
        }
        return sessionData.getSessionId() + ":" + name;
    }


    @Override
    public void storeEntry(String name, String value) {
        storageMechanism.put(storageKeyFromName(name), value);
    }

    @Override
    public void clearEntry(String name) {
        storageMechanism.put(storageKeyFromName(name), null);
    }

    @Override
    public String getEntry(String name) {
        String result = storageMechanism.get(storageKeyFromName(name));
        if (result != null) {
            return result;
        }
        // It was null, but we might be able to refresh the value now
        Callable<String> refreshFunction = entryMetadataMap.get(name).refreshFunction;
        if (refreshFunction == null) {
            // We don't have a way to refresh this field, so we'll just return null
            return null;
        }
        else {
            String newValue;
            try {
                newValue = refreshFunction.call();
            } catch(Exception err) {
                // FIXME: Should probably LOG the error.
                // Refreshing didn't work, so we'll have to return null
                return null;
            }
            // Store this new value, then return it
            storeEntry(name, newValue);
            return newValue;
        }
    }


    /**
     * This clears from the cache all fields that have been made invalid by new events in the
     * eventCache. It is called in beginRequest() after the CacheInvalidationQueue has been
     * initialized.
     */
    private void applyNewEventsFromQueue() {
        // -- Find where we are in the queue --
        final String placeInQueueString = getEntry(PLACE_IN_QUEUE_ENTRY_NAME);
        final Integer placeInQueue;
        if (placeInQueueString == null) {
            placeInQueue = 0; // No record, so assume we're at the start of the queue
        } else {
            placeInQueue = Integer.parseInt(placeInQueueString);
        }

        // -- See if there is anything new --
        if (cacheInvalidationQueue.eventCount() > placeInQueue) {
            // -- Get events --
            Set<CIE> newEvents = cacheInvalidationQueue.getNewerEvents(placeInQueue);
            System.out.println("New events to process are " + newEvents + "."); // FIXNE: Remove, but only later after I learn.
            for (EntryMetadata<CIE> entryMetadata : entryMetadataMap.values()) {
                // -- Clear affected entries --
                Set<CIE> eventsThatWouldInvalidateThisEntry = new HashSet<>(entryMetadata.getInvalidators()); // copy it as we will modify
                eventsThatWouldInvalidateThisEntry.retainAll(newEvents);
                if (! eventsThatWouldInvalidateThisEntry.isEmpty()) {
                    // -- go ahead and invalidate this one --
                    clearEntry(entryMetadata.getName());
                    System.out.println("Clearing entry for " + entryMetadata.getName() + "."); // FIXME: Remove, but only later after I learn some
                }
            }
            // -- Update place in queue --
            storeEntry(PLACE_IN_QUEUE_ENTRY_NAME, Integer.toString(cacheInvalidationQueue.eventCount()));
        }
    }

    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        applyNewEventsFromQueue();
    }

    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
    }

}
