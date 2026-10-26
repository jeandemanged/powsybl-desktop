/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.scene.control.Tab;

import java.util.List;

/**
 * Tie line detail: its boundary lines (linked to their own detail views, where their characteristics are edited), the
 * resulting characteristics, read-only, each side's connection and solved values, editable flow limits (the boundary
 * lines').
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TieLineDetailController extends AbstractEquipmentDetailController<TieLine> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal1().getVoltageLevel(), equipment.getTerminal2().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.TIE_LINES;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.text("desktop.common.column.pairingKey", equipment::getPairingKey);
        DetailSections.equipmentLink(form, "detail.tieLine.boundaryLine1", sceneModel, equipment::getBoundaryLine1);
        DetailSections.equipmentLink(form, "detail.tieLine.boundaryLine2", sceneModel, equipment::getBoundaryLine2);
        form.section("detail.section.characteristics")
                .value("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getR)
                .value("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getX)
                .value("desktop.common.column.g1", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG1)
                .value("desktop.common.column.b1", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB1)
                .value("desktop.common.column.g2", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG2)
                .value("desktop.common.column.b2", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB2);
        DetailSections.sides(form, List.of(equipment::getTerminal1, equipment::getTerminal2));
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(limitsTab());
    }

    @Override
    protected List<ExtensionsPane.Editor<TieLine>> extensionEditors() {
        return ExtensionEditors.forTieLine();
    }
}
