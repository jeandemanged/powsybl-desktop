/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

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
    private final AtomicBoolean started = new AtomicBoolean();

    protected AbstractNetworkTask(MainModel mainModel, Network network) {
        releaseBusy = network == null ? () -> { } : mainModel.markBusy(network);
        stateProperty().addListener((observable, oldState, newState) -> {
            // cancelled before it ever ran: compute() won't run, nor release the network
            if (newState == Worker.State.CANCELLED && !started.get()) {
                releaseBusy.run();
            }
        });
    }

    @Override
    protected final V call() throws Exception {
        started.set(true);
        try {
            return compute();
        } finally {
            Platform.runLater(releaseBusy);
        }
    }

    protected abstract V compute() throws Exception;
}
