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

import com.mcherm.invalidationqueue.util.BufferingHttpServletResponse;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


/**
 * This filter exists in order to initialize some <code>StatelessSessionBean</code>
 * objects. It will call <code>beginRequest()</code> on them before calling the actual
 * servlet and will call <code>endRequest()</code> on them after the servlet finishes.
 * This gives the <code>StatelessSessionBean</code> objects a chance to set their
 * ThreadLocal variables before the Servlet runs.
 * <p>
 * In addition, this jumps through some special hoops to ensure that the
 * <code>StatelessSessionBean</code>s are allowed to call things like
 * <code>setCookie()</code> in the <code>endRequest()</code> method, even though that
 * requires jumping through some hoops to buffer the output until after that step is
 * complete.
 * <p>
 * Because there is no way to use <code>@Autowired</code> to have Spring inject values
 * into a ServletFilter, the class <code>ServletFilterInitializer</code> exists to
 * help initialize these instances correctly. See that javadoc for a full explanation
 * and instructions on how to use this class.
 */
public class StatelessSessionServletFilter implements Filter {

    // ===== Static Stuff =====

    /**
     * A static list of all instances of this class, which is maintained so that
     * ServletFilterInitializer can initialize them.
     */
    private static List<StatelessSessionServletFilter> listOfInstances = new ArrayList<>();

    /**
     * Returns a list of all the instances that have been created. Made package-visible so
     * that ServletFilterInitializer can call it.
     */
    static List<StatelessSessionServletFilter> getListOfInstances() {
        synchronized (StatelessSessionServletFilter.class) {
            return listOfInstances;
        }
    }


    // ===== Instance Variables and Constructor =====

    /**
     * The list of beans on which to call <code>beginRequest()</code> and <code>endRequest()</code>.
     */
    private List<StatelessSessionBean> statelessSessionBeans;


    /** Constructor. */
    public StatelessSessionServletFilter() {
        statelessSessionBeans = null;
        synchronized (StatelessSessionServletFilter.class) {
            listOfInstances.add(this);
        }
    }


    // ===== Methods =====

    /**
     * Call this JUST ONCE to set the list of stateless session beans. It is an error
     * to call it more than once.
     *
     * @param statelessSessionBeans the list of StatelessSessionBeans in the order in
     *     which they should have <code>beginSession()</code> called.
     */
    public void setStatelessSessionBeans(List<StatelessSessionBean> statelessSessionBeans) {
        if (this.statelessSessionBeans == null) {
            this.statelessSessionBeans = statelessSessionBeans;
        } else {
            throw new RuntimeException("Attempt to call setStatelessSessionBeans() more than once.");
        }
    }

    /**
     * Returns the current list of stateless session beans, or null if they have not been set yet.
     */
    public List<StatelessSessionBean> getStatelessSessionBeans() {
        return statelessSessionBeans;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {

            // --- Stuff that happens in the interceptor before the servlet runs ---
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
            BufferingHttpServletResponse bufferedResponse = new BufferingHttpServletResponse(httpServletResponse);


            for (StatelessSessionBean statelessSessionBean : statelessSessionBeans) {
                statelessSessionBean.beginRequest(httpServletRequest);
            }

            try {

                // --- Run the servlet ---
                filterChain.doFilter(servletRequest, bufferedResponse);

            } finally {

                // --- Stuff that happens in the interceptor after the servlet runs ---
                // Go through the list backward
                ListIterator<StatelessSessionBean> li = statelessSessionBeans.listIterator(statelessSessionBeans.size());
                while (li.hasPrevious()) {
                    li.previous().endRequest(bufferedResponse);
                }

                // --- Stop buffering the response ---
                bufferedResponse.flushBufferedOutput();

            }

        } catch(Throwable err) {

            // --- For any throwable, log it ---
            err.printStackTrace();
            throw err;
        }
    }

    @Override
    public void destroy() {
    }

}
