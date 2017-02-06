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


/**
 * Implementers of this interface are expected to be enums which list the
 * possible cache events that can be generated. Each application using the
 * invalidationqueue framework will have its own implementation of
 * CacheInvalidationEvent, but the class DemoappCacheInvalidationEvent
 * provides an example of how to write these.
 * <p>
 * There must be no more thant 256 different events in the application,
 * and each needs to be encoded as a different byte.
 */
public interface CacheInvalidationEvent {

    /**
     * Function to convert to a small non-negative integer. All enums will automatically
     * implement this method.
     */
    public int ordinal();

}
