/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.scene.Node;
import javafx.scene.control.Tab;

import java.util.List;

/**
 * Generator detail: active power, local targets, voltage regulation, reactive limits with their P/Q diagram.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GeneratorDetailController extends AbstractEquipmentDetailController<Generator> {

    // IIDM's settable modes for a generator (VoltageRegulationUtils), reactive power only with a remote terminal
    static final List<RegulationMode> REGULATION_MODES = List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER);

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.GENERATORS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.choiceField("detail.generator.energySource", DetailSections.values(EnergySource.class), DetailSections.enumFormat(),
                        equipment::getEnergySource, equipment::setEnergySource)
                .text("detail.generator.condenser", () -> DetailSections.yesNo(equipment.isCondenser()))
                .doubleField("transformers.column.ratedS", DetailForm.MVA, GuiParameters.Quantity.POWER, equipment::getRatedS, equipment::setRatedS);
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        form.section("detail.section.activePower")
                .doubleField("desktop.common.column.minP", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getMinP, equipment::setMinP)
                .doubleField("desktop.common.column.maxP", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getMaxP, equipment::setMaxP)
                .doubleField("generators.column.targetP", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getTargetP, equipment::setTargetP);
        DetailSections.localTargets(form, equipment, true);
        DetailSections.voltageRegulation(form, "desktop.common.column.voltageRegulation", equipment, REGULATION_MODES, sceneModel);
        DetailSections.solvedValues(form, "desktop.common.columnGroup.solvedValues", equipment::getTerminal);
    }

    @Override
    protected Node mainSide() {
        return capabilityChart(equipment, equipment::getMinP, equipment::getMaxP, this::operatingPoints);
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(reactiveLimitsTab(equipment, equipment::getMinP, equipment::getMaxP, this::operatingPoints));
    }

    @Override
    protected List<ExtensionsPane.Editor<Generator>> extensionEditors() {
        return ExtensionEditors.forGenerator();
    }

    ReactiveLimitsPane.OperatingPoints operatingPoints() {
        // generator convention, the terminal's being load convention
        return new ReactiveLimitsPane.OperatingPoints(equipment.getTargetP(), equipment.getLocalTargetQ(),
                -equipment.getTerminal().getP(), -equipment.getTerminal().getQ());
    }
}
