/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.testutil;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;

/**
 * A root network merging two subnetworks, {@link #NODE_BREAKER} (with busbar sections) and {@link #BUS_BREAKER},
 * joined by the tie line {@link #TIE_LINE}, held by the root network.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SubnetworksTestFactory {

    public static final String NODE_BREAKER = "fourSubstations";
    public static final String BUS_BREAKER = "sim1";
    public static final String TIE_LINE = "BL_BB + BL_NB";

    private SubnetworksTestFactory() {
    }

    public static Network create() {
        Network nodeBreaker = FourSubstationsNodeBreakerFactory.create();
        nodeBreaker.getVoltageLevel("S1VL2").newBoundaryLine()
                .setId("BL_NB").setNode(100).setR(1).setX(1).setG(0).setB(0).setP0(0).setQ0(0).setPairingKey("XNODE")
                .add();
        Network busBreaker = EurostagTutorialExample1Factory.create();
        busBreaker.getVoltageLevel("VLHV1").newBoundaryLine()
                .setId("BL_BB").setBus("NHV1").setConnectableBus("NHV1").setR(1).setX(1).setG(0).setB(0).setP0(0).setQ0(0)
                .setPairingKey("XNODE")
                .add();
        return Network.merge(nodeBreaker, busBreaker);
    }
}
