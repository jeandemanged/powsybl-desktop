/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class InjectionCreationsTest extends AbstractCreationDialogTest {

    private Optional<Identifiable<?>> createAndConfirm(Network network, InjectionCreations.Kind kind, VoltageLevel voltageLevel,
                                                      Identifiable<?> busOrBusbarSection) {
        return Optional.ofNullable(openAndConfirm(sceneModel -> InjectionCreations.create(sceneModel, stage, kind, voltageLevel, busOrBusbarSection), network));
    }

    @Test
    void loadIsCreatedWithItsBayAndPosition() {
        Network network = Network.create("test", ContainerCreations.SOURCE_FORMAT);
        VoltageLevel voltageLevel = network.newVoltageLevel().setId("VL").setNominalV(225).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        ContainerCreations.createTopology(network, "VL", TopologyKind.NODE_BREAKER, 2, 1, SwitchKind.BREAKER, true);
        int switchCount = voltageLevel.getNodeBreakerView().getSwitchCount();

        Load load = (Load) createAndConfirm(network, InjectionCreations.Kind.LOAD, voltageLevel, network.getBusbarSection("VL_1_1")).orElseThrow();

        assertEquals("VL_LD1", load.getId());
        assertTrue(load.getTerminal().isConnected());
        // a breaker and a disconnector per parallel busbar section, the one to the other busbar open
        assertEquals(switchCount + 3, voltageLevel.getNodeBreakerView().getSwitchCount());
        ConnectablePosition<Load> position = load.getExtension(ConnectablePosition.class);
        assertNotNull(position);
        assertEquals(ConnectablePosition.Direction.BOTTOM, position.getFeeder().getDirection());
        assertTrue(TopologyModificationUtils.getFeederPositions(voltageLevel).contains(position.getFeeder().getOrder().orElseThrow()));
    }

    @Test
    void connectablesWithoutPositionGetNone() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("S1VL2");

        Load load = (Load) createAndConfirm(network, InjectionCreations.Kind.LOAD, voltageLevel, network.getBusbarSection("S1VL2_BBS1")).orElseThrow();

        assertTrue(load.getTerminal().isConnected());
        assertNull(load.getExtension(ConnectablePosition.class));
    }

    @Test
    void generatorRegulatesItsVoltageLevelsNominalVoltageByDefault() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLGEN");

        Generator generator = (Generator) createAndConfirm(network, InjectionCreations.Kind.GENERATOR, voltageLevel, null).orElseThrow();

        assertEquals("NGEN", generator.getTerminal().getBusBreakerView().getBus().getId());
        assertTrue(generator.isWithMode(RegulationMode.VOLTAGE));
        assertTrue(generator.getVoltageRegulation().isRegulating());
        assertEquals(voltageLevel.getNominalV(), generator.getRegulatingTargetV());
    }

    @Test
    void wrongValueKeepsTheDialogOpen() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLLOAD");
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        AtomicReference<Optional<Identifiable<?>>> created = new AtomicReference<>();
        Platform.runLater(() -> created.set(InjectionCreations.create(SceneModel.main(mainModel), stage,
                InjectionCreations.Kind.SHUNT_COMPENSATOR, voltageLevel, null)));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane dialogPane = lookup(".dialog-pane").queryAs(DialogPane.class);
        // the linear model's B per section is required by IIDM
        clickOn(dialogPane.lookupButton(ButtonType.OK));
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(dialogPane.getScene().getWindow().isShowing());
        assertEquals(0, network.getShuntCompensatorCount());

        TextField bPerSection = from(dialogPane).lookup(".text-field").nth(2).queryAs(TextField.class);
        interact(() -> bPerSection.setText("0.001"));
        // the error popup may cover the button
        interact(() -> ((Button) dialogPane.lookupButton(ButtonType.OK)).fire());
        WaitForAsyncUtils.waitForFxEvents();

        ShuntCompensator shunt = (ShuntCompensator) created.get().orElseThrow();
        assertEquals(0.001, shunt.getB(1));
    }
}
