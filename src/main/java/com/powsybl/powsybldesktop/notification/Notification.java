/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import com.powsybl.powsybldesktop.utils.Messages;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The message is kept as a bundle key (resolved via {@link #message()} at render time) rather than
 * a string resolved once at creation, so a notification already in {@code MainModel}'s history
 * re-renders in the current UI language instead of staying frozen in whichever language was active
 * when it was created. {@code startTimestamp} is carried over from the running notification into its
 * outcome (see {@link #createSuccess}/{@link #createError}/{@link #createCancelled}) so
 * {@link #duration()} reports how long the operation actually took. The message template gets the elapsed time
 * as {@code {0}}, followed by {@code messageArgs} as {@code {1}}, {@code {2}}...
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record Notification(Instant startTimestamp, Instant timestamp, NotificationStatus status,
                            String messageKey, List<Object> messageArgs, List<NotificationAction> actions, Runnable onCancel) {
    public static Notification createRunning(String messageKey, Runnable onCancel) {
        Instant now = Instant.now();
        return new Notification(now, now, NotificationStatus.RUNNING, messageKey, List.of(), List.of(), onCancel);
    }

    public static Notification createSuccess(Instant startTimestamp, String messageKey, NotificationAction... actions) {
        return new Notification(startTimestamp, Instant.now(), NotificationStatus.SUCCESS, messageKey, List.of(), List.of(actions), null);
    }

    public static Notification createError(Instant startTimestamp, String messageKey, NotificationAction... actions) {
        return new Notification(startTimestamp, Instant.now(), NotificationStatus.ERROR, messageKey, List.of(), List.of(actions), null);
    }

    public static Notification createCancelled(Instant startTimestamp, String messageKey) {
        return new Notification(startTimestamp, Instant.now(), NotificationStatus.CANCELLED, messageKey, List.of(), List.of(), null);
    }

    public Notification withMessageArgs(Object... args) {
        return new Notification(startTimestamp, timestamp, status, messageKey, List.of(args), actions, onCancel);
    }

    /** Time elapsed since the operation started: still growing while {@link NotificationStatus#RUNNING}, fixed once terminal. */
    public Duration duration() {
        return Duration.between(startTimestamp, status == NotificationStatus.RUNNING ? Instant.now() : timestamp);
    }

    public String message() {
        Object[] args = new Object[messageArgs.size() + 1];
        args[0] = duration().toSeconds() + " s";
        for (int i = 0; i < messageArgs.size(); i++) {
            args[i + 1] = messageArgs.get(i);
        }
        return Messages.get(messageKey, args);
    }
}
