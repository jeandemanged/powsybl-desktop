/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.sld.library.SldComponentTypeName;
import javafx.scene.Node;
import javafx.scene.control.Tab;

import java.util.List;

/**
 * VSC converter station detail: loss factor, HVDC line, local targets, voltage regulation, reactive limits with their
 * P/Q diagram over the HVDC line's [-max P, max P] range.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VscConverterStationDetailController extends AbstractEquipmentDetailController<VscConverterStation> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.VSC_CONVERTER_STATIONS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        HvdcDetails.converterStation(form, equipment, sceneModel);
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        DetailSections.localTargets(form, equipment, true);
        // IIDM's settable modes for a VSC converter station are a generator's (VoltageRegulationUtils)
        DetailSections.voltageRegulation(form, "desktop.common.column.voltageRegulation", equipment, GeneratorDetailController.REGULATION_MODES, sceneModel);
    }

    @Override
    protected Node mainSide() {
        return capabilityChart(equipment, () -> -maxP(), this::maxP, this::operatingPoints);
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(reactiveLimitsTab(equipment, () -> -maxP(), this::maxP, this::operatingPoints));
    }

    @Override
    protected EquipmentSymbol symbol() {
        return newSymbol().injection(() -> SldComponentTypeName.VSC_CONVERTER_STATION, equipment::getTerminal);
    }

    @Override
    protected List<ExtensionsPane.Editor<VscConverterStation>> extensionEditors() {
        return ExtensionEditors.forInjection();
    }

    private double maxP() {
        HvdcLine line = equipment.getHvdcLine();
        return line == null ? Double.NaN : line.getMaxP();
    }

    private ReactiveLimitsPane.OperatingPoints operatingPoints() {
        // generator convention, the terminal's being load convention
        return new ReactiveLimitsPane.OperatingPoints(HvdcDetails.targetP(equipment), equipment.getLocalTargetQ(),
                -equipment.getTerminal().getP(), -equipment.getTerminal().getQ());
    }
}
