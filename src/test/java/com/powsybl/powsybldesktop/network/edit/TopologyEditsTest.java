/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateBranchFeederBaysBuilder;
import com.powsybl.iidm.modification.topology.CreateFeederBayBuilder;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The topology edits and line cuttings, on the network of {@link AbstractCreationDialogTest#network()}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class TopologyEditsTest extends AbstractCreationDialogTest {

    private static Line addLine(Network network) {
        NetworkEdits.apply(network, new CreateBranchFeederBaysBuilder()
                .withBranchAdder(network.newLine().setId("L").setR(1).setX(10).setG1(0).setB1(0).setG2(0).setB2(0))
                .withBusOrBusbarSectionId1("VL400_1_1").withPositionOrder1(10)
                .withBusOrBusbarSectionId2("VL225_1_1").withPositionOrder2(10)
                .build());
        return network.getLine("L");
    }

    @Test
    void sectionsAreAddedOnEveryParallelBusbar() {
        Network network = network();

        openAndConfirm(sceneModel -> TopologyEdits.newBusbarSections(sceneModel, stage, network.getBusbarSection("VL400_1_1")), network);

        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");
        assertEquals(4, voltageLevel.getNodeBreakerView().getBusbarSectionCount());
        assertTrue(voltageLevel.getNodeBreakerView().getBusbarSectionStream()
                .allMatch(busbarSection -> busbarSection.getExtension(BusbarSectionPosition.class) != null));
    }

    @Test
    void busbarSectionWithItsPosition() {
        Network network = network();
        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");

        BusbarSection busbarSection = openAndConfirm(sceneModel -> TopologyEdits.newBusbarSection(sceneModel, stage, voltageLevel), network,
                dialogPane -> {
                    from(dialogPane).lookup(".text-field").nth(3).queryAs(TextField.class).setText("3");
                    from(dialogPane).lookup(".text-field").nth(4).queryAs(TextField.class).setText("1");
                });

        assertNotNull(busbarSection);
        assertEquals(3, busbarSection.getExtension(BusbarSectionPosition.class).getBusbarIndex());
    }

    @Test
    void couplingDeviceBetweenTwoBuses() {
        Network network = network();
        VoltageLevel voltageLevel = network.getVoltageLevel("VL63");
        Bus bus = openAndConfirm(sceneModel -> TopologyEdits.newBus(sceneModel, stage, voltageLevel), network);
        assertNotNull(bus);

        openAndConfirm(sceneModel -> TopologyEdits.newCouplingDevice(sceneModel, stage, voltageLevel, null), network);

        assertEquals(1, voltageLevel.getBusBreakerView().getSwitchCount());
    }

    @Test
    void switchAndInternalConnectionBetweenNodes() {
        Network network = network();
        VoltageLevel voltageLevel = network.getVoltageLevel("VL225");
        int node = network.getBusbarSection("VL225_1_1").getTerminal().getNodeBreakerView().getNode();

        Switch aSwitch = openAndConfirm(sceneModel -> TopologyEdits.newSwitch(sceneModel, stage, voltageLevel, node), network);
        openAndConfirm(sceneModel -> TopologyEdits.newInternalConnection(sceneModel, stage, voltageLevel, null), network);

        assertEquals(SwitchKind.BREAKER, aSwitch.getKind());
        assertEquals(node, voltageLevel.getNodeBreakerView().getNode1(aSwitch.getId()));
        assertEquals(1, voltageLevel.getNodeBreakerView().getInternalConnectionCount());
    }

    @Test
    void busbarSectionGoesToTheChosenVoltageLevel() {
        Network network = network();

        BusbarSection busbarSection = openAndConfirm(sceneModel -> TopologyEdits.newBusbarSection(sceneModel, stage, network.getVoltageLevel("VL400"), true),
                network, dialogPane -> {
                    // node breaker voltage levels only
                    ComboBox<VoltageLevel> voltageLevels = from(dialogPane)
                            .lookup((ComboBox<?> box) -> box.getItems().contains(network.getVoltageLevel("VL225"))).query();
                    assertFalse(voltageLevels.getItems().contains(network.getVoltageLevel("VL63")));
                    voltageLevels.setValue(network.getVoltageLevel("VL225"));
                });

        assertEquals("VL225", busbarSection.getTerminal().getVoltageLevel().getId());
        assertTrue(busbarSection.getId().startsWith("VL225_BBS"));
    }

    @Test
    void feederMovesToAnotherVoltageLevel() {
        Network network = network();
        NetworkEdits.apply(network, new CreateFeederBayBuilder()
                .withInjectionAdder(network.getVoltageLevel("VL400").newLoad().setId("LD").setP0(1).setQ0(0))
                .withBusOrBusbarSectionId("VL400_1_1").withInjectionPositionOrder(1).build());
        Load load = network.getLoad("LD");

        openAndConfirm(sceneModel -> TopologyEdits.moveFeeder(sceneModel, stage, load), network, dialogPane -> {
            ComboBox<VoltageLevel> voltageLevels = from(dialogPane)
                    .lookup((ComboBox<?> box) -> box.getItems().contains(network.getVoltageLevel("VL63"))).query();
            voltageLevels.setValue(network.getVoltageLevel("VL63"));
        });

        assertEquals("VL63", load.getTerminal().getVoltageLevel().getId());
    }

    @Test
    void lineTappedThenMergedBack() {
        Network network = network();
        addLine(network);

        openAndConfirm(sceneModel -> LineEdits.tapLine(sceneModel, stage, network.getLine("L")), network);

        assertNull(network.getLine("L"));
        assertNotNull(network.getLine("L_1"));
        assertNotNull(network.getLine("L_2"));
        Line tap = network.getLine("L_TAP1");
        assertNotNull(tap);
        assertEquals("VL63", tap.getTerminal2().getVoltageLevel().getId());

        openAndConfirm(sceneModel -> LineEdits.revertTapLine(sceneModel, stage, network, network.getLine("L_1")), network);

        assertEquals(1, network.getLineCount());
        assertNull(network.getVoltageLevel("L_VL"));
    }

    @Test
    void voltageLevelConnectedOnLineThenMergedBack() {
        Network network = network();
        addLine(network);

        openAndConfirm(sceneModel -> LineEdits.connectVoltageLevelOnLine(sceneModel, stage, network.getLine("L")), network);

        assertEquals("VL63", network.getLine("L_1").getTerminal2().getVoltageLevel().getId());
        assertEquals("VL63", network.getLine("L_2").getTerminal1().getVoltageLevel().getId());

        openAndConfirm(sceneModel -> LineEdits.revertConnectVoltageLevelOnLine(sceneModel, stage, network, network.getLine("L_1")), network);

        assertEquals(1, network.getLineCount());
    }
}
