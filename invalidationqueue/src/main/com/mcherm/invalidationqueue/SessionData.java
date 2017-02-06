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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This is a parent class for the object that retrieves the environment data. A specific
 * example of creating an SessionData can be found in the class DemoappSessionData.
 * <p>
 * Although the ComponentCache stores most of the information that needs to be cached
 * in a session in some out-of-memory storage, there will still be a handful of data
 * values which are needed everywhere: at a bare minimum this will include the session id
 * that is used to look up data in the out-of-memory storage. This is the parent interface
 * for the class that retrieves that data on demand -- each use of the invalidationqueue
 * framework will have its own set of fields and thus its own implementation of this
 * interface, and pieces like the ComponentCache are parameterized by the specific subclass
 * of SessionData.
 * <p>
 * This needs to return data that is specific to the user session which is currently being
 * processed. There will be a single instance of this class for the whole server (that's so
 * we can inject it to places that need to access the data), which means that it CANNOT be
 * built as a simple POJO. Instead, it needs to store data in ThreadLocal variables which
 * get initialized when beginRequest() is called and reset to null when endRequest() is
 * called.
 * <p>
 * And that explains the purpose of the <code>beginRequest()</code> and
 * <code>endRequest()</code> methods, which are guaranteed to be called by a ServletFilter
 * before (and after, respectively) any code is executed for a given session. This allows
 * the data fields to be stored in a cookie, via URL-rewriting, or passed in HTTP headers
 * from some other location.
 */
public interface SessionData extends StatelessSessionBean {

    /**
     * Retrieves the SessionId for this session. The SessionId is a String containing only
     * certain characters (it must match the regular expression <code>[a-zA-Z0-9+/.=]+</code>)
     * which must be unique for each session. It is used as a key for storing and retrieving
     * data.
     */
    public String getSessionId();

    /**
     * When a request is being processed on the server, this method will be called before
     * any data access methods (like <code>getSessionId()</code>) are called on this thread.
     * It should retrieve the information and store it in ThreadLocal fields.
     *
     * @param httpServletRequest the request made on this session.
     */
    @Override
    public void beginRequest(HttpServletRequest httpServletRequest);

    /**
     * After a request is being processed on the server, this method will be called. This
     * gives the object implementing SessionData an opportunity to clear out any ThreadLocal
     * variables, since it is guaranteed that no data access methods (like <code>getSessionId()</code>)
     * will be called on this thread after <code>endRequest()</code> and before the next call
     * to <code>beginRequest()</code>.
     * <p>
     * The method will also be passed the httpServletResponse. This gives the object
     * implementing SessionData an opportunity to do things like writing a cookie.
     *
     * @param httpServletResponse the response which will be returned.
     */
    @Override
    public void endRequest(HttpServletResponse httpServletResponse);

}
