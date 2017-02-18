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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


// FIXME: Also consider encrypting the cookie so it can't be modified.

// FIXME: Future Design Idea:
// FIXME:
// FIXME: Create an interceptor that sets up the session (and saves it afterward) to be used to
// FIXME: transition an existing system using sessions.
// FIXME:


/**
 * This class implements a queue of cache invalidation events that occurred during the lifetime of
 * a user's session. In order to allow instances of the class to be easily injected into all of
 * the places where it might be needed, instances must threadsafe, yet have data specific to the
 * session. This is achieved by storing all information in ThreadLocal variables and by implementing
 * the StatelessSessionBean interface.
 */
public class CacheInvalidationQueue<CIE extends CacheInvalidationEvent> implements Serializable, StatelessSessionBean {
    private static final String cookieName = "invalidationqueue";
    private static final int FIRST_ASCII_VALUE_TO_USE = 65;
    private static final int LARGEST_ASCII_VALUE_TO_USE = 126;
    private static final int MAX_NUMBER_OF_EVENTS = LARGEST_ASCII_VALUE_TO_USE - FIRST_ASCII_VALUE_TO_USE;
    private static final int EVENTS_PER_BLOCK = 256;


    /**
     * Just a class to contain the information that is specific to each session and is stored in a ThreadLocal.
     * The object can write itself out to a string (containing only printable ASCII, to be stored in a cookie)
     * or read itself in from such a string.
     * <p>
     * The format of the string is: <code>'[' <blocks-discarded> "|" ( <component-name> ":" <position-value> + "." )*
     * "|" <next-most-recent-block> <most-recent-block> ']'</code>
     * <p>
     * Since this is just for private use inside this class, I didn't bother with getters or setters.
     */
    private static class InstanceData {
        private boolean hasBeenModified;
        private Map<String, Integer> positionByComponent; // if not found, assume zero
        private StringBuilder mostRecentEvents; // always 0 <= mostRecentEvents.length() <= EVENTS_PER_BLOCK
        private String nextMostRecentEvents; // either null or a String of length EVENTS_PER_BLOCK
        private int numBlocksDiscarded;

        /** Constructor */
        private InstanceData(Map<String, Integer> positionByComponent,
                            StringBuilder mostRecentEvents,
                            String nextMostRecentEvents,
                            int numBlocksDiscarded) {
            this.hasBeenModified = false;
            this.positionByComponent = positionByComponent;
            this.mostRecentEvents = mostRecentEvents;
            this.nextMostRecentEvents = nextMostRecentEvents;
            this.numBlocksDiscarded = numBlocksDiscarded;
        }

        /** Write itself out to a cookie string. */
        private String writeCookieString() {
            StringBuilder result = new StringBuilder();
            result.append('[');
            result.append(Integer.toString(numBlocksDiscarded));
            result.append('|');
            for (Map.Entry<String, Integer> entry : positionByComponent.entrySet()) {
                result.append(entry.getKey());
                result.append(':');
                result.append(Integer.toString(entry.getValue()));
                result.append('.');
            }
            result.append('|');
            if (nextMostRecentEvents != null) {
                assert nextMostRecentEvents.length() == EVENTS_PER_BLOCK;
                result.append(nextMostRecentEvents);
            }
            assert mostRecentEvents.length() >= 0 && mostRecentEvents.length() <= EVENTS_PER_BLOCK;
            result.append(mostRecentEvents.toString());
            result.append(']');
            return result.toString();
        }

        /**
         * Given a cookie string, this parses it and produces a new InstanceData.
         */
        private static InstanceData parseCookieString(String cookieString) throws InvalidCookieException {
            // -- break it into sections ---
            if (! cookieString.startsWith("[") || ! cookieString.endsWith("]")) {
                throw new InvalidCookieException();
            }
            final String[] vBarSplitParts = cookieString.substring(1,cookieString.length()-1).split("\\|");
            if (vBarSplitParts.length != 3) {
                throw new InvalidCookieException();
            }
            final String blocksDiscardedStr = vBarSplitParts[0];
            final String positionMapStr = vBarSplitParts[1];
            final String eventsStr = vBarSplitParts[2];

            // -- numBlocksDiscarded --
            final int numBlocksDiscarded;
            try {
                numBlocksDiscarded = Integer.parseInt(blocksDiscardedStr);
            } catch(NumberFormatException err) {
                throw new InvalidCookieException();
            }

            // -- map of positions by component --
            final Map<String,Integer> positionByComponent = new TreeMap<>();
            if (! positionMapStr.isEmpty()) {
                final String[] entryStrings = positionMapStr.split("\\.");
                // Note: String.split() omits trailing empty strings, so the trailing '.' isn't a problem
                for (String entryString : entryStrings) {
                    final String[] keyAndValue = entryString.split(":");
                    if (keyAndValue.length != 2) {
                        throw new InvalidCookieException();
                    }
                    try {
                        positionByComponent.put(keyAndValue[0], Integer.parseInt(keyAndValue[1]));
                    } catch(NumberFormatException err) {
                        throw new InvalidCookieException();
                    }
                }
            }

            // -- blocks of events --
            final String nextMostRecentEvents;
            final StringBuilder mostRecentEvents;
            if (eventsStr.length() <= EVENTS_PER_BLOCK) {
                nextMostRecentEvents = null;
                mostRecentEvents = new StringBuilder(eventsStr);
            } else {
                nextMostRecentEvents = eventsStr.substring(0, EVENTS_PER_BLOCK);
                mostRecentEvents = new StringBuilder(eventsStr.substring(EVENTS_PER_BLOCK));
            }

            // -- return the result --
            return new InstanceData(positionByComponent, mostRecentEvents, nextMostRecentEvents, numBlocksDiscarded);
        }

    }

    /** Thrown when cookie we are parsing is invalid. */
    private static class InvalidCookieException extends Exception {
    }

    private final Class<CIE> cieClass;
    private final ThreadLocal<InstanceData> instanceData = new ThreadLocal<>();


    /**
     * Constructor.
     *
     * @param cieClass the Class object for the actual CIE class; needed because of how type parameters
     *           work in Java (erasure).
     */
    public CacheInvalidationQueue(Class<CIE> cieClass) {
        this.cieClass = cieClass;
    }


    /**
     * Add an event to the queue.
     */
    public void addEvent(CIE event) {
        InstanceData data = instanceData.get();
        // -- Make sure there is space to add another event --
        if (data.mostRecentEvents.length() >= EVENTS_PER_BLOCK) {
            if (data.nextMostRecentEvents != null) {
                // -- Throw away oldest block --
                data.nextMostRecentEvents = null;
                data.numBlocksDiscarded = data.numBlocksDiscarded + 1;
            }
            // -- Move this block to second place, then add it to a new block --
            data.nextMostRecentEvents = data.mostRecentEvents.toString();
            data.mostRecentEvents = new StringBuilder("");
        }
        // -- Now add the new event to the end of the current block --
        data.mostRecentEvents.append(eventToChar(event));
        data.hasBeenModified = true;
    }



    /**
     * Passed a StringBuilder, this takes all events after placeInQueue in that String and adds
     * them to the <code>setToUpdate</code>. Modifies <code>setToUpdate</code>.
     */
    private void collectEventsAfter(CharSequence characters, int placeInQueue, Set<CIE> setToUpdate) {
        assert(placeInQueue >= 0);
        assert(placeInQueue <= characters.length());
        final byte[] bytes = characters.toString().getBytes(Charset.forName("US-ASCII"));
        for (int i = placeInQueue; i < bytes.length; i++) {
            setToUpdate.add(eventFromChar(bytes[i]));
        }
    }

    /**
     * Returns a Set of all events that have occurred since this component was last invoked.
     * In rare cases where there have been more than 2*EVENTS_PER_BLOCK events since the last
     * time this component was invoked, this will return a set of ALL possible events instead.
     *
     * @param componentName the name of the component for which new events should be returned
     */
    public Set<CIE> getNewEvents(String componentName) {
        final Set<CIE> result = new HashSet<>();
        final InstanceData data = instanceData.get();
        final int placeInQueue = data.positionByComponent.getOrDefault(componentName, 0);
        final int eventsDiscarded = data.numBlocksDiscarded * EVENTS_PER_BLOCK;
        final int eventsInNextMostRecentBlock = data.nextMostRecentEvents == null ? 0 : EVENTS_PER_BLOCK;
        final int currentQueueLength = eventsDiscarded + eventsInNextMostRecentBlock + data.mostRecentEvents.length();
        if (placeInQueue < currentQueueLength) {
            if (placeInQueue < eventsDiscarded) {
                // -- We lost some blocks. So return ALL possible events.
                result.addAll(Arrays.asList(cieClass.getEnumConstants()));
            } else {
                final int placeInWhatWeHave = placeInQueue - eventsDiscarded;
                final int placeInLastBlock;
                if (placeInWhatWeHave < EVENTS_PER_BLOCK && data.nextMostRecentEvents != null) {
                    // -- We need to read some from the nextMostRecent block --
                    collectEventsAfter(data.nextMostRecentEvents, placeInWhatWeHave, result);
                    placeInLastBlock = 0; // need to process ALL of the last block
                } else {
                    placeInLastBlock =
                            data.nextMostRecentEvents == null ? placeInWhatWeHave : placeInWhatWeHave - EVENTS_PER_BLOCK;
                }
                assert placeInLastBlock >= 0;
                assert placeInLastBlock < data.mostRecentEvents.length(); // since we know placeInQueue < currentQueueLength
                // -- We need to read some from the mostRecent block --
                collectEventsAfter(data.mostRecentEvents, placeInLastBlock, result);
            }
            // -- move this component's position forward --
            data.hasBeenModified = true;
            data.positionByComponent.put(componentName, currentQueueLength);
        }
        return result;
    }


    /**
     * This is called to update the position of the given component to the current end of the
     * event queue.
     *
     * @param componentName the name of the component for which the position should be updated
     */
    public void markAllEventsConsumed(String componentName) {
        final InstanceData data = instanceData.get();
        final int eventsDiscarded = data.numBlocksDiscarded * EVENTS_PER_BLOCK;
        final int eventsInNextMostRecentBlock = data.nextMostRecentEvents == null ? 0 : EVENTS_PER_BLOCK;
        final int currentQueueLength = eventsDiscarded + eventsInNextMostRecentBlock + data.mostRecentEvents.length();
        data.positionByComponent.put(componentName, currentQueueLength);
    }


    protected CIE eventFromChar(byte c) {
        CIE[] enumConstants = cieClass.getEnumConstants();
        for (CIE cie : enumConstants) {
            if (c == FIRST_ASCII_VALUE_TO_USE + cie.ordinal()) {
                return cie;
            }
        }
        throw new RuntimeException("Unexpected coded value '" + c + "' for enum " + cieClass.getSimpleName() + ".");
    }

    protected char eventToChar(CIE event) {
        int ordinal = event.ordinal();
        assert(ordinal >= 0 && ordinal < MAX_NUMBER_OF_EVENTS);
        byte byteValue = (byte)(FIRST_ASCII_VALUE_TO_USE + ordinal);
        char[] chars = Character.toChars(byteValue);
        assert(chars.length == 1); // always 1 because we don't use extended code pages; we don't even use multi-byte characters
        return chars[0];
    }


    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        assert instanceData.get() == null;
        // --- Get the cookie ---
        String cookieString = null;
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    cookieString = cookie.getValue();
                }
            }
        }

        InstanceData newSessionData;
        if (cookieString == null) {
            // --- must be a brand new session ---
            newSessionData = new InstanceData(new TreeMap<>(), new StringBuilder(), null, 0);
        } else {
            // --- set instanceData from the cookie ---
            try {
                newSessionData = InstanceData.parseCookieString(cookieString);
            } catch(InvalidCookieException err) {
                throw new RuntimeException("Error in cache invalidation queue cookie.");
            }
        }
        this.instanceData.set(newSessionData);
    }

    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        InstanceData data = instanceData.get();
        if (data.hasBeenModified) {
            Cookie cookie = new Cookie(cookieName, data.writeCookieString());
            cookie.setPath("/");
            httpServletResponse.addCookie(cookie);
        }
        instanceData.remove();
    }

}
