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
        form.choiceField("desktop.common.column.country", countries, DetailSections.enumFormat(), equipment::getNullableCountry, equipment::setCountry)
                .textField("substations.column.tso", equipment::getTso, equipment::setTso)
                .text("substations.column.geographicalTags", () -> String.join(", ", equipment.getGeographicalTags()));
        form.section("desktop.common.equipment.voltageLevel.plural");
        for (VoltageLevel voltageLevel : voltageLevels()) {
            DetailSections.equipmentLink(form, "desktop.common.equipment.voltageLevel", sceneModel, () -> voltageLevel);
        }
    }

    @Override
    protected List<ExtensionsPane.Editor<Substation>> extensionEditors() {
        return ExtensionEditors.forSubstation();
    }
}
