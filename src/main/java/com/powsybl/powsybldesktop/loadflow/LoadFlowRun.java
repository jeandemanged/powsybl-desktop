/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.powsybldesktop.utils.Futures;
import com.powsybl.powsybldesktop.violations.NetworkViolations;

/**
 * A load flow run, with its report and the violations of the state it leaves the network in.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LoadFlowRun {

    private LoadFlowRun() {
    }

    /**
     * Runs a load flow on {@code network}, to be called off the FX thread; interrupting the calling thread cancels it.
     */
    public static LoadFlowResultAndReport run(Network network, LoadFlowParameters parameters) throws Exception {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.loadFlow")
                .withTimestamp()
                .build();
        LoadFlowRunParameters runParameters = LoadFlowRunParameters.getDefault()
                .setParameters(parameters)
                .setReportNode(reportNode);
        LoadFlowResult loadFlowResult = Futures.await(LoadFlow.runAsync(network, runParameters));
        return new LoadFlowResultAndReport(loadFlowResult, reportNode, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS));
    }
}
