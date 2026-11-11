/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.powsybldesktop.network.MapTestNetworkFactory;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The map test networks mark 30% of their lines and of their substations with violations.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapTestNetworkViolationsTest {

    @Test
    void thirtyPercentOfLinesAndSubstationsAreMarked() {
        Network network = MapTestNetworkFactory.create();
        MapNetworkData data = MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS), null, List.of(),
                _ -> null, (_, defaultColor) -> defaultColor);

        long markedSubstations = Arrays.stream(data.substationIds()).map(data::violationMarker).filter(Objects::nonNull).count();
        long markedLines = Arrays.stream(data.lineIds()).map(data::violationMarker).filter(Objects::nonNull).count();

        assertEquals(Math.round(data.substationIds().length * 0.3), markedSubstations);
        assertEquals(Math.round(data.lineIds().length * 0.3), markedLines);
    }
}
