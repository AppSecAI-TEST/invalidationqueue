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
package com.mcherm.invalidationqueue.demoapp.components.accounts;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;


/**
 * This is the (mock) back end service for the demo app. It returns mock
 * data but is also quite slow (to simulate slow back ends).
 */
@Component
public class AccountsBackEndService {

    public List<Account> getAccounts() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Ignore it.
        }
        return Arrays.asList(
                new Account("12345", new BigDecimal("1000.00")),
                new Account("54321", new BigDecimal("25000.00")));
    }

}
