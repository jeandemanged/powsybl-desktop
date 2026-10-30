/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;

import java.util.List;

/**
 * Switch detail: kind, open and retained states, its nodes or buses, read-only.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SwitchDetailController extends AbstractEquipmentDetailController<Switch> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SWITCHES;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.text("switches.column.kind", () -> equipment.getKind().name())
                .booleanField("switches.column.open", equipment::isOpen, equipment::setOpen)
                .booleanField("switches.column.retained", equipment::isRetained, equipment::setRetained);
        // IIDM can't move a switch
        VoltageLevel voltageLevel = equipment.getVoltageLevel();
        if (voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER) {
            form.text("detail.switch.node1", () -> String.valueOf(voltageLevel.getNodeBreakerView().getNode1(equipment.getId())))
                    .text("detail.switch.node2", () -> String.valueOf(voltageLevel.getNodeBreakerView().getNode2(equipment.getId())));
        } else {
            form.text("detail.switch.bus1", () -> voltageLevel.getBusBreakerView().getBus1(equipment.getId()).getId())
                    .text("detail.switch.bus2", () -> voltageLevel.getBusBreakerView().getBus2(equipment.getId()).getId());
        }
    }

    @Override
    protected List<ExtensionsPane.Editor<Switch>> extensionEditors() {
        return ExtensionEditors.forSwitch();
    }
}
