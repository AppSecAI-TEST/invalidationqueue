/*
 *
 * Copyright 2017 Michael Chermside
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * /
 */

package com.mcherm.invalidationqueue;

import org.springframework.stereotype.Component;

/**
 * FIXME: This class exists only to test out initializing objects by Spring.
 */
@Component
public class PrefilledThingamig {

    private final String someValue = "find-this-value-1";
    private final ObjectMapper objectMapper;
    private final CacheInvalidationQueue cacheInvalidationQueue;

    /**
     * Constructor with mandatory fields, that Spring 4.3 will fill in.
     */
    public PrefilledThingamig(ObjectMapper objectMapper, CacheInvalidationQueue cacheInvalidationQueue, String componentName) {
        this.objectMapper = objectMapper;
        this.cacheInvalidationQueue = cacheInvalidationQueue;
    }

}
