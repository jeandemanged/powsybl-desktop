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
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.logs.LogsModel.LogEntry;
import com.powsybl.powsybldesktop.parameters.ApplicationParameters;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.powsybldesktop.rao.CracIO;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.limitscaling.LimitScaling;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityVariableSet;

import java.util.List;
import java.util.Set;

/**
 * What a study file holds: the app's state, or the part of it the user chose to save. A part not saved is null
 * (parameters, reports, logs, results) or empty (contingency lists, limit scalings, sensitivity factors).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record Study(ApplicationParameters parameters, List<StudyNetwork> networks, List<ReportNode> reports, List<LogEntry> logs, List<String> warnings) {

    /**
     * @param securityAnalysisLimitScalings the limit scalings the security analysis result was computed with
     * @param sensitivityAnalysisMatrices the matrices the sensitivity analysis result was computed with
     * @param sensitivityAnalysisDc whether the sensitivity analysis result was computed in DC
     * @param crac the CRAC, null when none
     * @param raoResult the RAO result, null when none
     */
    public record StudyNetwork(Network network, boolean selected, List<ContingencyList> contingencyLists, List<Boolean> contingencyListsEnabled,
                               List<LimitScaling> limitScalings, List<Boolean> limitScalingsEnabled,
                               List<SensitivityFactorMatrix> sensitivityFactorMatrices, List<SensitivityVariableSet> sensitivityVariableSets,
                               LoadFlowResult loadFlowResult, SecurityAnalysisResult securityAnalysisResult,
                               List<LimitScaling> securityAnalysisLimitScalings, SensitivityAnalysisResult sensitivityAnalysisResult,
                               List<SensitivityFactorMatrix> sensitivityAnalysisMatrices, boolean sensitivityAnalysisDc,
                               StudyCrac crac, StudyRaoResult raoResult) {
    }

    /**
     * A network's CRAC and the report of its creation. Captured as OpenRAO's JSON ({@code crac} null), the study's CRAC
     * being edited in place; read as a CRAC against the network ({@code json} as read).
     */
    public record StudyCrac(byte[] json, Crac crac, List<String> creationReport) {
    }

    /**
     * A RAO result and the CRAC it refers to (the run's copy, never edited), whether it was computed with another CRAC than
     * the study's (so outdated) and in DC.
     */
    public record StudyRaoResult(RaoResult result, Crac crac, boolean outdated, boolean dc) {
    }

    /**
     * The kinds of data a study can hold besides the networks themselves.
     */
    public enum Part {
        PARAMETERS,
        CONTINGENCIES,
        LIMIT_SCALINGS,
        // matrices and variable sets
        SENSITIVITY_FACTORS,
        // the CRAC and its creation report
        CRAC,
        LOAD_FLOW_RESULTS,
        SECURITY_ANALYSIS_RESULTS,
        SENSITIVITY_ANALYSIS_RESULTS,
        RAO_RESULTS,
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
        List<StudyNetwork> studyNetworks = networks.stream().map(network -> capture(mainModel, network, parts)).toList();
        return new Study(parameters, studyNetworks,
                parts.contains(Part.REPORTS) ? List.copyOf(mainModel.getReports()) : null,
                parts.contains(Part.LOGS) ? List.copyOf(mainModel.getLogsModel().getLogs()) : null, List.of());
    }

    private static StudyNetwork capture(MainModel mainModel, Network network, Set<Part> parts) {
        NetworkStudy study = mainModel.getStudy(network);
        List<ContingencyList> lists = parts.contains(Part.CONTINGENCIES) ? List.copyOf(study.getContingencyLists()) : List.of();
        List<LimitScaling> scalings = parts.contains(Part.LIMIT_SCALINGS) ? List.copyOf(study.getLimitScalings()) : List.of();
        boolean securityAnalysis = parts.contains(Part.SECURITY_ANALYSIS_RESULTS) && study.getSecurityAnalysisResult() != null;
        boolean sensitivityFactors = parts.contains(Part.SENSITIVITY_FACTORS);
        boolean sensitivityAnalysis = parts.contains(Part.SENSITIVITY_ANALYSIS_RESULTS) && study.getSensitivityAnalysisResult() != null;
        StudyCrac crac = parts.contains(Part.CRAC) && study.getCrac() != null
                ? new StudyCrac(CracIO.toJson(study.getCrac()), null, study.getCracCreationReport()) : null;
        // up to date only with the CRAC it was computed with saved too
        StudyRaoResult raoResult = parts.contains(Part.RAO_RESULTS) && study.getRaoResult() != null
                ? new StudyRaoResult(study.getRaoResult(), study.getRaoCrac(), crac == null || study.isRaoResultOutdated(), study.isRaoDc()) : null;
        return new StudyNetwork(network, network == mainModel.getNetwork(), lists,
                lists.stream().map(list -> study.contingencyListEnabledProperty(list).get()).toList(),
                scalings, scalings.stream().map(scaling -> study.limitScalingEnabledProperty(scaling).get()).toList(),
                sensitivityFactors ? List.copyOf(study.getSensitivityFactorMatrices()) : List.of(),
                sensitivityFactors ? List.copyOf(study.getSensitivityVariableSets()) : List.of(),
                parts.contains(Part.LOAD_FLOW_RESULTS) ? study.getLoadFlowResult() : null,
                securityAnalysis ? study.getSecurityAnalysisResult() : null,
                securityAnalysis ? study.getSecurityAnalysisLimitScalings() : List.of(),
                sensitivityAnalysis ? study.getSensitivityAnalysisResult() : null,
                sensitivityAnalysis ? study.getSensitivityAnalysisMatrices() : List.of(),
                sensitivityAnalysis && study.isSensitivityAnalysisDc(), crac, raoResult);
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
            study.getLimitScalings().setAll(studyNetwork.limitScalings());
            for (int i = 0; i < studyNetwork.limitScalings().size(); i++) {
                study.limitScalingEnabledProperty(studyNetwork.limitScalings().get(i)).set(studyNetwork.limitScalingsEnabled().get(i));
            }
            study.getSensitivityFactorMatrices().setAll(studyNetwork.sensitivityFactorMatrices());
            study.getSensitivityVariableSets().setAll(studyNetwork.sensitivityVariableSets());
            if (studyNetwork.loadFlowResult() != null) {
                study.setLoadFlowResult(studyNetwork.loadFlowResult());
            }
            if (studyNetwork.securityAnalysisResult() != null) {
                study.setSecurityAnalysisLimitScalings(studyNetwork.securityAnalysisLimitScalings());
                study.setSecurityAnalysisResult(studyNetwork.securityAnalysisResult());
            }
            if (studyNetwork.sensitivityAnalysisResult() != null) {
                study.setSensitivityAnalysisResult(studyNetwork.sensitivityAnalysisResult(), studyNetwork.sensitivityAnalysisMatrices(),
                        studyNetwork.sensitivityAnalysisDc());
            }
            restoreRao(study, studyNetwork);
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

    // the CRAC, then the RAO result: up to date with the CRAC restored, or else outdated (its own CRAC, never the study's)
    private static void restoreRao(NetworkStudy study, StudyNetwork studyNetwork) {
        if (studyNetwork.crac() != null && studyNetwork.crac().crac() != null) {
            study.setCrac(studyNetwork.crac().crac(), studyNetwork.crac().creationReport());
        }
        StudyRaoResult rao = studyNetwork.raoResult();
        if (rao != null && rao.result() != null && rao.crac() != null) {
            boolean upToDate = !rao.outdated() && study.getCrac() != null;
            study.setRaoResult(rao.result(), rao.crac(), upToDate ? study.getCrac() : rao.crac(), upToDate ? study.cracRevisionProperty().get() : -1, rao.dc());
        }
    }
}
