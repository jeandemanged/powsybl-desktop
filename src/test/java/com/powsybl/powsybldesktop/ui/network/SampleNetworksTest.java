/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SampleNetworksTest {

    // as SmallGrid's parallel lines: the CRAC Generator names its contingencies after the lines
    @Test
    void duplicateLineNamesAreSuffixedById() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getLine("NHV1_NHV2_2").setName("400 kV");
        network.getLine("NHV1_NHV2_1").setName("400 kV");

        NetworksController.renameDuplicateLines(network);

        assertEquals(Optional.of("400 kV-1"), network.getLine("NHV1_NHV2_1").getOptionalName());
        assertEquals(Optional.of("400 kV-2"), network.getLine("NHV1_NHV2_2").getOptionalName());
        assertEquals(Optional.empty(), network.getGenerator("GEN").getOptionalName());
    }
}
