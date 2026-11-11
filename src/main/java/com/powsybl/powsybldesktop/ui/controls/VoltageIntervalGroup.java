/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.controls;

import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A titled nominal voltage interval of an equipment criterion (contingency lists, limit scalings): low and high
 * bounds, each optional and inclusive or not. {@code onCommit} is told of every committed change.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class VoltageIntervalGroup {
    private final TextField lowField = new TextField();
    private final CheckBox lowClosedCheckBox = new CheckBox(Messages.get("contingencies.criterion.voltage.inclusive"));
    private final TextField highField = new TextField();
    private final CheckBox highClosedCheckBox = new CheckBox(Messages.get("contingencies.criterion.voltage.inclusive"));
    private final Node root;

    public VoltageIntervalGroup(String titleKey, Runnable onCommit) {
        lowClosedCheckBox.setSelected(true);
        highClosedCheckBox.setSelected(true);
        HBox lowBox = new HBox(4, new Label(Messages.get("contingencies.criterion.voltage.lowBound")), lowField, lowClosedCheckBox);
        HBox highBox = new HBox(4, new Label(Messages.get("contingencies.criterion.voltage.highBound")), highField, highClosedCheckBox);
        root = new VBox(4, new Label(Messages.get(titleKey)), lowBox, highBox);
        commitOnChange(lowField, onCommit);
        commitOnChange(highField, onCommit);
        lowClosedCheckBox.selectedProperty().addListener((_, _, _) -> onCommit.run());
        highClosedCheckBox.selectedProperty().addListener((_, _, _) -> onCommit.run());
    }

    public Node root() {
        return root;
    }

    public void setValue(VoltageInterval interval) {
        lowField.setText(interval == null ? "" : interval.getNominalVoltageLowBound().map(String::valueOf).orElse(""));
        highField.setText(interval == null ? "" : interval.getNominalVoltageHighBound().map(String::valueOf).orElse(""));
        lowClosedCheckBox.setSelected(interval == null || interval.isLowClosed());
        highClosedCheckBox.setSelected(interval == null || interval.isHighClosed());
    }

    /**
     * The interval, null when neither bound is set.
     */
    public VoltageInterval toInterval() {
        Double low = parse(lowField.getText());
        Double high = parse(highField.getText());
        if (low == null && high == null) {
            return null;
        }
        VoltageInterval.Builder builder = VoltageInterval.builder();
        if (low != null) {
            builder.setLowBound(low, lowClosedCheckBox.isSelected());
        }
        if (high != null) {
            builder.setHighBound(high, highClosedCheckBox.isSelected());
        }
        return builder.build();
    }

    private static Double parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Runs {@code onCommit} when the field's text is committed: on Enter, and when it loses the focus.
     */
    public static void commitOnChange(TextField field, Runnable onCommit) {
        field.setOnAction(_ -> onCommit.run());
        field.focusedProperty().addListener((_, _, isFocused) -> {
            if (Boolean.FALSE.equals(isFocused)) {
                onCommit.run();
            }
        });
    }
}
