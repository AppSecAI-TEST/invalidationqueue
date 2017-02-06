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
package com.mcherm.invalidationqueue.demoapp;

import com.mcherm.invalidationqueue.SessionData;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ThreadLocalRandom;


/**
 * A sample implementation of the SessionData interface.
 * <p>
 * This implementation allows access to four values: the mandatory sessionId, as well
 * as ssoId, selectedBusiness, and csrfToken. This implementation uses a cookie to
 * store the sessionId, selectedBusiness, and csrfToken. This cookie simply concatenates
 * those three fields together using ':' as a separator. The ssoId is obtained in a
 * completely different way (actually based on a different cookie), since that integrates
 * with Capital One's SSO system.
 */
public class DemoappSessionData implements SessionData {
    private static final String KEY_SESSION_VALUES_COOKIE_NAME = "nsbapp-key-session-values";

    private final ThreadLocal<Boolean> wasModified = new ThreadLocal<>();
    private final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private final ThreadLocal<String> ssoId = new ThreadLocal<>();
    private final ThreadLocal<String> selectedBusiness = new ThreadLocal<>();
    private final ThreadLocal<String> csrfToken = new ThreadLocal<>();


    /**
     * When this is called before beginning to process the request, read the cookie from
     * the http request and set the values accordingly.
     */
    @Override
    public void beginRequest(HttpServletRequest httpServletRequest) {
        String cookieValue = getCurrentCookieValue(httpServletRequest, KEY_SESSION_VALUES_COOKIE_NAME);
        if (cookieValue == null) {
            setValuesForNewSession();
        } else {
            String[] cookieFields = cookieValue.split(":");
            if (cookieFields.length != 3) {
                throw new RuntimeException("Cookie '" + KEY_SESSION_VALUES_COOKIE_NAME + "' has a formatting error.");
            }
            this.wasModified.set(false);
            this.sessionId.set(cookieFields[0]);
            this.selectedBusiness.set(cookieFields[1]);
            this.csrfToken.set(cookieFields[2]);
            this.ssoId.set("dummySSOID"); // FIXME: Needs real implementation, doesn't it?
        }
    }

    /**
     * This is called when no cookie is found and so brand new values are needed for the new session.
     */
    protected void setValuesForNewSession() {
        String sessionNumber = Integer.toString(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE - 1));
        String csrfTokenNumber = Integer.toString(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE - 1));
        this.wasModified.set(true);
        this.sessionId.set("session-" + sessionNumber);
        this.selectedBusiness.set("");
        this.csrfToken.set("csrf-" + csrfTokenNumber);
        this.ssoId.set("dummySSOID");
    }


    /**
     * When this is called after processing the response, if the session data has been
     * modified, send it to the browser in the cookie.
     */
    @Override
    public void endRequest(HttpServletResponse httpServletResponse) {
        if (wasModified.get()) {
            String cookieValue = this.sessionId.get() + ":" + this.selectedBusiness.get() + ":" + this.csrfToken.get();
            Cookie newCookie = new Cookie(KEY_SESSION_VALUES_COOKIE_NAME, cookieValue);
            newCookie.setPath("/");
            httpServletResponse.addCookie(newCookie);
        }

        // -- Clear the threadlocals since we are now exiting this session --
        this.wasModified.remove();
        this.sessionId.remove();
        this.selectedBusiness.remove();
        this.csrfToken.remove();
        this.ssoId.remove();
    }


    /**
     * Subroutine to extract the value of the cookie from the servlet request.
     * @param httpServletRequest
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

    @Override
    public String getSessionId() {
        return sessionId.get();
    }

    /**
     * Returns the single sign on ID (ssoId) of the logged-in user.
     */
    public String getSsoId() {
        return ssoId.get();
    }

    /**
     * Returns the currently selected business.
     */
    public String getSelectedBusiness() {
        return selectedBusiness.get();
    }

    /**
     * Returns the CRSF token.
     */
    public String getCrsfToken() {
        return csrfToken.get();
    }

    /**
     * Call this to change the selected business.
     *
     * @param newSelectedBusiness
     */
    public void setSelectedBusiness(String newSelectedBusiness) {
        this.selectedBusiness.set(newSelectedBusiness);
        this.wasModified.set(true);
    }
}
