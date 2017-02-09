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
import com.mcherm.invalidationqueue.StatelessSessionBean;
import com.mcherm.invalidationqueue.ServletFilterInitializer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;


/**
 * An instance of <code>ServletFilterInitializer</code> for the Demoapp project.
 */
public class DemoappServletFilterInitializer extends ServletFilterInitializer {
    @Autowired
    SessionData sessionData;

    @Autowired
    DemoappCacheInvalidationQueue cacheInvalidationQueue;

    @Autowired
    DemoappComponentCache componentCache;

    @Override
    public List<StatelessSessionBean> getStatelessSessionBeans() {
        return Arrays.asList(sessionData, cacheInvalidationQueue, componentCache);
    }

}
