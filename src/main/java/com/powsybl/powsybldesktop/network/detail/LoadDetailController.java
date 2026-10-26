/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;

import java.util.List;

/**
 * Load detail: type, P0/Q0 and the load model's type.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LoadDetailController extends AbstractEquipmentDetailController<Load> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.LOADS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.choiceField("desktop.common.column.type", DetailSections.values(LoadType.class), DetailSections.enumFormat(),
                        equipment::getLoadType, equipment::setLoadType)
                .text("detail.load.model", () -> equipment.getModel().map(model -> model.getType().name()).orElse(null));
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        form.section("detail.section.setpoints")
                .doubleField("desktop.common.column.p0", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getP0, equipment::setP0)
                .doubleField("desktop.common.column.q0", DetailForm.MVAR, GuiParameters.Quantity.POWER, equipment::getQ0, equipment::setQ0);
        DetailSections.solvedValues(form, "desktop.common.columnGroup.solvedValues", equipment::getTerminal);
    }

    @Override
    protected List<ExtensionsPane.Editor<Load>> extensionEditors() {
        return ExtensionEditors.forLoad();
    }
}
