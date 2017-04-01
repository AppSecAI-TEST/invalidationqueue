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
 * This defines the interface for a per-component cache. This provides methods for
 * storing entries in the cache, retrieving them, and supports invalidating the
 * entries when an invalidation event occurs. Specific implementations of the class
 * will provide a mechanism for storing the information, for instance one might use
 * memcache.
 * <p>
 * The model that this cache uses is that entries in the cache are first defined,
 * then used. Defining an entry involves specifying the name of the entry along
 * with which invalidation events should invalidate that cached value and optionally
 * a function to retrieve the value when needed. Users of the cache MUST be prepared
 * to either accept empty values (nulls) or else provide a refresh function because
 * the cache promises a best-effort to retrieve the values, not a guarantee.
 * <p>
 * An instance of this class is threadsafe. If there is a race condition to update
 * or store the value of an entry, one of the values will be avaliable for retrieval
 * by future get() calls, but there is no guarantee which one it will be. If there
 * is no race condition, then the most recently stored or retrieved value will be
 * the one returned by get() -- unless, of course, it returns null because the
 * cache has been cleared or lost.
 * <p>
 * This class is parameterized by some specific subclass of CacheInvalidationEvent
 * because each application that uses the invalidationqueue framework will have its
 * own list of invalidation events.
 * <p>
 * Any implementation of ComponentCache will need to be initialized as a StatelessSessionBean
 * so this interface will be declared to extend <code>StatelessSessionBean</code>.
 */
public interface ComponentCache<CIE extends CacheInvalidationEvent> extends StatelessSessionBean {


    /**
     * Store an entry in the cache.
     * <p>
     * The type of the value stored should exactly match whatever type was declared in the
     * <code>componentCacheEntries.json</code> file.
     *
     * @param name the name identifying the entry. Must be one of the entry names that was
     *     configured in the componentCacheEntries.json file.
     * @param value the value to be stored. This must be of the type that was configured in
     *     the componentCacheEntries.json file.
     */
    public void storeEntry(String name, Object value);

    /**
     * Empty a value out of the cache. Equivalent to <code>storeEntry(name, null)</code>.
     *
     * @param name the name identifying the entry. Must be one of the entry names that was
     *     configured in the componentCacheEntries.json file.
     */
    public void clearEntry(String name);

    /**
     * Returns the previously stored value for this entry OR returns null if there is
     * no refresh mechanism specified. To explain, the underlying storage mechanism is allowed
     * to be lossy. If the data was lost (or was never set), and a refresh function was
     * configured, then that will be used to obtain the value. But if the data was lost (or
     * never set) and there is no refresh function, then this will return null.
     * <p>
     * The actual type returned from this method will be whatever type was declared in the
     * <code>componentCacheEntries.json</code> file.
     *
     * @param name the name identifying the entry. Must be one of the entry names that was
     *     configured in the componentCacheEntries.json file.
     * @return either the value of that entry (which could be null if it was never set), or
     *   null (if the value got lost and there is no refresh function configured).
     */
    public <T> T getEntry(String name);

}


