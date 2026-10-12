/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.commons.config.BaseVoltageConfig;
import com.powsybl.commons.config.BaseVoltagesConfig;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.CheckBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The base voltage ranges the map colors substations and lines by, like single line diagrams: ranges from the
 * {@link BaseVoltagesConfig}, colors from the single line diagram's {@code baseVoltages.css}. The highest base voltage
 * range is open-ended here, so e.g. 750 kV equipment is shown as the 300-500 kV range rather than uncolored.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class MapBaseVoltages {

    private static final Pattern BASE_VOLTAGE_COLOR = Pattern.compile("\\.sld-(\\w+)\\s*\\{\\s*--sld-vl-color:\\s*(#\\w+)\\s*}");

    private final BaseVoltagesConfig baseVoltagesConfig = BaseVoltagesConfig.fromPlatformConfig();
    private final Map<String, String> baseVoltageColors = readBaseVoltageColors();
    private final List<BaseVoltageConfig> baseVoltages = baseVoltagesConfig.getBaseVoltages().stream()
            .filter(baseVoltage -> baseVoltage.getProfile().equals(baseVoltagesConfig.getDefaultProfile()))
            .toList();
    private final BaseVoltageConfig highestBaseVoltage = baseVoltages.stream()
            .max(Comparator.comparingDouble(BaseVoltageConfig::getMaxValue))
            .orElse(null);

    String baseVoltageName(double nominalV) {
        if (highestBaseVoltage != null && nominalV >= highestBaseVoltage.getMinValue()) {
            return highestBaseVoltage.getName();
        }
        return baseVoltagesConfig.getBaseVoltageName(nominalV, baseVoltagesConfig.getDefaultProfile()).orElse(null);
    }

    String color(String baseVoltage, String defaultColor) {
        return baseVoltage == null ? defaultColor : baseVoltageColors.getOrDefault(baseVoltage, defaultColor);
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
            checkBoxes = baseVoltages.stream().map(this::createCheckBox).toList();
        }

        private CheckBox createCheckBox(BaseVoltageConfig baseVoltage) {
            CheckBox checkBox = new CheckBox(baseVoltage == highestBaseVoltage
                    ? Messages.get("map.baseVoltages.rangeAbove", baseVoltage.getMinValue())
                    : Messages.get("map.baseVoltages.range", baseVoltage.getMinValue(), baseVoltage.getMaxValue()));
            checkBox.setGraphic(new Rectangle(10, 10, Color.web(color(baseVoltage.getName(), MapNetworkData.DEFAULT_SUBSTATION_COLOR))));
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

    private Map<String, String> readBaseVoltageColors() {
        Map<String, String> colors = new HashMap<>();
        Matcher matcher = BASE_VOLTAGE_COLOR.matcher(readResource("/baseVoltages.css"));
        while (matcher.find()) {
            colors.put(matcher.group(1), matcher.group(2));
        }
        return colors;
    }

    private String readResource(String name) {
        try (var stream = getClass().getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
