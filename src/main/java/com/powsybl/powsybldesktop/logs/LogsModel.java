/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LogsModel {
    private static final int MAX_LOGS_SIZE = 20_000;
    private final ObservableList<ILoggingEvent> logs = FXCollections.observableArrayList();
    private final Queue<ILoggingEvent> pendingLogs = new ConcurrentLinkedQueue<>();
    // kept as a field: an unreferenced running Timeline is eligible for GC and would silently stop ticking
    private final Timeline flushTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> flushPendingLogs()));

    public LogsModel() {
        Appender appender = new Appender();
        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logbackLogger.addAppender(appender);
        appender.start();

        flushTimeline.setCycleCount(Animation.INDEFINITE);
        flushTimeline.play();
    }

    private void flushPendingLogs() {
        if (pendingLogs.isEmpty()) {
            return;
        }
        List<ILoggingEvent> batch = new ArrayList<>();
        ILoggingEvent event;
        while ((event = pendingLogs.poll()) != null) {
            batch.add(event);
        }
        logs.addAll(batch);
        int overflow = logs.size() - MAX_LOGS_SIZE;
        if (overflow > 0) {
            logs.remove(0, overflow);
        }
    }

    public ObservableList<ILoggingEvent> getLogs() {
        return logs;
    }

    public void clearLogs() {
        logs.clear();
    }

    class Appender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent eventObject) {
            pendingLogs.add(eventObject);
        }
    }
}
