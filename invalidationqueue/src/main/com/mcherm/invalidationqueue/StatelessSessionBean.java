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
 * An interface for Spring-injectable global beans that contain per-session data.
 * <p>
 * Several places we have the same problem: we have a class that we want to make
 * available at scattered places throughout the code (so passing it as a method
 * argument is not viable because then it would wind up having to be passed
 * nearly everywhere). A great solution to that problem is to make sure the class
 * is threadsafe and let Spring instantiate a single instance (for the whole
 * server) and inject it wherever it is needed.
 * <p>
 * However, the class in question has data that is specific to the user session,
 * not global to the server. Spring offers to solve that for us by allowing us
 * to declare the scope of the instance as "session" scope, and Spring will simply
 * store the instance in the application server's Session. Unfortunately, that
 * does not work for us because part of the design of this system is to ensure
 * that there is NOT any server session (because we don't want to enforce
 * sticky-session routing, so we can't guarantee the session will be present on
 * the server the next time a request is made.
 * <p>
 * This interface is used by classes which implement a particular solution to this
 * design dilemma. What they do is to store ALL of the information that is specific
 * to a session in ThreadLocal variables. Then in the <code>beginRequest()</code>
 * method they initialize those variables (possibly from a Cookie). In the
 * <code>endRequest()</code> method they have the opportunity to reset the values
 * to null and (if desired) to update a cookie. The methods will be called from
 * a ServletFilter in order to ensure they occur before (and after) any other
 * methods of the class which might depend on those ThreadLocal variables.
 */
public interface StatelessSessionBean {

    /**
     * When a request is being processed on the server, this method will be called before
     * any other methods are called on this thread. It should retrieve the information
     * and store it in ThreadLocal fields.
     *
     * @param httpServletRequest the request made on this session.
     */
    public void beginRequest(HttpServletRequest httpServletRequest);

    /**
     * After a request is being processed on the server, this method will be called. This
     * gives the object implementing StatelessSessionBean an opportunity to clear out any
     * ThreadLocal variables, and to write out a Cookie (or otherwise modify the response)
     * if desired.
     *
     * @param httpServletResponse the response which will be returned.
     */
    public void endRequest(HttpServletResponse httpServletResponse);
}
