/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Two-winding transformer detail: characteristics, each side's connection and solved values, ratio and phase tap
 * changers (replacing the transformers table's former steps dialog), editable flow limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TwoWindingsTransformerDetailController extends AbstractEquipmentDetailController<TwoWindingsTransformer> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal1().getVoltageLevel(), equipment.getTerminal2().getVoltageLevel()).stream().distinct().toList();
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.TRANSFORMERS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.section("detail.section.characteristics")
                .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getR, equipment::setR)
                .doubleField("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, equipment::getX, equipment::setX)
                .doubleField("desktop.common.column.g", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG, equipment::setG)
                .doubleField("desktop.common.column.b", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB, equipment::setB)
                .doubleField("detail.transformer.ratedU1", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, equipment::getRatedU1, equipment::setRatedU1)
                .doubleField("detail.transformer.ratedU2", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, equipment::getRatedU2, equipment::setRatedU2)
                .doubleField("transformers.column.ratedS", DetailForm.MVA, GuiParameters.Quantity.POWER, equipment::getRatedS, equipment::setRatedS);
        DetailSections.sides(form, List.of(equipment::getTerminal1, equipment::getTerminal2));
    }

    @Override
    protected List<Tab> extraTabs() {
        VBox tapChangers = new VBox(16,
                register(TapChangerPane.ratio(equipment, "transformers.column.ratioTapChanger", sceneModel, guiParameters(), onEdit())),
                register(TapChangerPane.phase(equipment, "transformers.column.phaseTapChanger", sceneModel, guiParameters(), onEdit())));
        return List.of(tab("detail.tab.tapChangers", scrolling(tapChangers)), limitsTab());
    }

    @Override
    protected List<ExtensionsPane.Editor<TwoWindingsTransformer>> extensionEditors() {
        return ExtensionEditors.forTwoWindingsTransformer();
    }
}
