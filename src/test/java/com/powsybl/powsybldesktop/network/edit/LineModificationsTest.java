/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateBranchFeederBaysBuilder;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LineModificationsTest {

    private final Network network = EditTestNetworks.network();
    private final Line line = addLine();

    private Line addLine() {
        NetworkEdits.apply(network, new CreateBranchFeederBaysBuilder()
                .withBranchAdder(network.newLine().setId("L").setR(1).setX(10).setG1(0).setB1(0).setG2(0).setB2(0))
                .withBusOrBusbarSectionId1("VL400_1_1").withPositionOrder1(10)
                .withBusOrBusbarSectionId2("VL225_1_1").withPositionOrder2(10)
                .build());
        return network.getLine("L");
    }

    @Test
    void lineTappedThenMergedBack() {
        LineModifications.tapLine(network, line, new LineModifications.TapSpec(50, "L_VL", true, "L_1", "L_2", "L_TAP", 0, 1),
                EditTestNetworks.at(network, "VL63", ConnectablePosition.Direction.TOP));

        assertNull(network.getLine("L"));
        assertEquals("VL63", network.getLine("L_TAP").getTerminal2().getVoltageLevel().getId());
        assertNotNull(network.getSubstation("L_VL_S"));
        List<Line> atTeePoint = LineModifications.teePointLines(network.getLine("L_1"));
        assertEquals(3, atTeePoint.size());
        assertSame(network.getLine("L_1"), atTeePoint.getFirst());

        LineModifications.revertTapLine(network, atTeePoint.get(0), atTeePoint.get(1), network.getLine("L_TAP"), "MERGED", "Merged");

        assertEquals(1, network.getLineCount());
        assertEquals("Merged", network.getLine("MERGED").getOptionalName().orElseThrow());
        assertNull(network.getVoltageLevel("L_VL"));
    }

    @Test
    void voltageLevelConnectedOnLineThenMergedBack() {
        assertEquals("VL63", LineModifications.otherThanLineEnds(network, line).getId());

        LineModifications.connectVoltageLevelOnLine(network, line, 50, "L_1", "L_2", EditTestNetworks.at(network, "VL63", ConnectablePosition.Direction.TOP));

        Line line1 = network.getLine("L_1");
        assertEquals("VL63", line1.getTerminal2().getVoltageLevel().getId());
        assertEquals("VL63", network.getLine("L_2").getTerminal1().getVoltageLevel().getId());
        Line sharing = LineModifications.sharingAVoltageLevel(network.getLineStream().toList(), line1);
        assertSame(network.getLine("L_2"), sharing);

        LineModifications.revertConnectVoltageLevelOnLine(network, line1, sharing, "MERGED", null);

        assertEquals(1, network.getLineCount());
        assertNotNull(network.getLine("MERGED"));
    }

    @Test
    void teePointIsReplacedByAVoltageLevel() {
        LineModifications.tapLine(network, line, new LineModifications.TapSpec(50, "L_VL", false, "L_1", "L_2", "L_TAP", 0, 1),
                EditTestNetworks.at(network, "VL63", ConnectablePosition.Direction.TOP));
        String bus = Topologies.busesOrBusbarSections(network.getVoltageLevel("VL63")).getFirst().getId();

        LineModifications.replaceTeePoint(network, network.getLine("L_1"), network.getLine("L_2"), network.getLine("L_TAP"), bus, "N1", "N2");

        assertNull(network.getVoltageLevel("L_VL"));
        assertEquals("VL63", network.getLine("N1").getTerminal2().getVoltageLevel().getId());
        assertEquals("VL63", network.getLine("N2").getTerminal1().getVoltageLevel().getId());
    }
}
