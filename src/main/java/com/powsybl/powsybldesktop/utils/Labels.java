/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import javafx.util.StringConverter;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Labels {

    private static final int TREE_LABEL_MAX_LENGTH = 64;
    private static final int TREE_LABEL_SUFFIX_LENGTH = 4;
    private static final int COUNTRIES_MAX_LISTED = 5;

    // code then name, so that a searchable choice matches either
    private static final StringConverter<Country> COUNTRY_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Country country) {
            return country == null ? "-" : country.name() + " - " + country.getFullName();
        }

        @Override
        public Country fromString(String text) {
            throw new UnsupportedOperationException();
        }
    };

    private Labels() {
    }

    /**
     * Shows a country as its code and name, "-" for none.
     */
    public static StringConverter<Country> countryFormat() {
        return COUNTRY_FORMAT;
    }

    public static String truncateForTree(String text) {
        if (text.length() <= TREE_LABEL_MAX_LENGTH) {
            return text;
        }
        int prefixLength = TREE_LABEL_MAX_LENGTH - TREE_LABEL_SUFFIX_LENGTH - "...".length();
        return text.substring(0, prefixLength) + "..." + text.substring(text.length() - TREE_LABEL_SUFFIX_LENGTH);
    }

    // Truncated name/id plus its countries, e.g. for the navigation history menu (NavigationEvent.describe())
    public static String networkLabel(Network network) {
        String countries = formatCountries(network.getCountries());
        return truncateForTree(network.getNameOrId()) + (countries.isEmpty() ? "" : " (" + countries + ")");
    }

    public static String shuntCompensatorModelTypeLabel(ShuntCompensatorModelType modelType) {
        return Messages.get(modelType == ShuntCompensatorModelType.LINEAR
                ? "shuntCompensators.modelType.linear" : "shuntCompensators.modelType.nonLinear");
    }

    // A linear shunt has a single bPerSection for every section, so its sign alone decides reactor vs. capacitor.
    // A non-linear shunt stores each section's B independently, so all of them must agree on the sign.
    public static String shuntCompensatorTypeLabel(ShuntCompensator shuntCompensator) {
        if (shuntCompensator.getModelType() == ShuntCompensatorModelType.LINEAR) {
            double bPerSection = shuntCompensator.getModel(ShuntCompensatorLinearModel.class).getBPerSection();
            return Messages.get(bPerSection < 0 ? "shuntCompensators.type.reactor" : "shuntCompensators.type.capacitor");
        }
        List<ShuntCompensatorNonLinearModel.Section> sections = shuntCompensator.getModel(ShuntCompensatorNonLinearModel.class).getAllSections();
        if (sections.stream().allMatch(section -> section.getB() < 0)) {
            return Messages.get("shuntCompensators.type.reactor");
        }
        if (sections.stream().allMatch(section -> section.getB() > 0)) {
            return Messages.get("shuntCompensators.type.capacitor");
        }
        return Messages.get("shuntCompensators.type.unknown");
    }

    private static String formatCountries(Collection<Country> countries) {
        if (countries.size() > COUNTRIES_MAX_LISTED) {
            return Messages.get("networks.tree.countriesCount", countries.size());
        }
        return countries.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }
}
