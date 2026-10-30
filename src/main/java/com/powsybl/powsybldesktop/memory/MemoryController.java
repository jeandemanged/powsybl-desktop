/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.memory;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Content of the "Memory" popup: JVM {@code -Xmx}, current heap usage and usage percentage, refreshed
 * every second by {@link #refreshTimeline} while the popup is showing - {@link #dispose()} stops it. Also edits the
 * max heap stored in the launcher config ({@link MaxHeapConfig}), applied at the next start.
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
    @FXML
    private ComboBox<Integer> maxHeapCombo;
    @FXML
    private Label maxHeapHintLabel;

    private final Timeline refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), _ -> refresh()));

    @FXML
    private void initialize() {
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refresh();
        refreshTimeline.play();

        initMaxHeapCombo();
    }

    private void initMaxHeapCombo() {
        int runningGb = MaxHeapConfig.running();
        Optional<Path> config = MaxHeapConfig.launcherConfigFile();
        maxHeapCombo.getItems().setAll(MaxHeapConfig.availableChoices());
        maxHeapCombo.setConverter(MaxHeapConfig.choiceConverter());
        if (config.isEmpty()) {
            maxHeapCombo.setValue(runningGb);
            maxHeapCombo.setDisable(true);
            showMaxHeapHint(Messages.get("memory.packagedOnly"));
            return;
        }
        maxHeapCombo.setValue(MaxHeapConfig.read(config.get()));
        updateRestartHint(runningGb);
        maxHeapCombo.valueProperty().addListener((_, _, newValue) -> {
            try {
                MaxHeapConfig.write(config.get(), newValue);
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR, Messages.get("memory.saveFailed", config.get(), e.getMessage()), ButtonType.OK);
                alert.initOwner(maxHeapCombo.getScene().getWindow());
                alert.showAndWait();
                maxHeapCombo.setValue(MaxHeapConfig.read(config.get()));
            }
            updateRestartHint(runningGb);
        });
    }

    private void updateRestartHint(int runningGb) {
        showMaxHeapHint(maxHeapCombo.getValue() != runningGb ? Messages.get("memory.restartRequired") : null);
    }

    private void showMaxHeapHint(String hint) {
        maxHeapHintLabel.setText(hint);
        maxHeapHintLabel.setVisible(hint != null);
        maxHeapHintLabel.setManaged(hint != null);
        // the popup isn't resizable: grow or shrink it to the hint
        if (maxHeapHintLabel.getScene() != null) {
            maxHeapHintLabel.getScene().getWindow().sizeToScene();
        }
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
