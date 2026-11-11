/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.controls;

import com.powsybl.iidm.network.Country;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A titled country picker of an equipment criterion (contingency lists, limit scalings), telling {@code onCommit} of
 * every change.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CountryGroup {
    private final SearchableCheckComboBox<Country> comboBox = new SearchableCheckComboBox<>(List.of(Country.values()), Labels.countryFormat());
    private final Node root;

    public CountryGroup(String titleKey, Runnable onCommit) {
        comboBox.getCheckedItems().addListener((ListChangeListener<Country>) _ -> onCommit.run());
        root = new VBox(4, new Label(Messages.get(titleKey)), comboBox);
    }

    public Node root() {
        return root;
    }

    public void setValue(List<Country> countries) {
        comboBox.setChecked(countries);
    }

    public List<Country> getChecked() {
        return List.copyOf(comboBox.getCheckedItems());
    }
}
