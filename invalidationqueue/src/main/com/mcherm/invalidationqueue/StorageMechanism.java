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
 * A storage mechanism. Calling get() either returns null, or returns one of the most
 * recent values passed to the call store().
 */
public interface StorageMechanism {
    /**
     * Sets the value of a certain key.
     *
     * @param key a string that can be used to identify the data being stored
     * @param value the value to be stored, or null to clear the stored value.
     */
    public void put(String key, String value);

    /**
     * Retrieves the latest value that was stored.
     *
     * @param key the key to be looked up
     * @return the latest value that was set with that key using put() OR it can return null instead since
     *  the storage mechanism is allowed to be lossy.
     */
    public String get(String key);
}
