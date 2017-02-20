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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final static String ENTRIES_JSON_FIELD_NAME = "entries";
    private final static String NAME_JSON_FIELD_NAME = "name";
    private final static String TYPE_JSON_FIELD_NAME = "type";
    private final static String EVENTS_JSON_FIELD_NAME = "invalidatingEvents";
    private final static String REFRESH_FUNCTION_JSON_FIELD_NAME = "refreshFunction";


    /**
     * An immutable data object containing the metadata about a single entry in the component
     * cache. It contains things like the field's name, type, the list of events that would render
     * the current value invalid, and (optionally), the function to call to obtain an up-to-date
     * value for the field.
     */
    protected static class EntryMetadata<CIE2 extends CacheInvalidationEvent> {
        private final String name;
        private final Class type;
        private final Set<CIE2> invalidators;
        private final String refreshFunctionName;

        /**
         * Constructor.
         *
         * @param name the name of the field, which will be passed to the <code>storeEntry()</code>,
         *             <code>getEntry()</code>, and <code>clearEntry()</code> methods on the cache.
         * @param type the type of the object that will be stored in this field. Must be either
         *             <code>java.lang.String</code> or anything which can be serialized using the
         *             Jackson library.
         * @param invalidators a set of events which, if they occur, will invalidate any cached
         *                     value, so after such an event the cache will effectively be cleared.
         * @param refreshFunctionName the name used to look up a function which can be called to load
         *     in a new value for this entry if no value is currently available, or null if there is
         *     no such function. For fields where this is null, the <code>getEntry()</code>
         *     method can sometimes return null even if a value had previously
         *     been cached, due to invalidation or due to the cache being unreliable.
         */
        public EntryMetadata(String name, Class type, Set<CIE2> invalidators, String refreshFunctionName) {
            this.name = name;
            this.type = type;
            this.invalidators = Collections.unmodifiableSet(invalidators);
            this.refreshFunctionName = refreshFunctionName;
        }

        /**
         * Returns the name of the entry.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the type of data that will be stored in the field. This will be be either
         * <code>java.lang.String</code> or anything which can be serialized using the
         * Jackson library.
         */
        public Class getType() {
            return type;
        }

        /**
         * Retrieves the set of events which would render this field invalid. If any of the
         * events have occurred, then the ComponentCache will clear the entry.
         */
        public Set<CIE2> getInvalidators() {
            return invalidators;
        }

        /**
         * Returns true if the entry has a function to call which will retrieve the value of
         * the field (used the first time, whenever the cache is cleared, and whenever the
         * cache fails), or false if it does not have such a function.
         */
        public boolean hasRefreshFunction() {
            return refreshFunctionName != null;
        }

        /**
         * If there is a function to call which will retrieve the value of this field on demand,
         * then this returns the string used to look it up (typically Spring bean name). If not,
         * then this returns null.
         */
        public String getRefreshFunctionName() {
            return refreshFunctionName;
        }

    }


    private final ObjectMapper objectMapper;
    private final StorageMechanism storageMechanism;
    private final String componentName;
    private final SD sessionData;
    private final CacheInvalidationQueue<CIE> cacheInvalidationQueue;
    private final Map<String,EntryMetadata<CIE>> entryMetadataMap;


    /**
     * Constructor. It is passed all of the initialization information required to properly set up the
     * component cache.
     *
     * @param objectMapper a Jackson object mapper to use
     * @param storageMechanism the actual implementation of a key-value store
     * @param componentName the name for this component. Must contain only [a-zA-Z0-9].
     * @param sessionData the object that provides access to the (minimal) data that IS part of the
     *                    session. (Stored somehow, perhaps a cookie.)
     * @param cacheInvalidationQueue this is the queue that is used to track when items in the cache
     *           should be invalidated due to changes made by other components
     * @param entriesConfiguration an InputStream that will read the file containing the configuration
     *           for the cache, including the list of what entries can be stored in the cache. This
     *           file may be found on the classpath.
     * @param entriesFileName the filename of the file that entriesConfiguration was loaded from. Used
     *           for error reporting.
     * @param enumClass the actual class for the enum
     */
    public ComponentCacheImpl(
            ObjectMapper objectMapper,
            StorageMechanism storageMechanism,
            String componentName,
            SD sessionData,
            CacheInvalidationQueue<CIE> cacheInvalidationQueue,
            InputStream entriesConfiguration,
            String entriesFileName,
            Class<CIE> enumClass) {
        this.objectMapper = objectMapper;
        this.storageMechanism = storageMechanism;
        this.componentName = componentName;
        this.sessionData = sessionData;
        this.cacheInvalidationQueue = cacheInvalidationQueue;

        final Collection<EntryMetadata<CIE>> entryMetadatas = readEntryMetadataFromFile(entriesConfiguration, entriesFileName, enumClass);
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
        final BufferedReader streamReader;
        try {
            streamReader = new BufferedReader(new InputStreamReader(entriesConfiguration, "UTF-8"));
        } catch (UnsupportedEncodingException err) {
            throw new Error("Required encoding not supported.", err); // JVM promises it can't happen so this is an Error
        }
        final StringBuilder fileContents = new StringBuilder();

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
        final JSONObject entriesJSON = readEntriesFile(entriesConfiguration, entriesFileName);
        try {
            final List<EntryMetadata<CIE>> result = new ArrayList<>();
            final JSONArray entriesList = entriesJSON.getJSONArray(ENTRIES_JSON_FIELD_NAME);
            for (int i=0; i<entriesList.length(); i++) {
                // -- read the JSON entry --
                final JSONObject entryJSON = entriesList.getJSONObject(i);
                final String name = entryJSON.getString(NAME_JSON_FIELD_NAME);
                final String typeString = entryJSON.getString(TYPE_JSON_FIELD_NAME);
                final Class type;
                try {
                    type = Class.forName(typeString);
                } catch(ClassNotFoundException err) {
                    throw new JSONException("Class \"" + typeString + "\" was not found.");
                }
                final JSONArray invalidatingEvents = entryJSON.getJSONArray(EVENTS_JSON_FIELD_NAME);
                final String refreshFunctionName;
                if (!entryJSON.has(REFRESH_FUNCTION_JSON_FIELD_NAME) || entryJSON.isNull(REFRESH_FUNCTION_JSON_FIELD_NAME)) {
                    refreshFunctionName = null;
                } else {
                    refreshFunctionName = entryJSON.getString(REFRESH_FUNCTION_JSON_FIELD_NAME);
                }

                // -- create list of events --
                final Set<CIE> invalidators = new TreeSet<>();
                for (int j=0; j<invalidatingEvents.length(); j++) {
                    final String eventName = invalidatingEvents.getString(j);
                    try {
                        final CIE[] enumValues = enumClass.getEnumConstants();
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

                // -- create metadata object --
                final EntryMetadata<CIE> entryMetadata = new EntryMetadata<>(name, type, invalidators, refreshFunctionName);
                result.add(entryMetadata);
            }
            return result;
        } catch (JSONException err) {
            throw new RuntimeException("Could not parse config file \"" + entriesFileName + "\".", err);
        }
    }

    // FIXME: Consider a variant of the library that DOES depend on Spring.

    /**
     * This is intended to be overridden by subclasses that want to support refresh functions.
     * The method will be passed the name specified in the config file, and should return the
     * actual Callable object that retrieves the value. For instance, the subclass could do a
     * Spring lookup of the name to find the appropriate bean. If there is a problem retrieving
     * the value it should throw an Exception, or return null instead of the actual Callable.
     *
     * @param refreshFunctionBeanName the string specified in the config file
     * @return the actual Callable which should be used to refresh the cache
     */
    protected Callable getRefreshBeanFromName(String refreshFunctionBeanName) throws Exception {
        return null;
    }


    /**
     * Given a field name, returns the storage key.
     */
    private String storageKeyFromName(String name) {
        if (!entryMetadataMap.keySet().contains(name)) {
            throw new RuntimeException("The ComponentCache is not configured to support storing the field '" + name + "'.");
        }
        return sessionData.getSessionId() + ":" + componentName + ":" + name;
    }


    @Override
    public void storeEntry(String name, Object value) {
        // --- Check that name and value are valid ---
        final EntryMetadata<CIE> entryMetadata = entryMetadataMap.get(name);
        if (entryMetadata == null) {
            throw new IllegalArgumentException("The ComponentCache is not configured to support storing the field '" + name + "'.");
        }
        if (!entryMetadata.getType().equals(value.getClass())) {
            throw new IllegalArgumentException("The value was of type \"" + value.getClass().getName() +
                    "\", but the entry " + name + " is configured to expect values of type \"" +
                    entryMetadata.getType().getName() + "\".");
        }

        // --- Store it as either String or Jackson-serialized object ---
        final String stringToStore;
        if (value instanceof CharSequence) {
            stringToStore = value.toString();
        } else {
            try {
                stringToStore = objectMapper.writeValueAsString(value);
            } catch (JsonProcessingException err) {
                throw new RuntimeException("Object of type \"" + value.getClass().getName() +
                        "\" could not be serialized to JSON by Jackson.");
            }
        }
        storageMechanism.put(storageKeyFromName(name), stringToStore);
    }

    @Override
    public void clearEntry(String name) {
        storageMechanism.put(storageKeyFromName(name), null);
    }



    @Override
    public <T> T getEntry(String name) {
        final String valueStr = storageMechanism.get(storageKeyFromName(name));
        final EntryMetadata<CIE> entryMetadata = entryMetadataMap.get(name);
        if (valueStr == null) {
            // The storage didn't have it, but we might be able to refresh the value now
            if (! entryMetadata.hasRefreshFunction()) {
                // We don't have a way to refresh this field, so we'll just return null
                return null;
            }

            // -- find the actual function from the bean --
            final String refreshFunctionName = entryMetadata.getRefreshFunctionName();
            final Callable refreshFunction;
            try {
                refreshFunction = getRefreshBeanFromName(refreshFunctionName);
            } catch(Throwable err) {
                throw new RefreshFunctionNotFoundException(refreshFunctionName, err);
            }
            if (refreshFunction == null) {
                throw new RefreshFunctionNotFoundException(refreshFunctionName, new NullPointerException());
            }

            // -- invoke the refresh function --
            final Object theValue;
            try {
                theValue = refreshFunction.call();
            } catch (Exception err) {
                // Exception trying to fetch the value; log it and return null
                throw new RefreshErrorException(err);
            }

            // -- Make sure it is the right type --
            if (! entryMetadata.getType().isAssignableFrom(theValue.getClass())) {
                throw new RefreshReturnedWrongTypeException(
                        refreshFunctionName, entryMetadata.getType(), theValue.getClass());
            }

            // Store in cache for next time
            try {
                storeEntry(name, theValue);
            } catch (RuntimeException err) {
                throw new RefreshCouldNotBeCachedException(name, theValue);
            }

            // Return the value
            return (T) theValue;

        } else {
            // Storage did have it, but we may need to convert to an object
            try {
                final Class<T> clazz = entryMetadata.getType();
                return objectMapper.readValue(valueStr, clazz);
            } catch (ClassCastException err) {
                throw new ClassCastException("Class cast while reading field " + name + ". " + err.getMessage());
            } catch (IOException err) {
                throw new RuntimeException("IOException reading from string; this shouldn't happen.");
            }
        }
    }


    /**
     * This clears from the cache all fields that have been made invalid by new events in the
     * eventCache. It is called in beginRequest() after the CacheInvalidationQueue has been
     * initialized.
     */
    private void applyNewEventsFromQueue() {
        Set<CIE> newEvents = cacheInvalidationQueue.getNewEvents(componentName);
        // -- Only if there is something new to do... --
        if (newEvents.size() > 0) {
            for (EntryMetadata<CIE> entryMetadata : entryMetadataMap.values()) {
                // -- Clear affected entries --
                final Set<CIE> eventsThatWouldInvalidateThisEntry = new HashSet<>(entryMetadata.getInvalidators()); // copy it as we will modify
                eventsThatWouldInvalidateThisEntry.retainAll(newEvents);
                if (! eventsThatWouldInvalidateThisEntry.isEmpty()) {
                    // -- go ahead and invalidate this one --
                    clearEntry(entryMetadata.getName());
                }
            }
        }
    }

    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        applyNewEventsFromQueue();
    }

    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        cacheInvalidationQueue.markAllEventsConsumed(componentName);
    }

}
