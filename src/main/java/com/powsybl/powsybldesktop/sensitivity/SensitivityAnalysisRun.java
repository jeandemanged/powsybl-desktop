/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.network.VariantMultiThreadAccess;
import com.powsybl.powsybldesktop.utils.Futures;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A sensitivity analysis run: {@link #prepare} on the FX thread, {@link #compute} in the background, then
 * {@link #restoreVariantAccess} on the FX thread once the computation is over, multi-thread access to the network's
 * variants being allowed in between (see {@link VariantMultiThreadAccess}): Open Load Flow's AC sensitivity analysis
 * runs on several threads when its thread count is above 1.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SensitivityAnalysisRun {

    private final Network network;
    private final List<SensitivityFactorMatrix> matrices;
    private final List<SensitivityVariableSet> variableSets;
    private final ContingencyList contingencyList;
    private final SensitivityAnalysisParameters parameters;
    private final VariantMultiThreadAccess variantAccess;
    private volatile CompletableFuture<SensitivityAnalysisResult> computation;

    private SensitivityAnalysisRun(Network network, List<SensitivityFactorMatrix> matrices, List<SensitivityVariableSet> variableSets,
                                   ContingencyList contingencyList, SensitivityAnalysisParameters parameters) {
        this.network = network;
        this.matrices = List.copyOf(matrices);
        this.variableSets = List.copyOf(variableSets);
        this.contingencyList = contingencyList;
        this.parameters = parameters;
        variantAccess = VariantMultiThreadAccess.allow(network);
    }

    /**
     * Prepares a sensitivity analysis of {@code network} (a root network) computing the factors of {@code matrices}
     * against {@code contingencyList}, allowing multi-thread access to its variants until {@link #restoreVariantAccess}.
     */
    public static SensitivityAnalysisRun prepare(Network network, List<SensitivityFactorMatrix> matrices, List<SensitivityVariableSet> variableSets,
                                                 ContingencyList contingencyList, SensitivityAnalysisParameters parameters) {
        return new SensitivityAnalysisRun(network, matrices, variableSets, contingencyList, parameters);
    }

    /**
     * Runs the sensitivity analysis, to be called off the FX thread; interrupting the calling thread cancels it.
     */
    public SensitivityAnalysisResultAndReport compute() throws Exception {
        variantAccess.setWorkingVariant();
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.sensitivityAnalysis")
                .withTimestamp()
                .build();
        List<Contingency> contingencies = ContingencyNames.deduplicate(contingencyList.getContingencies(network));
        List<SensitivityFactor> factors = matrices.stream().flatMap(matrix -> matrix.factors().stream()).toList();
        SensitivityAnalysisRunParameters runParameters = SensitivityAnalysisRunParameters.getDefault()
                .setParameters(parameters)
                .setContingencies(contingencies)
                .setVariableSets(variableSets)
                .setReportNode(reportNode);
        CompletableFuture<SensitivityAnalysisResult> future = SensitivityAnalysis.runAsync(network, network.getVariantManager().getWorkingVariantId(),
                factors, runParameters);
        computation = future;
        return new SensitivityAnalysisResultAndReport(Futures.await(future), reportNode);
    }

    /**
     * The variable sets the run uses, which its result doesn't hold.
     */
    public List<SensitivityVariableSet> variableSets() {
        return variableSets;
    }

    /**
     * The underlying computation once started: a cancelled run's may keep running until Open Load Flow notices.
     */
    public Optional<CompletableFuture<SensitivityAnalysisResult>> computation() {
        return Optional.ofNullable(computation);
    }

    public void restoreVariantAccess() {
        variantAccess.restore();
    }

    /**
     * A copy of {@code parameters}, so that a run doesn't see the parameters window's in-place edits made meanwhile,
     * AC or DC as {@code mode} says.
     */
    public static SensitivityAnalysisParameters copyParameters(SensitivityAnalysisParameters parameters, SensitivityAnalysisMode mode) {
        byte[] json;
        try {
            json = JsonSensitivityAnalysisParameters.createObjectMapper().writeValueAsBytes(parameters);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SensitivityAnalysisParameters copy = JsonSensitivityAnalysisParameters.read(new ByteArrayInputStream(json));
        copy.setLoadFlowParameters(parameters.getLoadFlowParameters().copy());
        copy.getLoadFlowParameters().setDc(mode.isDc(parameters.getLoadFlowParameters()));
        return copy;
    }
}
