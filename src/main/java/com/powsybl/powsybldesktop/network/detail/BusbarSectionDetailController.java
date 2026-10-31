/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;

import java.util.List;

/**
 * Busbar section detail: connection, its solved voltage being the symbol's.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BusbarSectionDetailController extends AbstractEquipmentDetailController<BusbarSection> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.BUSBAR_SECTIONS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
    }

    @Override
    protected EquipmentSymbol symbol() {
        return newSymbol().busbarSection(equipment);
    }

    @Override
    protected List<ExtensionsPane.Editor<BusbarSection>> extensionEditors() {
        return ExtensionEditors.forBusbarSection();
    }
}
