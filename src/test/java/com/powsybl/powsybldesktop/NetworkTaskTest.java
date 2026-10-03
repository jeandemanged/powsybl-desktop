/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkTaskTest {

    // see MainModelTest
    @BeforeAll
    static void initJavaFxToolkit() {
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Headless");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // toolkit already started by another test class running in this fork
        }
    }

    private final Network network = IeeeCdfNetworkFactory.create14();

    @Test
    void cancelledTaskKeepsTheNetworkBusyUntilItsWorkReturns() throws Exception {
        MainModel model = new MainModel();
        CountDownLatch computing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AbstractNetworkTask<Void>[] task = new AbstractNetworkTask[1];
        WaitForAsyncUtils.asyncFx(() -> {
            task[0] = new AbstractNetworkTask<>(model, network) {
                @Override
                protected Void compute() {
                    computing.countDown();
                    // ignores interruption, like most PowSyBl calls
                    while (finish.getCount() > 0) {
                        Thread.onSpinWait();
                    }
                    return null;
                }
            };
            new Thread(task[0]).start();
        }).get();
        assertTrue(computing.await(5, TimeUnit.SECONDS));

        WaitForAsyncUtils.asyncFx(() -> task[0].cancel()).get();
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(model.isBusy(network), "the cancelled task's work is still running");

        finish.countDown();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !model.isBusy(network));
    }

    @Test
    void taskCancelledBeforeRunningReleasesTheNetwork() throws Exception {
        MainModel model = new MainModel();
        WaitForAsyncUtils.asyncFx(() -> {
            AbstractNetworkTask<Void> task = new AbstractNetworkTask<>(model, network) {
                @Override
                protected Void compute() {
                    return null;
                }
            };
            assertTrue(model.isBusy(network));
            task.cancel();
        }).get();

        assertFalse(model.isBusy(network));
    }
}
