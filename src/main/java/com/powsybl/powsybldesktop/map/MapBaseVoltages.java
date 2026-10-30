/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.commons.config.BaseVoltageConfig;
import com.powsybl.powsybldesktop.diagram.BaseVoltages;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.CheckBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The map's base voltage filter: a checkbox per {@link BaseVoltages} range, in its color.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class MapBaseVoltages {

    private final BaseVoltages baseVoltages;

    public MapBaseVoltages(BaseVoltages baseVoltages) {
        this.baseVoltages = Objects.requireNonNull(baseVoltages);
    }

    /**
     * One checkbox per base voltage range, checked if shown. A user toggle passes the names of the base voltages
     * now hidden to {@code onChange}.
     */
    public CheckBoxes createCheckBoxes(Consumer<Set<String>> onChange) {
        return new CheckBoxes(onChange);
    }

    public final class CheckBoxes {
        private final List<CheckBox> checkBoxes;
        private final Consumer<Set<String>> onChange;
        private boolean updating;

        private CheckBoxes(Consumer<Set<String>> onChange) {
            this.onChange = Objects.requireNonNull(onChange);
            checkBoxes = baseVoltages.getBaseVoltages().stream().map(this::createCheckBox).toList();
        }

        private CheckBox createCheckBox(BaseVoltageConfig baseVoltage) {
            CheckBox checkBox = new CheckBox(baseVoltages.isHighest(baseVoltage)
                    ? Messages.get("map.baseVoltages.rangeAbove", baseVoltage.getMinValue())
                    : Messages.get("map.baseVoltages.range", baseVoltage.getMinValue(), baseVoltage.getMaxValue()));
            checkBox.setGraphic(new Rectangle(10, 10, Color.web(baseVoltages.color(baseVoltage.getName(), MapNetworkData.DEFAULT_SUBSTATION_COLOR))));
            checkBox.setUserData(baseVoltage.getName());
            checkBox.setSelected(true);
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (!updating) {
                    onChange.accept(hidden());
                }
            });
            return checkBox;
        }

        public List<CheckBox> getCheckBoxes() {
            return checkBoxes;
        }

        public void setHidden(Set<String> hidden) {
            updating = true;
            try {
                checkBoxes.forEach(checkBox -> checkBox.setSelected(!hidden.contains((String) checkBox.getUserData())));
            } finally {
                updating = false;
            }
        }

        // a single onChange rather than one per checkbox
        public void setAllSelected(boolean selected) {
            updating = true;
            try {
                checkBoxes.forEach(checkBox -> checkBox.setSelected(selected));
            } finally {
                updating = false;
            }
            onChange.accept(hidden());
        }

        private Set<String> hidden() {
            return checkBoxes.stream()
                    .filter(checkBox -> !checkBox.isSelected())
                    .map(checkBox -> (String) checkBox.getUserData())
                    .collect(Collectors.toSet());
        }
    }
}
