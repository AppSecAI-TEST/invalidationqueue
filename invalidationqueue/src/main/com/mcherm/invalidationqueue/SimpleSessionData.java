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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;


/**
 * An implementation of SessionData that does not store anything other than a session id.
 * Most applications can use this SessionData.
 */
public class SimpleSessionData implements SessionData {
    private final static String COOKIE_NAME = "session-id";

    private final SecureRandom secureRandom; // NOTE: as of Java 1.7, this is guaranteed to be threadsafe

    private ThreadLocal<String> sessionId = new ThreadLocal<>();
    private ThreadLocal<Boolean> wasModified = new ThreadLocal<>();


    /** Constructor. */
    public SimpleSessionData() {
        // --- Create a SecureRandom we can use ---
        SecureRandom theSecureRandom;
        try {
            theSecureRandom = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException err) {
            theSecureRandom = null; // Later on, things will fail with a NullPointerException, but we cannot throw from here
        }
        secureRandom = theSecureRandom;
    }


    @Override
    public String getSessionId() {
        return sessionId.get();
    }


    /**
     * When this is called before beginning to process the request, read the cookie from
     * the http request and set the sessionId accordingly.
     */
    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        String cookieValue = getCurrentCookieValue(httpServletRequest, COOKIE_NAME);
        if (cookieValue == null) {
            int sessionNumber = secureRandom.nextInt(Integer.MAX_VALUE);
            sessionId.set("session-" + Integer.toString(sessionNumber));
            wasModified.set(true);
        } else {
            sessionId.set(cookieValue);
            wasModified.set(false);
        }
    }


    /**
     * Write out the cookie if wasModified is true (which will ONLY be when the
     * session first starts, as the session id never changes during the session).
     */
    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        if (wasModified.get()) {
            Cookie newCookie = new Cookie(COOKIE_NAME, sessionId.get());
            newCookie.setPath("/");
            httpServletResponse.addCookie(newCookie);
        }

        // -- Clear the threadlocals since we are now exiting this session --
        wasModified.remove();
        sessionId.remove();
    }


    /**
     * Subroutine to extract the value of the cookie from the servlet request.
     * @param httpServletRequest the servlet request which is expected to contain a cookie header
     * @return the value of the cookie OR null if the cookie is not present
     */
    private String getCurrentCookieValue(HttpServletRequest httpServletRequest, String cookieName) {
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // If we got here, the cookie wasn't found.
        return null;
    }
}
