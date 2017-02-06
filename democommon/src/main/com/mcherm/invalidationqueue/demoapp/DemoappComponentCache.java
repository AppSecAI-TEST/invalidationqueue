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

import com.mcherm.invalidationqueue.ComponentCacheImpl;


/**
 * An implementation of ComponentCache for Demoapp.
 */
public class DemoappComponentCache extends ComponentCacheImpl<DemoappCacheInvalidationEvent, DemoappSessionData> {

    /** Constructor. */
    public DemoappComponentCache(
            DemoappStorageMechanism storageMechanism,
            DemoappCacheInvalidationQueue cacheInvalidationQueue,
            DemoappSessionData sessionData,
            String entriesFileName) {
        super(
                storageMechanism,
                sessionData,
                cacheInvalidationQueue,
                DemoappComponentCache.class.getClassLoader().getResourceAsStream("/webapp/WEB-INF/" + entriesFileName),
                entriesFileName,
                DemoappCacheInvalidationEvent.class);
    }

}