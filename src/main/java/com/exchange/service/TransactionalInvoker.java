package com.exchange.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalInvoker {

    @Transactional
    public void invokeTransactional(Runnable runnable) {
        runnable.run();
    }
}
