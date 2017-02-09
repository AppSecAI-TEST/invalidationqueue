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

import com.mcherm.invalidationqueue.util.SecureSerializer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;

/**
 * This is an abstract class that implements SessionData by storing the state of
 * the session in a cookie. The cookie will be loaded at the beginning of each
 * call and written back to the browser after any request in which the SessionData
 * was changed. The cookie will be encrypted so it cannot be modified by the client.
 * <p>
 * To subclass this, the caller needs to provide a type ("T") which contains data
 * fields to be saved. If the only field needed is the session id, then there is no
 * need to use this class; simply use <code>SimpleSessionData</code> instead.
 */
public abstract class SecureCookieBasedSessionData<T extends Serializable> implements SessionData {
    private final static String DEFAULT_COOKIE_NAME = "key-session-values";
    private final static SecureSerializer secureSerializer = new SecureSerializer();


    private final ThreadLocal<T> threadLocalData = new ThreadLocal<T>();
    private final ThreadLocal<Boolean> wasModified = new ThreadLocal<>();


    /**
     * Subclasses can override this to use a different cookie name.
     */
    protected String getCookieName() {
        return DEFAULT_COOKIE_NAME;
    }


    /**
     * Subclasses must override this to provide the password used to encrypt the data
     * in the cookie. It must be at least 32 bytes long.
     */
    protected abstract String getPassword();

    /**
     * Subclasses implement this to generate the data values used in a brand-new session.
     */
    protected abstract T createNewSessionData();

    /**
     * Subclasses can use this to access the underlying session data. Any time that the
     * session data is changed, they should call <code>modifySessionData()</code> in order
     * to ensure that the change will be written out to the cookie.
     */
    protected T getSessionData() {
        return threadLocalData.get();
    }


    /**
     * Subclasses MUST call this whenever they modify the sessionData in order to ensure that
     * the change will be written out to the cookie.
     *
     * @param newSessionData the newly modified value
     */
    protected void modifySessionData(T newSessionData) {
        wasModified.set(true);
        threadLocalData.set(newSessionData);
    }

    /**
     * When this is called before beginning to process the request, read the cookie from
     * the http request and set the values accordingly.
     */
    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        String cookieValue = getCurrentCookieValue(httpServletRequest, getCookieName());
        if (cookieValue == null) {
            final T newSessionData = createNewSessionData();
            threadLocalData.set(newSessionData);
            wasModified.set(true);
        } else {
            Serializable dataFromCookie = secureSerializer.deserializeFromTamperproofAscii(cookieValue, getPassword());
            threadLocalData.set((T) dataFromCookie);
            wasModified.set(false);
        }
    }



    /**
     * When this is called after processing the response, if the session data has been
     * modified, send it to the browser in the cookie.
     */
    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        if (wasModified.get()) {
            String cookieValue = secureSerializer.serializeToTamperproofAscii(threadLocalData.get(), getPassword());
            Cookie newCookie = new Cookie(getCookieName(), cookieValue);
            newCookie.setPath("/");
            httpServletResponse.addCookie(newCookie);
        }

        // -- Clear the threadlocals since we are now exiting this session --
        wasModified.remove();
        threadLocalData.remove();
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
