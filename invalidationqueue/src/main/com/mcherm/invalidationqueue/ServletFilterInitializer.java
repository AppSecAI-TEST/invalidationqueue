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

import javax.annotation.PostConstruct;
import java.util.List;


/**
 * This manually wires in fields of the DemoappServletFilter.
 * <p>
 * In an actual project, delcare a <code>StatelessSessionServletFilter</code> in the web.xml
 * file. Then create a subclass of <code>ServletFilterInitializer</code> which has fields for
 * any <code>StatelessSessionBean</code> objects, all injected by Spring (probably that means
 * annotating them with <code>@Autowired</code>). Finally, create an instance of the
 * <code>ServletFilterInitializer</code> subclass within the Spring context
 * (<code>applicationContext.xml</code> or a class annotated with <code>@Configuration</code>).
 * The servlet container will create the <code>StatelessSessionServletFilter</code> -- since it
 * is not created by Spring it cannot use <code>@Autowired</code> annotations -- but then the
 * <code>ServletFilterInitializer</code> WILL be created by Spring and CAN have the proper
 * instances injected, and it will manually add them to the
 * <code>StatelessSessionServletFilter</code>.
 * <p>
 * Let's walk through that slowly. You see, there's a problem: The StatelessSessionServletFilter
 * is created by Tomcat (or whatever servlet container you are using), not by Spring, so
 * if we declared some its fields to be <code>@Autowired</code>, it wouldn't work: Spring never
 * gets a chance to inject the values. Therefore, we will declare a bean that is an instance of
 * this class inside of the applicationContext.xml file, and Spring will autowire the
 * fields into THAT class, then IT will manually wire the values into the
 * DemoappServletFilter.
 * <p>
 * The process is made much messier than you might think by the fact that each component
 * may have MORE THAN ONE instance of the DemoappServletFilter and of the DemoappServletFilterInitializer.
 * The extra copy of the initializer is because if your web.xml contains a servlet with load-on-startup
 * and also a ContextLoaderListener (a common arrangement) then Spring creates two contexts:
 * a dispatcher context and an application context (see <a
 * href="http://stackoverflow.com/questions/18682486/why-does-spring-mvc-need-at-least-two-contexts">this</a>).
 * I wish I could tell you WHY Spring creates all of the objects in both of these contexts,
 * but honestly I don't really get it. Probably we are initializing Spring incorrectly so
 * that it reads "applicationContext.xml" for both contexts -- but most real-world apps I
 * have seen do the same thing. Why there are multiple copies of the DemoappServletFilter
 * is a complete mystery to me. All I can say in the end is that it appears to work. Don't
 * think too deeply about it, or else DO think deeply about it and then fix it, then let
 * me (mcherm@mcherm.com) know how.
 */
public abstract class ServletFilterInitializer {

    /**
     * This will inject the values into the <code>StatelessSessionServletFilter</code>(s).
     */
    @PostConstruct
    public void manuallyInjectValuesIntoServletFilter() {
        synchronized (StatelessSessionServletFilter.class) {
            for (StatelessSessionServletFilter filter : StatelessSessionServletFilter.getListOfInstances()) {
                if (filter.getStatelessSessionBeans() == null) {
                    filter.setStatelessSessionBeans(this.getStatelessSessionBeans());
                }
            }
        }
    }

    /**
     * The concrete class should implement this to return the list of
     * <code>StatelessSessionBean</code>s (in the order in which
     * <code>beginSession()</code> should be called; <code>endSession()</code>
     * will be called in the reverse order).
     */
    public abstract List<StatelessSessionBean> getStatelessSessionBeans();
}
