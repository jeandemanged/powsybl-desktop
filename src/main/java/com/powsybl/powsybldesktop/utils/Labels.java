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

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Labels {

    private static final int TREE_LABEL_MAX_LENGTH = 64;
    private static final int TREE_LABEL_SUFFIX_LENGTH = 4;
    private static final int COUNTRIES_MAX_LISTED = 5;

    private Labels() {
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

    private static String formatCountries(Collection<Country> countries) {
        if (countries.size() > COUNTRIES_MAX_LISTED) {
            return Messages.get("networks.tree.countriesCount", countries.size());
        }
        return countries.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }
}
