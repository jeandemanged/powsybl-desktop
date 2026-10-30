/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.sld.library.SldComponentTypeName;

import java.util.List;

/**
 * Static var compensator detail: susceptance range, local targets and voltage regulation.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class StaticVarCompensatorDetailController extends AbstractEquipmentDetailController<StaticVarCompensator> {

    // IIDM's settable modes for a static var compensator (VoltageRegulationUtils), locally as remotely
    private static final List<RegulationMode> REGULATION_MODES = List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER,
            RegulationMode.VOLTAGE_PER_REACTIVE_POWER);

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        form.section("detail.section.susceptance")
                .doubleField("detail.svc.bMin", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getBmin, equipment::setBmin)
                .doubleField("detail.svc.bMax", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getBmax, equipment::setBmax);
        DetailSections.localTargets(form, equipment, true);
        DetailSections.voltageRegulation(form, "desktop.common.column.voltageRegulation", equipment, REGULATION_MODES, sceneModel);
    }

    @Override
    protected EquipmentSymbol symbol() {
        return newSymbol().injection(() -> SldComponentTypeName.STATIC_VAR_COMPENSATOR, equipment::getTerminal);
    }

    @Override
    protected List<ExtensionsPane.Editor<StaticVarCompensator>> extensionEditors() {
        return ExtensionEditors.forStaticVarCompensator();
    }
}
