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
package com.mcherm.invalidationqueue.demoapp.components.movemoney;

import com.mcherm.invalidationqueue.CacheInvalidationQueue;
import com.mcherm.invalidationqueue.demoapp.DemoappCacheInvalidationEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;



/**
 * The Spring component that serves up the call to get the list of accounts
 */
@Controller
public class MoveMoneyComponent {

    @Autowired
    MoveMoneyBackEndService moveMoneyBackEndService;

    @Autowired
    CacheInvalidationQueue<DemoappCacheInvalidationEvent> cacheInvalidationQueue;

    @RequestMapping(value = "v1/spend", method = RequestMethod.POST)
    public @ResponseBody
    void getAccounts(@ModelAttribute SpendMoneyRequest spendMoneyRequest) {
        moveMoneyBackEndService.spendMoney(spendMoneyRequest.getFromAccountNumber(), spendMoneyRequest.getAmount());
        cacheInvalidationQueue.addEvent(DemoappCacheInvalidationEvent.BalancesChanged);
    }

}
