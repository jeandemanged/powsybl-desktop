/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.core;

import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class UnexpectedErrorsTest {

    private final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();

    // see MainModelTest
    @BeforeAll
    static void initJavaFxToolkit() {
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Headless");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException _) {
            // toolkit already started by another test class running in this fork
        }
        // TestFX sets its own default uncaught exception handler on first use, which would replace the one installed
        WaitForAsyncUtils.waitForFxEvents();
    }

    // TestFX's would fail the next test with the exceptions these tests raise on purpose
    @BeforeEach
    void withoutTestFxHandler() {
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    @AfterEach
    void restoreHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
    }

    @Test
    void exceptionEscapingAnFxEventIsNotifiedWithAViewLogsAction() throws Exception {
        MainModel model = new MainModel();
        AtomicBoolean logsViewed = new AtomicBoolean();
        UnexpectedErrors.install(model, () -> logsViewed.set(true));
        Platform.runLater(() -> {
            throw new NullPointerException("thrown by powsybl");
        });
        List<Notification> notifications = model.getNotificationsModel().getNotifications();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !notifications.isEmpty());

        Notification notification = notifications.getFirst();
        assertEquals(NotificationStatus.ERROR, notification.status());
        assertEquals(List.of("java.lang.NullPointerException: thrown by powsybl"), notification.messageArgs());
        WaitForAsyncUtils.asyncFx(() -> notification.actions().getFirst().handler().accept(null)).get();
        assertTrue(logsViewed.get());
    }

    @Test
    void exceptionEscapingABackgroundThreadIsNotified() throws Exception {
        MainModel model = new MainModel();
        UnexpectedErrors.install(model, () -> { });
        Thread thread = new Thread(() -> {
            throw new IllegalStateException("background");
        });
        thread.start();
        thread.join();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !model.getNotificationsModel().getNotifications().isEmpty());
        assertEquals(List.of("java.lang.IllegalStateException: background"),
                model.getNotificationsModel().getNotifications().getFirst().messageArgs());
    }

    @Test
    void guardedCallbackExceptionIsNotifiedAndReplacedByTheFallback() throws Exception {
        MainModel model = new MainModel();
        UnexpectedErrors.install(model, () -> { });
        boolean result = WaitForAsyncUtils.asyncFx(() -> UnexpectedErrors.guard(() -> {
            throw new IllegalStateException("from a diagram click");
        }, true)).get();
        assertTrue(result);
        assertEquals(List.of("java.lang.IllegalStateException: from a diagram click"),
                model.getNotificationsModel().getNotifications().getFirst().messageArgs());
    }

    @Test
    void repeatedExceptionIsNotifiedOnce() throws Exception {
        MainModel model = new MainModel();
        UnexpectedErrors.install(model, () -> { });
        WaitForAsyncUtils.asyncFx(() -> {
            UnexpectedErrors.report(new IllegalStateException("each layout pass"));
            UnexpectedErrors.report(new IllegalStateException("each layout pass"));
            UnexpectedErrors.report(new IllegalStateException("another"));
        }).get();
        assertEquals(2, model.getNotificationsModel().getNotifications().size());
    }
}
