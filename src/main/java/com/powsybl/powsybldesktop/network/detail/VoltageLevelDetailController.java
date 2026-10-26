/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;

import java.util.List;

/**
 * Voltage level detail: substation (linked to its detail view), topology kind, nominal voltage and voltage limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VoltageLevelDetailController extends AbstractEquipmentDetailController<VoltageLevel> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment);
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SINGLE_LINE;
    }

    @Override
    protected void buildMain(DetailForm form) {
        DetailSections.equipmentLink(form, "desktop.common.equipment.substation", sceneModel, equipment::getNullableSubstation);
        form.text("voltageLevels.column.topologyKind", () -> equipment.getTopologyKind().name())
                .doubleField("desktop.common.column.nominalVoltage", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                        equipment::getNominalV, equipment::setNominalV)
                .doubleField("voltageLevels.column.lowVoltageLimit", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                        equipment::getLowVoltageLimit, equipment::setLowVoltageLimit)
                .doubleField("voltageLevels.column.highVoltageLimit", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                        equipment::getHighVoltageLimit, equipment::setHighVoltageLimit);
    }

    @Override
    protected List<ExtensionsPane.Editor<VoltageLevel>> extensionEditors() {
        return ExtensionEditors.forVoltageLevel();
    }
}
