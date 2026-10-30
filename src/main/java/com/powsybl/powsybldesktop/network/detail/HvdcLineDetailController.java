/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;

import java.util.Arrays;
import java.util.List;

/**
 * HVDC line detail: characteristics, setpoint and converters mode, then each side's converter station (linked to its
 * own detail view) and connection, solved values being the symbol's.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class HvdcLineDetailController extends AbstractEquipmentDetailController<HvdcLine> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return Arrays.stream(TwoSides.values())
                .map(side -> equipment.getConverterStation(side).getTerminal().getVoltageLevel())
                .distinct()
                .toList();
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.HVDC_LINES;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.section("detail.section.characteristics")
                .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getR, equipment::setR)
                .doubleField("desktop.common.column.nominalVoltage", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                        equipment::getNominalV, equipment::setNominalV)
                .doubleField("desktop.common.column.maxP", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getMaxP, equipment::setMaxP);
        form.section("detail.section.setpoints")
                .doubleField("hvdcLines.column.activePowerSetpoint", DetailForm.MW, GuiParameters.Quantity.POWER,
                        equipment::getActivePowerSetpoint, equipment::setActivePowerSetpoint)
                .choiceField("hvdcLines.column.convertersMode", DetailSections.values(HvdcLine.ConvertersMode.class), DetailSections.enumFormat(),
                        equipment::getConvertersMode, equipment::setConvertersMode);
        for (TwoSides side : TwoSides.values()) {
            form.section("limits.side", side.getNum());
            DetailSections.equipmentLink(form, "hvdcLines.column.converterStation", sceneModel, () -> equipment.getConverterStation(side));
            DetailSections.connection(form, null, () -> equipment.getConverterStation(side).getTerminal());
        }
    }

    @Override
    protected EquipmentSymbol symbol() {
        return newSymbol().hvdcLine(equipment);
    }

    @Override
    protected List<ExtensionsPane.Editor<HvdcLine>> extensionEditors() {
        return ExtensionEditors.forHvdcLine();
    }
}
