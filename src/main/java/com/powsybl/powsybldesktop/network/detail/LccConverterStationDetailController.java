/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;

import java.util.List;

/**
 * LCC converter station detail: loss and power factors, HVDC line.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LccConverterStationDetailController extends AbstractEquipmentDetailController<LccConverterStation> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.LCC_CONVERTER_STATIONS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        HvdcDetails.converterStation(form, equipment, sceneModel);
        form.doubleField("lccConverterStations.column.powerFactor", null, GuiParameters.Quantity.POWER_FACTOR,
                equipment::getPowerFactor, powerFactor -> equipment.setPowerFactor((float) powerFactor));
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        DetailSections.solvedValues(form, "desktop.common.columnGroup.solvedValues", equipment::getTerminal);
    }

    @Override
    protected List<ExtensionsPane.Editor<LccConverterStation>> extensionEditors() {
        return ExtensionEditors.forInjection();
    }
}
