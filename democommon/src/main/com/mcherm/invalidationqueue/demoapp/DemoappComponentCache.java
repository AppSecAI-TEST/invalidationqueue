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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcherm.invalidationqueue.ComponentCacheImpl;
import com.mcherm.invalidationqueue.SessionData;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;


/**
 * An implementation of ComponentCache for Demoapp.
 */
public class DemoappComponentCache extends ComponentCacheImpl<DemoappCacheInvalidationEvent, SessionData> {

    @Autowired
    private BeanFactory beanFactory;

    /** Constructor. */
    public DemoappComponentCache(
            ObjectMapper objectMapper,
            DemoappStorageMechanism storageMechanism,
            String componentName,
            DemoappCacheInvalidationQueue cacheInvalidationQueue,
            SessionData sessionData,
            String entriesFileName) {
        super(
                objectMapper,
                storageMechanism,
                componentName,
                sessionData,
                cacheInvalidationQueue,
                DemoappComponentCache.class.getClassLoader().getResourceAsStream("/webapp/WEB-INF/" + entriesFileName),
                entriesFileName,
                DemoappCacheInvalidationEvent.class);
    }


    // FIXME: Consider making so the string can be a bean name OR a type, so long as the type is unique
    @Override
    public Callable getRefreshBeanFromName(String refreshFunctionBeanName) {
        if (beanFactory.containsBean(refreshFunctionBeanName)) {
            if (beanFactory.isTypeMatch(refreshFunctionBeanName, Callable.class)) {
                return (Callable) beanFactory.getBean(refreshFunctionBeanName);
            } else {
                throw new RuntimeException("Bean '" + refreshFunctionBeanName + "' is not Callable.");
            }
        } else {
            throw new RuntimeException("No bean found with the name '" + refreshFunctionBeanName + "'.");
        }
    }

}
