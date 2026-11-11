/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.core;

import com.powsybl.iidm.network.Network;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task reading or writing a network off the FX thread, which keeps it marked busy (see {@link MainModel#markBusy})
 * from the task's creation until its work actually returns: a cancelled task's work can keep running, since most
 * PowSyBl calls ignore interruption, so the task being cancelled isn't when the network is free again. Must be
 * created on the FX thread, e.g. from {@link javafx.concurrent.Service#createTask()}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public abstract class AbstractNetworkTask<V> extends Task<V> {

    private final Runnable releaseBusy;
    private final AtomicBoolean computing = new AtomicBoolean();

    protected AbstractNetworkTask(MainModel mainModel, Network network) {
        releaseBusy = network == null ? () -> { } : mainModel.markBusy(network);
        // released in the same FX event as the onSucceeded/onFailed handlers storing the result, so that no edit can
        // slip in between and be missing from it (e.g. a rename missing from a search index built meanwhile)
        stateProperty().addListener((_, _, newState) -> {
            if (isTerminal(newState) && !computing.get()) {
                releaseBusy.run();
            }
        });
    }

    @Override
    protected final V call() throws Exception {
        computing.set(true);
        try {
            return compute();
        } finally {
            computing.set(false);
            // cancelled while computing: no later state change will release the network. Not isDone(): it turns true
            // once call() returns, before the SUCCEEDED state is set, which would release before the result is stored
            Platform.runLater(() -> {
                if (isCancelled()) {
                    releaseBusy.run();
                }
            });
        }
    }

    private static boolean isTerminal(Worker.State state) {
        return state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED;
    }

    protected abstract V compute() throws Exception;
}
