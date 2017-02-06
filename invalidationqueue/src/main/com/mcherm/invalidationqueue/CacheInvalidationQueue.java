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
import java.util.HashSet;
import java.util.Set;


/**
 * This class implements a queue of cache invalidation events that occurred during the lifetime of
 * a user's session. In order to allow instances of the class to be easily injected into all of
 * the places where it might be needed, instances must threadsafe, yet have data specific to the
 * session. This is achieved by storing all information in ThreadLocal variables and by implementing
 * the StatelessSessionBean interface.
 */
public class CacheInvalidationQueue<CIE extends CacheInvalidationEvent> implements Serializable, StatelessSessionBean {
    private static final String cookieName = "invalidationqueue";
    private static final int FIRST_ASCII_VALUE_TO_USE = 65; // Note: could use as low as 33 if needed
    private static final int LARGEST_ASCII_VALUE_TO_USE = 126;
    private static final int MAX_NUMBER_OF_EVENTS = LARGEST_ASCII_VALUE_TO_USE - FIRST_ASCII_VALUE_TO_USE;


    private final Class<CIE> cieClass;
    private final ThreadLocal<Boolean> eventsWereAdded = new ThreadLocal<>();
    private final ThreadLocal<StringBuilder> encodedEvents = new ThreadLocal<>();


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
        encodedEvents.get().append(eventToChar(event));
        eventsWereAdded.set(true);
    }


    /**
     * This returns a Set of all events that occur on or after the given place in
     * the queue.
     *
     * @param placeInQueue position of the first event to include. Must be >= 0.
     */
    public Set<CIE> getNewerEvents(int placeInQueue) {
        final Set<CIE> result = new HashSet<>();
        final byte[] bytes = encodedEvents.get().toString().getBytes(Charset.forName("US-ASCII"));
        if (placeInQueue < bytes.length) {
            for (int i = placeInQueue; i < bytes.length; i++) {
                result.add(eventFromChar(bytes[i], cieClass));
            }
        }
        return result;
    }

    /**
     * This returns the number of events in the queue.
     */
    public int eventCount() {
        return encodedEvents.get().length();
    }


    protected CIE eventFromChar(byte c, Class<CIE> cieClassXXX) {
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
        String previousEncodedEvents = ""; // If no cookie is found (like first time through), assume an empty queue
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    previousEncodedEvents = cookie.getValue();
                }
            }
        }
        encodedEvents.set(new StringBuilder(previousEncodedEvents));
        eventsWereAdded.set(false);
    }

    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        if (eventsWereAdded.get()) {
            Cookie cookie = new Cookie(cookieName, encodedEvents.get().toString());
            cookie.setPath("/");
            httpServletResponse.addCookie(cookie);
        }
        encodedEvents.remove();
        eventsWereAdded.remove();
    }

}
