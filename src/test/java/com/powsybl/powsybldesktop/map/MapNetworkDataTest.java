/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapNetworkDataTest {

    @Test
    void worstVoltageViolationIsTheFarthestFromOnePu() {
        Network network = Network.create("test", "code");
        // over by 0.075 pu, under by 0.111 pu: the under voltage is the worst
        addSubstation(network, "S1", 0, List.of(new double[] {400, 380, 420, 430}, new double[] {225, 210, 240, 200}));
        addSubstation(network, "S2", 1, List.of(new double[] {400, 380, 420, 430}));
        addSubstation(network, "S3", 2, List.of(new double[] {400, 380, 420, 400}));
        addSubstation(network, "S4", 3, List.of(new double[] {400, 380, 420, Double.NaN}));

        MapNetworkData data = MapNetworkData.build(network, v -> null, (baseVoltage, defaultColor) -> defaultColor);

        int s1 = Arrays.asList(data.substationIds()).indexOf("S1");
        int s2 = Arrays.asList(data.substationIds()).indexOf("S2");
        int s3 = Arrays.asList(data.substationIds()).indexOf("S3");
        int s4 = Arrays.asList(data.substationIds()).indexOf("S4");
        assertEquals(-25.0 / 225, data.substationViolations()[s1], 1e-9);
        assertEquals(200.0 / 225, data.substationViolationVoltages()[s1], 1e-9);
        assertEquals(0.075, data.substationViolations()[s2], 1e-9);
        assertEquals(1.075, data.substationViolationVoltages()[s2], 1e-9);
        assertEquals(0, data.substationViolations()[s3]);
        assertTrue(Double.isNaN(data.substationViolationVoltages()[s3]));
        assertTrue(Double.isNaN(data.substationViolations()[s4]));
        assertTrue(Double.isNaN(data.substationViolationVoltages()[s4]));
    }

    /**
     * @param voltageLevels nominal voltage, low limit, high limit and bus voltage, in kV
     */
    private static void addSubstation(Network network, String id, int position, List<double[]> voltageLevels) {
        Substation substation = network.newSubstation().setId(id).add();
        substation.newExtension(SubstationPositionAdder.class).withCoordinate(new Coordinate(45, position)).add();
        for (double[] v : voltageLevels) {
            String vlId = id + "_" + (int) v[0];
            VoltageLevel voltageLevel = substation.newVoltageLevel()
                    .setId(vlId)
                    .setNominalV(v[0])
                    .setLowVoltageLimit(v[1])
                    .setHighVoltageLimit(v[2])
                    .setTopologyKind(TopologyKind.BUS_BREAKER)
                    .add();
            voltageLevel.getBusBreakerView().newBus().setId(vlId + "_BUS").add().setV(v[3]);
            voltageLevel.newLoad().setId(vlId + "_LOAD").setBus(vlId + "_BUS").setConnectableBus(vlId + "_BUS").setP0(0).setQ0(0).add();
        }
    }
}
