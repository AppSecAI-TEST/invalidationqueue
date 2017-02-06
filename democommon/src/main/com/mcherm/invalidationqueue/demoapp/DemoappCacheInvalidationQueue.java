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

import com.mcherm.invalidationqueue.CacheInvalidationQueue;

/**
 * A concrete implementation of CacheInvalidationQueue for Demoapp. It merely needs to specify
 * what type of CacheInvalidationEvent is being used.
 */
public class DemoappCacheInvalidationQueue extends CacheInvalidationQueue<DemoappCacheInvalidationEvent> {
    /** Constructor. */
    public DemoappCacheInvalidationQueue() {
        super(DemoappCacheInvalidationEvent.class);
    }
}
