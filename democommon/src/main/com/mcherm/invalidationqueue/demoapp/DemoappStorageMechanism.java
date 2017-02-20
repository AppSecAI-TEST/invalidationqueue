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

import com.mcherm.invalidationqueue.StorageMechanism;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of StorageMechanism.
 */
@Component("storageMechanism")
public class DemoappStorageMechanism implements StorageMechanism {
    // FIXME: I should probably use something that is ACTUALLY out of memory. Like memcache. Or whatever.

    private static Map<String,String> data = new HashMap<>();


    @Override
    public void put(String key, String value) {
        data.put(key, value);
    }

    @Override
    public String get(String key) {
        return data.get(key);
    }
}
