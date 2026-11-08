/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.BasicSequenceNumberGenerator;
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
    private final ObservableList<LogEntry> logs = FXCollections.observableArrayList();
    private final Queue<LogEntry> pendingLogs = new ConcurrentLinkedQueue<>();
    // kept as a field: an unreferenced running Timeline is eligible for GC and would silently stop ticking
    private final Timeline flushTimeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> flushPendingLogs()));

    public LogsModel() {
        Appender appender = new Appender();
        ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        // timestamps are only millisecond-precise, so ordering views (e.g. newest on top) sort on this instead
        if (logbackLogger.getLoggerContext().getSequenceNumberGenerator() == null) {
            logbackLogger.getLoggerContext().setSequenceNumberGenerator(new BasicSequenceNumberGenerator());
        }
        logbackLogger.addAppender(appender);
        appender.start();

        flushTimeline.setCycleCount(Animation.INDEFINITE);
        flushTimeline.play();
    }

    private void flushPendingLogs() {
        if (pendingLogs.isEmpty()) {
            return;
        }
        List<LogEntry> batch = new ArrayList<>();
        LogEntry event;
        while ((event = pendingLogs.poll()) != null) {
            batch.add(event);
        }
        logs.addAll(batch);
        int overflow = logs.size() - MAX_LOGS_SIZE;
        if (overflow > 0) {
            logs.remove(0, overflow);
        }
    }

    public ObservableList<LogEntry> getLogs() {
        return logs;
    }

    public void clearLogs() {
        logs.clear();
    }

    /**
     * Replaces the logs by a study's, which must be numbered below this session's logs to stay ordered before them.
     */
    public void restoreLogs(List<LogEntry> entries) {
        logs.setAll(entries);
    }

    class Appender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent eventObject) {
            pendingLogs.add(new LogEntry(eventObject.getSequenceNumber(), eventObject.getTimeStamp(),
                    eventObject.getLevel(), eventObject.getFormattedMessage()));
        }
    }

    /**
     * Snapshot of a logging event: keeping the event itself would retain its arguments and throwable,
     * e.g. a closed network logged as a message argument.
     */
    public record LogEntry(long sequenceNumber, long timeStamp, Level level, String message) {
    }
}
