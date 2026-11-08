/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.study;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.logs.LogsModel.LogEntry;
import com.powsybl.powsybldesktop.parameters.ApplicationParameters;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.security.SecurityAnalysisResult;

import java.util.List;
import java.util.Set;

/**
 * What a study file holds: the app's state, or the part of it the user chose to save. A part not saved is null
 * (parameters, reports, logs, results) or empty (contingency lists).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record Study(ApplicationParameters parameters, List<StudyNetwork> networks, List<ReportNode> reports, List<LogEntry> logs) {

    public record StudyNetwork(Network network, boolean selected, List<ContingencyList> contingencyLists, List<Boolean> contingencyListsEnabled,
                               LoadFlowResult loadFlowResult, SecurityAnalysisResult securityAnalysisResult) {
    }

    /**
     * The kinds of data a study can hold besides the networks themselves.
     */
    public enum Part {
        PARAMETERS,
        CONTINGENCIES,
        LOAD_FLOW_RESULTS,
        SECURITY_ANALYSIS_RESULTS,
        REPORTS,
        LOGS
    }

    /**
     * Snapshot of {@code networks} (root networks of {@code mainModel}) and of the chosen parts, taken on the FX thread.
     * The networks themselves are not copied: they must not be edited until the study is written.
     */
    public static Study capture(MainModel mainModel, List<Network> networks, Set<Part> parts) {
        // parameters are edited in place by the parameters view: a copy, through their JSON form
        ApplicationParameters parameters = parts.contains(Part.PARAMETERS)
                ? ApplicationParametersJson.fromJson(ApplicationParametersJson.toJson(mainModel.getParametersModel().getParameters()))
                : null;
        List<StudyNetwork> studyNetworks = networks.stream().map(network -> {
            NetworkStudy study = mainModel.getStudy(network);
            List<ContingencyList> lists = parts.contains(Part.CONTINGENCIES) ? List.copyOf(study.getContingencyLists()) : List.of();
            return new StudyNetwork(network, network == mainModel.getNetwork(), lists,
                    lists.stream().map(list -> study.contingencyListEnabledProperty(list).get()).toList(),
                    parts.contains(Part.LOAD_FLOW_RESULTS) ? study.getLoadFlowResult() : null,
                    parts.contains(Part.SECURITY_ANALYSIS_RESULTS) ? study.getSecurityAnalysisResult() : null);
        }).toList();
        return new Study(parameters, studyNetworks,
                parts.contains(Part.REPORTS) ? List.copyOf(mainModel.getReports()) : null,
                parts.contains(Part.LOGS) ? List.copyOf(mainModel.getLogsModel().getLogs()) : null);
    }

    /**
     * Replaces the loaded networks by the study's, and the parameters, reports and logs by the study's when it holds
     * them. FX thread only.
     */
    public void restore(MainModel mainModel) {
        mainModel.removeAllNetworks();
        if (parameters != null) {
            mainModel.getParametersModel().setParameters(parameters);
        }
        for (StudyNetwork studyNetwork : networks) {
            Network network = studyNetwork.network();
            mainModel.addNetwork(network);
            NetworkStudy study = mainModel.getStudy(network);
            study.getContingencyLists().setAll(studyNetwork.contingencyLists());
            for (int i = 0; i < studyNetwork.contingencyLists().size(); i++) {
                study.contingencyListEnabledProperty(studyNetwork.contingencyLists().get(i)).set(studyNetwork.contingencyListsEnabled().get(i));
            }
            if (studyNetwork.loadFlowResult() != null) {
                study.setLoadFlowResult(studyNetwork.loadFlowResult());
            }
            if (studyNetwork.securityAnalysisResult() != null) {
                study.setSecurityAnalysisResult(studyNetwork.securityAnalysisResult());
            }
        }
        networks.stream().filter(StudyNetwork::selected).findFirst().ifPresent(selected -> mainModel.setNetwork(selected.network()));
        if (reports != null) {
            mainModel.clearReports();
            reports.forEach(mainModel::addReport);
        }
        if (logs != null) {
            mainModel.getLogsModel().restoreLogs(logs);
        }
        mainModel.setUpdate();
    }
}
