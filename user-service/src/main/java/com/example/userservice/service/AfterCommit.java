package com.example.userservice.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// runs an action once the surrounding transaction has committed, so a slow or failing call to another service never
// runs inside it and cannot roll it back. If the transaction rolls back the action does not run. Outside a
// transaction it runs straight away
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
