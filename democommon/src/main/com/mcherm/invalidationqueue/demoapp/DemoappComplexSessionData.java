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

import com.mcherm.invalidationqueue.SecureCookieBasedSessionData;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;


/**
 * This is an implementation of the SessionData for demoapp which illustrates having
 * several complex things stored in the SessionData.
 * <p>
 * NOTE: This exists only to demonstrate how <code>SecureCookieBasedSessionData</code>
 * is used. It is NOT actually needed in demoapp.
 */
public class DemoappComplexSessionData extends SecureCookieBasedSessionData<DemoappComplexSessionData.SessionDataFields> {


    protected static class SessionDataFields implements Serializable {
        String sessionId;
        String ssoId;
        String selectedBusiness;
        String csrfToken;

        private SessionDataFields(String sessionId, String ssoId, String selectedBusiness, String csrfToken) {
            this.sessionId = sessionId;
            this.ssoId = ssoId;
            this.selectedBusiness = selectedBusiness;
            this.csrfToken = csrfToken;
        }
    }


    @Override
    protected String getPassword() {
        return "some-unguessable-password";
    }

    @Override
    protected SessionDataFields createNewSessionData() {
        String sessionNumber = Integer.toString(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE - 1));
        String csrfTokenNumber = Integer.toString(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE - 1));
        return new SessionDataFields("session-" + sessionNumber, "", "csrf-" + csrfTokenNumber, "dummySSOID");
    }

    @Override
    public String getSessionId() {
        return getSessionData().sessionId;
    }

    /**
     * Returns the single sign on ID (ssoId) of the logged-in user.
     */
    public String getSsoId() {
        return getSessionData().ssoId;
    }

    /**
     * Returns the currently selected business.
     */
    public String getSelectedBusiness() {
        return getSessionData().selectedBusiness;
    }

    /**
     * Returns the CRSF token.
     */
    public String getCrsfToken() {
        return getSessionData().csrfToken;
    }

    /**
     * Call this to change the selected business.
     *
     * @param newSelectedBusiness
     */
    public void setSelectedBusiness(String newSelectedBusiness) {
        SessionDataFields data = getSessionData();
        data.selectedBusiness = newSelectedBusiness;
        modifySessionData(data);
    }
}
