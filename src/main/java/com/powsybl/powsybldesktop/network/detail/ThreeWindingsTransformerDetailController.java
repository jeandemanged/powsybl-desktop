/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Three-winding transformer detail: star rated voltage, each leg's characteristics, connection and solved values,
 * each leg's ratio and phase tap changers, editable flow limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ThreeWindingsTransformerDetailController extends AbstractEquipmentDetailController<ThreeWindingsTransformer> {

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return equipment.getLegStream().map(leg -> leg.getTerminal().getVoltageLevel()).distinct().toList();
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.TRANSFORMERS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.doubleField("detail.transformer.ratedU0", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, equipment::getRatedU0, equipment::setRatedU0);
        for (ThreeWindingsTransformer.Leg leg : equipment.getLegs()) {
            form.section("detail.section.leg", leg.getSide().getNum())
                    .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, leg::getR, leg::setR)
                    .doubleField("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, leg::getX, leg::setX)
                    .doubleField("desktop.common.column.g", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, leg::getG, leg::setG)
                    .doubleField("desktop.common.column.b", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, leg::getB, leg::setB)
                    .doubleField("transformers.column.ratedU", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, leg::getRatedU, leg::setRatedU)
                    .doubleField("transformers.column.ratedS", DetailForm.MVA, GuiParameters.Quantity.POWER, leg::getRatedS, leg::setRatedS);
            DetailSections.connection(form, null, leg::getTerminal);
            DetailSections.solvedValues(form, null, leg::getTerminal);
        }
    }

    @Override
    protected List<Tab> extraTabs() {
        VBox tapChangers = new VBox(16);
        for (ThreeWindingsTransformer.Leg leg : equipment.getLegs()) {
            int side = leg.getSide().getNum();
            TapChangerPane ratio = register(TapChangerPane.ratio(leg, "transformers.column.ratioTapChanger", sceneModel, guiParameters(), onEdit()));
            TapChangerPane phase = register(TapChangerPane.phase(leg, "transformers.column.phaseTapChanger", sceneModel, guiParameters(), onEdit()));
            Label title = new Label(Messages.get("detail.section.leg", side));
            title.getStyleClass().add("limits-side-title");
            tapChangers.getChildren().addAll(title, ratio, phase);
        }
        return List.of(tab("detail.tab.tapChangers", scrolling(tapChangers)), limitsTab());
    }

    @Override
    protected List<ExtensionsPane.Editor<ThreeWindingsTransformer>> extensionEditors() {
        return ExtensionEditors.forThreeWindingsTransformer();
    }
}
