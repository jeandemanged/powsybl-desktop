/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.utils.Labels;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Substation detail: country, TSO, geographical tags and its voltage levels, linked to their own detail views.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SubstationDetailController extends AbstractEquipmentDetailController<Substation> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return equipment.getVoltageLevelStream().toList();
    }

    @Override
    protected List<? extends Container<?>> linkedContainers() {
        return List.of(equipment);
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SINGLE_LINE;
    }

    @Override
    protected void buildMain(DetailForm form) {
        // no country is a valid choice
        List<Country> countries = new ArrayList<>();
        countries.add(null);
        countries.addAll(DetailSections.values(Country.class));
        form.searchableChoiceField("desktop.common.column.country", countries, Labels.countryFormat(), equipment::getNullableCountry, equipment::setCountry)
                .textField("substations.column.tso", equipment::getTso, equipment::setTso)
                .text("substations.column.geographicalTags", () -> String.join(", ", equipment.getGeographicalTags()));
        // voltage levels are created and removed from elsewhere too
        VBox links = new VBox();
        List<VoltageLevel> shown = new ArrayList<>();
        form.node("desktop.common.equipment.voltageLevel.plural", links, null, () -> {
            List<VoltageLevel> voltageLevels = voltageLevels();
            if (!voltageLevels.equals(shown)) {
                shown.clear();
                shown.addAll(voltageLevels);
                links.getChildren().setAll(voltageLevels.stream().map(this::link).toList());
            }
        });
    }

    private Hyperlink link(VoltageLevel voltageLevel) {
        Hyperlink link = new Hyperlink(voltageLevel.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(_ -> EquipmentDetails.open(sceneModel, voltageLevel));
        return link;
    }

    @Override
    protected List<ExtensionsPane.Editor<Substation>> extensionEditors() {
        return ExtensionEditors.forSubstation();
    }
}
