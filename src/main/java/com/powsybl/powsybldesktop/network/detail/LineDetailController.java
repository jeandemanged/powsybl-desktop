/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.scene.control.Tab;

import java.util.List;

/**
 * Line detail: characteristics, each side's connection and solved values, editable flow limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LineDetailController extends AbstractEquipmentDetailController<Line> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal1().getVoltageLevel(), equipment.getTerminal2().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.LINES;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.section("detail.section.characteristics")
                .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getR, equipment::setR)
                .doubleField("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getX, equipment::setX)
                .doubleField("desktop.common.column.g1", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG1, equipment::setG1)
                .doubleField("desktop.common.column.b1", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB1, equipment::setB1)
                .doubleField("desktop.common.column.g2", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG2, equipment::setG2)
                .doubleField("desktop.common.column.b2", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB2, equipment::setB2);
        DetailSections.sides(form, List.of(equipment::getTerminal1, equipment::getTerminal2));
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(limitsTab());
    }

    @Override
    protected List<ExtensionsPane.Editor<Line>> extensionEditors() {
        return ExtensionEditors.forBranch();
    }
}
