/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;

import java.util.List;

/**
 * Switch detail: kind, open and retained states.
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
    }

    @Override
    protected List<ExtensionsPane.Editor<Switch>> extensionEditors() {
        return ExtensionEditors.forSwitch();
    }
}
