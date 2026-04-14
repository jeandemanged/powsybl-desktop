/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.memory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

/**
 * Content of the "Memory" popup: JVM {@code -Xmx}, current heap usage and usage percentage, refreshed
 * every second by {@link #refreshTimeline} while the popup is showing - {@link #dispose()} stops it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MemoryController {

    private static final long BYTES_PER_MB = 1024 * 1024;

    @FXML
    private Label maxValueLabel;
    @FXML
    private Label usedValueLabel;
    @FXML
    private Label percentValueLabel;
    @FXML
    private ProgressBar usageBar;

    private final Timeline refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));

    @FXML
    private void initialize() {
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refresh();
        refreshTimeline.play();
    }

    @FXML
    private void onForceGc() {
        System.gc();
        refresh();
    }

    public void dispose() {
        refreshTimeline.stop();
    }

    private void refresh() {
        Runtime runtime = Runtime.getRuntime();
        long maxMb = runtime.maxMemory() / BYTES_PER_MB;
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB;
        double ratio = maxMb > 0 ? (double) usedMb / maxMb : 0;

        maxValueLabel.setText(maxMb + " MB");
        usedValueLabel.setText(usedMb + " MB");
        percentValueLabel.setText(Math.round(ratio * 100) + " %");
        usageBar.setProgress(ratio);
    }
}
