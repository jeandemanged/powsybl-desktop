/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracFactory;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.openrao.data.crac.api.range.RangeType;
import com.powsybl.openrao.data.crac.io.commons.iidm.IidmPstHelper;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;

import java.util.List;

/**
 * A RAO case on PowSyBl's phase shifter test network: generator G1 feeds load LD2 (100 MW) through line L1 and,
 * in parallel, phase shifter PS1 (taps 0, 1, 2: -20, 0, 20 degrees; at 1) and line L2.
 * <ul>
 *     <li>Preventive: L1 carries about 50 MW (76 A), above its 45 A CNEC threshold: the PST, available preventively,
 *     moves to tap 2, sending L1's flow the other way (about -81 MW), within its -200 A minimum.</li>
 *     <li>Curative, after the loss of L2: L1 carries the 100 MW (152 A), above 130 A: shedding LD2 to 70 MW, available
 *     after that contingency, solves it.</li>
 * </ul>
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class RaoTestCases {

    static final String PST = "PST";
    static final String LOAD_SHEDDING = "Load shedding";
    static final String CONTINGENCY = "L2 out";

    private RaoTestCases() {
    }

    /**
     * The network, with current limits on its lines: OpenRAO's JSON CRAC importer requires them on the CNECs' branches.
     */
    static Network network() {
        Network network = PhaseShifterTestCaseFactory.create();
        for (String line : List.of("L1", "L2")) {
            network.getLine(line).getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(1000.).add();
            network.getLine(line).getOrCreateSelectedOperationalLimitsGroup2().newCurrentLimits().setPermanentLimit(1000.).add();
        }
        return network;
    }

    static Crac crac(Network network) {
        Crac crac = CracFactory.findDefault().create("crac");
        crac.newInstant("preventive", InstantKind.PREVENTIVE)
                .newInstant("outage", InstantKind.OUTAGE)
                .newInstant("curative", InstantKind.CURATIVE);
        crac.newContingency().withId(CONTINGENCY).withContingencyElement("L2", ContingencyElementType.LINE).add();
        double nominalV = network.getVoltageLevel("VL1").getNominalV();
        crac.newFlowCnec().withId("L1 preventive").withNetworkElement("L1").withInstant("preventive").withOptimized()
                .withNominalVoltage(nominalV)
                .newThreshold().withUnit(Unit.AMPERE).withSide(TwoSides.ONE).withMin(-200.).withMax(45.).add()
                .add();
        crac.newFlowCnec().withId("L1 curative").withNetworkElement("L1").withInstant("curative").withContingency(CONTINGENCY)
                .withOptimized().withNominalVoltage(nominalV)
                .newThreshold().withUnit(Unit.AMPERE).withSide(TwoSides.ONE).withMin(-130.).withMax(130.).add()
                .add();
        IidmPstHelper pst = new IidmPstHelper("PS1", network);
        crac.newPstRangeAction().withId(PST).withNetworkElement("PS1")
                .withInitialTap(pst.getInitialTap()).withTapToAngleConversionMap(pst.getTapToAngleConversionMap())
                .newTapRange().withRangeType(RangeType.ABSOLUTE).withMinTap(pst.getLowTapPosition()).withMaxTap(pst.getHighTapPosition()).add()
                .newOnInstantUsageRule().withInstant("preventive").add()
                .add();
        crac.newNetworkAction().withId(LOAD_SHEDDING)
                .newLoadAction().withNetworkElement("LD2").withActivePowerValue(70.).add()
                .newOnContingencyStateUsageRule().withContingency(CONTINGENCY).withInstant("curative").add()
                .add();
        return crac;
    }

    static RaoParameters parameters(int threads) {
        RaoParameters parameters = new RaoParameters(ReportNode.NO_OP);
        OpenRaoSearchTreeParameters searchTree = new OpenRaoSearchTreeParameters(ReportNode.NO_OP);
        searchTree.getMultithreadingParameters().setAvailableCPUs(threads);
        parameters.addExtension(OpenRaoSearchTreeParameters.class, searchTree);
        return parameters;
    }
}
