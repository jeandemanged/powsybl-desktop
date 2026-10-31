/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.diagram;

import com.powsybl.commons.config.BaseVoltageConfig;
import com.powsybl.commons.config.BaseVoltagesConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The base voltage ranges equipment is colored by outside the diagrams (map, detail views' equipment symbols), like
 * single line diagrams: ranges from the {@link BaseVoltagesConfig}'s default profile, colors from the single line
 * diagram's {@code baseVoltages.css}. The highest base voltage range is open-ended here, so e.g. 750 kV equipment is
 * shown as the 300-500 kV range rather than uncolored.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class BaseVoltages {

    private static final Pattern BASE_VOLTAGE_COLOR = Pattern.compile("\\.sld-(\\w+)\\s*\\{\\s*--sld-vl-color:\\s*(#\\w+)\\s*}");

    private final BaseVoltagesConfig baseVoltagesConfig = BaseVoltagesConfig.fromPlatformConfig();
    private final Map<String, String> baseVoltageColors = readBaseVoltageColors();
    private final List<BaseVoltageConfig> baseVoltages = baseVoltagesConfig.getBaseVoltages().stream()
            .filter(baseVoltage -> baseVoltage.getProfile().equals(baseVoltagesConfig.getDefaultProfile()))
            .toList();
    private final BaseVoltageConfig highestBaseVoltage = baseVoltages.stream()
            .max(Comparator.comparingDouble(BaseVoltageConfig::getMaxValue))
            .orElse(null);

    /**
     * The default profile's ranges.
     */
    public List<BaseVoltageConfig> getBaseVoltages() {
        return baseVoltages;
    }

    /**
     * Whether {@code baseVoltage} is the open-ended highest range.
     */
    public boolean isHighest(BaseVoltageConfig baseVoltage) {
        return baseVoltage == highestBaseVoltage;
    }

    /**
     * The name of the range of {@code nominalV}, null outside all ranges.
     */
    public String name(double nominalV) {
        if (highestBaseVoltage != null && nominalV >= highestBaseVoltage.getMinValue()) {
            return highestBaseVoltage.getName();
        }
        return baseVoltagesConfig.getBaseVoltageName(nominalV, baseVoltagesConfig.getDefaultProfile()).orElse(null);
    }

    /**
     * The CSS color of the range named {@code baseVoltage}, {@code defaultColor} if none.
     */
    public String color(String baseVoltage, String defaultColor) {
        return baseVoltage == null ? defaultColor : baseVoltageColors.getOrDefault(baseVoltage, defaultColor);
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
