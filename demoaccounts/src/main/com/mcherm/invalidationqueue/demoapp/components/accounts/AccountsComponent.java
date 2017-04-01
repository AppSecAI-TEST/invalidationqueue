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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcherm.invalidationqueue.PrefilledThingamig;
import com.mcherm.invalidationqueue.SpringComponentCache;
import com.mcherm.invalidationqueue.demoapp.DemoappComponentCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * The Spring component that serves up the call to get the list of accounts
 */
@Controller
public class AccountsComponent {

    @Autowired
    AccountsBackEndService accountsBackEndService;

    // FIXME: Two of these because I'm still experimenting.
    @Autowired
    DemoappComponentCache componentCache1;

    @Autowired
    SpringComponentCache componentCache2;

    @Autowired
    ObjectMapper objectMapper;

    // FIXME: The following is only needed for some experimentation, and should be deleted afterwared
    @Autowired
    PrefilledThingamig prefilledThingamig;


    @RequestMapping(value = "v1/accounts", method = RequestMethod.GET)
    public @ResponseBody AccountList getAccounts2() throws  Throwable {
        return componentCache1.getEntry("accountList");
    }
}
