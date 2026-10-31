/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Boundary line detail: pairing, characteristics, P0/Q0, the optional generation part (with its reactive limits tab),
 * the boundary side's computed values, editable flow limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BoundaryLineDetailController extends AbstractEquipmentDetailController<BoundaryLine> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.BOUNDARY_LINES;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.textField("desktop.common.column.pairingKey", equipment::getPairingKey, equipment::setPairingKey);
        DetailSections.equipmentLink(form, "desktop.common.equipment.tieLine", sceneModel, () -> equipment.getTieLine().orElse(null));
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        form.section("detail.section.characteristics")
                .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getR, equipment::setR)
                .doubleField("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getX, equipment::setX)
                .doubleField("desktop.common.column.g", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG, equipment::setG)
                .doubleField("desktop.common.column.b", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB, equipment::setB);
        form.section("detail.section.setpoints")
                .doubleField("desktop.common.column.p0", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getP0, equipment::setP0)
                .doubleField("desktop.common.column.q0", DetailForm.MVAR, GuiParameters.Quantity.POWER, equipment::getQ0, equipment::setQ0);
        BoundaryLine.Generation generation = equipment.getGeneration();
        if (generation != null) {
            form.section("detail.boundaryLine.generation")
                    .doubleField("desktop.common.column.minP", DetailForm.MW, GuiParameters.Quantity.POWER, generation::getMinP, generation::setMinP)
                    .doubleField("desktop.common.column.maxP", DetailForm.MW, GuiParameters.Quantity.POWER, generation::getMaxP, generation::setMaxP)
                    .doubleField("generators.column.targetP", DetailForm.MW, GuiParameters.Quantity.POWER, generation::getTargetP, generation::setTargetP)
                    .doubleField("desktop.common.column.targetQ", DetailForm.MVAR, GuiParameters.Quantity.POWER, generation::getTargetQ, generation::setTargetQ)
                    .booleanField("desktop.common.column.voltageRegulation", generation::isVoltageRegulationOn, generation::setVoltageRegulationOn)
                    .doubleField("desktop.common.column.targetV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, generation::getTargetV, generation::setTargetV);
        }
    }

    @Override
    protected List<Tab> extraTabs() {
        List<Tab> tabs = new ArrayList<>();
        BoundaryLine.Generation generation = equipment.getGeneration();
        if (generation != null) {
            // generator convention, the terminal's being load convention
            tabs.add(reactiveLimitsTab(generation, generation::getMinP, generation::getMaxP, () -> new ReactiveLimitsPane.OperatingPoints(
                    generation.getTargetP(), generation.getTargetQ(), -equipment.getTerminal().getP(), -equipment.getTerminal().getQ())));
        }
        tabs.add(limitsTab());
        return tabs;
    }

    @Override
    protected EquipmentSymbol symbol() {
        return newSymbol().boundaryLine(equipment);
    }

    @Override
    protected List<ExtensionsPane.Editor<BoundaryLine>> extensionEditors() {
        return ExtensionEditors.forBoundaryLine();
    }
}
