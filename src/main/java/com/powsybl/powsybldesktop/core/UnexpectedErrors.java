/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.core;

import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Makes exceptions nothing else handles visible: logged and notified, instead of the JVM's default handler printing
 * them to the console only (e.g. a PowSyBl call throwing in an FX event handler).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class UnexpectedErrors {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnexpectedErrors.class);
    private static final String MESSAGE_KEY = "main.unexpectedError";

    private UnexpectedErrors() {
    }

    /**
     * Reports the exceptions escaping any thread, the FX one included (JavaFX hands those thrown by event handlers to
     * the uncaught exception handler and carries on), as notifications of {@code mainModel} offering {@code viewLogs}.
     */
    public static void install(MainModel mainModel, Runnable viewLogs) {
        // a reloaded shell's replaces the previous shell's
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Handler(mainModel, viewLogs, current instanceof Handler handler ? handler.next() : current));
    }

    // next: the handler installed before, if any (e.g. TestFX's, failing tests on exceptions), still told
    private record Handler(MainModel mainModel, Runnable viewLogs, Thread.UncaughtExceptionHandler next) implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable exception) {
            LOGGER.error(exception.toString(), exception);
            if (Platform.isFxApplicationThread()) {
                addNotification(mainModel, viewLogs, exception);
            } else {
                Platform.runLater(() -> addNotification(mainModel, viewLogs, exception));
            }
            if (next != null) {
                next.uncaughtException(thread, exception);
            }
        }
    }

    /**
     * Reports an exception caught where it would otherwise go unnoticed, from any thread, as if uncaught.
     */
    public static void report(Throwable exception) {
        Thread thread = Thread.currentThread();
        // the installed handler, not one the thread may have of its own
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        (handler != null ? handler : thread.getUncaughtExceptionHandler()).uncaughtException(thread, exception);
    }

    /**
     * Runs a method a WebView's script calls, reporting its exception: WebKit would turn it into a script error, out
     * of sight of the uncaught exception handler.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void guard(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            report(e);
        }
    }

    /**
     * As {@link #guard(Runnable)}, for a method returning a value: {@code fallback} when it throws.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T> T guard(Supplier<T> callback, T fallback) {
        try {
            return callback.get();
        } catch (RuntimeException e) {
            report(e);
            return fallback;
        }
    }

    private static void addNotification(MainModel mainModel, Runnable viewLogs, Throwable exception) {
        List<Object> messageArgs = List.of(exception.toString());
        // an exception thrown at every layout pass or map tile would otherwise flood the notifications; the logs keep each
        List<Notification> notifications = mainModel.getNotificationsModel().getNotifications();
        if (!notifications.isEmpty() && notifications.getLast().messageKey().equals(MESSAGE_KEY)
                && notifications.getLast().messageArgs().equals(messageArgs)) {
            return;
        }
        NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", _ -> viewLogs.run());
        mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), MESSAGE_KEY, viewLogsAction)
                .withMessageArgs(messageArgs.toArray()));
    }
}
