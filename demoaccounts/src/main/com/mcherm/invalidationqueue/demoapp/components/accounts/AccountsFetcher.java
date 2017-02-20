package com.mcherm.invalidationqueue.demoapp.components.accounts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;


/**
 * This is an example of creating a fetcher. Because the componentCacheEntries.json file lists
 * "accountsFetcher" as the refreshFunction for the accounts entry, the code of the actual
 * component never needs to execute code to load the value; it happens automatically by
 * invoking this whenever the value is requested but is not available in the cache. The class
 * here need to implement <code>Callable&lt;</code>the-type-of-the-entry<code>&gt;</code>, but it
 * may be as simple or complex as needed. In this case, it is a wrapper around a call to the
 * (mock) back end service, but it does one extra transformation (converting a
 * <code>List&lt;Account&gt;</code> to an <code>AccountList</code>).
 */
@Component("accountsFetcher")
public class AccountsFetcher implements Callable<AccountList> {

    @Autowired
    AccountsBackEndService accountsBackEndService;

    @Override
    public AccountList call() throws Exception {
        return new AccountList(accountsBackEndService.getAccounts());
    }
}
