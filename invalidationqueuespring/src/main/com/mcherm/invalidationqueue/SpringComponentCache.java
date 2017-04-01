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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;


/**
 * // FIXME: Document this
 */
@Component
public class SpringComponentCache<CIE extends CacheInvalidationEvent> extends ComponentCacheImpl<CIE> {
    private final static String ENTRIES_FILE_NAME = "componentCacheEntries.json";
    private final static String ENTRIES_LOCATION = "/webapp/WEB-INF/";


    @Autowired
    private BeanFactory beanFactory;

    /** Constructor. */
    public SpringComponentCache(
            ObjectMapper objectMapper,
            StorageMechanism storageMechanism,
            SessionData sessionData,
            CacheInvalidationQueue<CIE> cacheInvalidationQueue,
            Class<CIE> cacheInvalidationEventClass,
            String componentName) {
        super(
                objectMapper,
                storageMechanism,
                sessionData,
                cacheInvalidationQueue,
                cieClass,
                componentName,
                ENTRIES_FILE_NAME,
                SpringComponentCache.class.getClassLoader().getResourceAsStream(ENTRIES_LOCATION + ENTRIES_FILE_NAME));
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
