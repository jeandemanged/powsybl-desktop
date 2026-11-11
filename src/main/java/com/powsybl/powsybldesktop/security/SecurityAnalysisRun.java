/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.security;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.BoundaryLineFilter;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.limitscaling.OlfLimitScalingSupport;
import com.powsybl.powsybldesktop.utils.Futures;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisReport;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.json.JsonSecurityAnalysisParameters;
import com.powsybl.security.limitscaling.LimitScaling;
import com.powsybl.security.monitor.StateMonitor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A security analysis run: {@link #prepare} on the FX thread, {@link #compute} in the background, then
 * {@link #restoreVariantAccess} on the FX thread once the computation is over.
 * <p>
 * OpenLoadFlow's multi-threaded security analysis switches the network to thread-local variants while running, so that
 * any other thread reading it meanwhile fails ("Variant index not set for current thread"), e.g. the FX thread opening a
 * table. So the switch is made by {@link #prepare}, giving the FX thread (and the computing one) its working variant,
 * and undone by {@link #restoreVariantAccess}: OpenLoadFlow then finds it on and leaves it on. The FX thread may still
 * read the network while OpenLoadFlow's threads build their own networks from it, racing on IIDM's lazily computed
 * caches (e.g. the bus view): running the security analysis on a copy of the network would isolate it fully, at the
 * cost of a copy per run.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SecurityAnalysisRun {

    private final Network network;
    private final ContingencyList contingencyList;
    private final SecurityAnalysisParameters parameters;
    private final List<LimitScaling> limitScalings;
    private final String variantId;
    private final boolean variantMultiThreadAccessAllowed;
    private volatile CompletableFuture<SecurityAnalysisReport> computation;

    private SecurityAnalysisRun(Network network, ContingencyList contingencyList, SecurityAnalysisParameters parameters,
                                List<LimitScaling> limitScalings) {
        this.network = network;
        this.contingencyList = contingencyList;
        this.parameters = parameters;
        this.limitScalings = List.copyOf(limitScalings);
        VariantManager variantManager = network.getVariantManager();
        variantId = variantManager.getWorkingVariantId();
        variantMultiThreadAccessAllowed = variantManager.isVariantMultiThreadAccessAllowed();
    }

    /**
     * Prepares a security analysis of {@code network} (a root network) against {@code contingencyList} with
     * {@code limitScalings}, allowing multi-thread access to its variants until {@link #restoreVariantAccess}.
     */
    public static SecurityAnalysisRun prepare(Network network, ContingencyList contingencyList, SecurityAnalysisParameters parameters,
                                              List<LimitScaling> limitScalings) {
        SecurityAnalysisRun run = new SecurityAnalysisRun(network, contingencyList, parameters, limitScalings);
        network.getVariantManager().allowVariantMultiThreadAccess(true);
        network.getVariantManager().setWorkingVariant(run.variantId);
        return run;
    }

    /**
     * Runs the security analysis, to be called off the FX thread; interrupting the calling thread cancels it.
     */
    public SecurityAnalysisResultAndReport compute() throws Exception {
        network.getVariantManager().setWorkingVariant(variantId);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.securityAnalysis")
                .withTimestamp()
                .build();
        reportLimitScalingSupport(reportNode);
        List<Contingency> contingencies = ContingencyNames.deduplicate(contingencyList.getContingencies(network));
        SecurityAnalysisRunParameters runParameters = SecurityAnalysisRunParameters.getDefault()
                .setSecurityAnalysisParameters(parameters)
                .addMonitor(preContingencyMonitor(network))
                .setLimitScalings(limitScalings)
                .setReportNode(reportNode);
        CompletableFuture<SecurityAnalysisReport> future = SecurityAnalysis.runAsync(network, contingencies, runParameters);
        computation = future;
        return new SecurityAnalysisResultAndReport(Futures.await(future).getResult(), reportNode);
    }

    // Open Load Flow only logs the scalings it skips, and says nothing of the criteria it ignores
    private void reportLimitScalingSupport(ReportNode reportNode) {
        for (int i = 0; i < limitScalings.size(); i++) {
            OlfLimitScalingSupport.Support support = OlfLimitScalingSupport.of(limitScalings.get(i));
            if (support.status() != OlfLimitScalingSupport.Status.APPLIED) {
                reportNode.newReportNode()
                        .withMessageTemplate(support.status() == OlfLimitScalingSupport.Status.PARTIALLY_APPLIED
                                ? "desktop.securityAnalysis.limitScalingPartiallyApplied" : "desktop.securityAnalysis.limitScalingNotApplied")
                        .withUntypedValue("index", i + 1)
                        .withUntypedValue("reasons", String.join(" ", support.reasons()))
                        .withSeverity(TypedValue.WARN_SEVERITY)
                        .add();
            }
        }
    }

    /**
     * The limit scalings the run applies, Open Load Flow skipping some (see {@link OlfLimitScalingSupport}).
     */
    public List<LimitScaling> limitScalings() {
        return limitScalings;
    }

    /**
     * The underlying computation once started: a cancelled run's may keep running until OpenLoadFlow notices.
     */
    public Optional<CompletableFuture<SecurityAnalysisReport>> computation() {
        return Optional.ofNullable(computation);
    }

    public void restoreVariantAccess() {
        network.getVariantManager().allowVariantMultiThreadAccess(variantMultiThreadAccessAllowed);
    }

    /**
     * A copy of {@code parameters}, so that a run doesn't see the parameters window's in-place edits made meanwhile.
     */
    public static SecurityAnalysisParameters copyParameters(SecurityAnalysisParameters parameters) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonSecurityAnalysisParameters.write(parameters, out);
        SecurityAnalysisParameters copy = JsonSecurityAnalysisParameters.read(new ByteArrayInputStream(out.toByteArray()));
        copy.setLoadFlowParameters(parameters.getLoadFlowParameters().copy());
        return copy;
    }

    /**
     * Monitors the pre-contingency flows and voltages of all equipment, shown along the post-contingency violations:
     * a single state, unlike monitoring the post-contingency ones.
     */
    public static StateMonitor preContingencyMonitor(Network network) {
        Set<String> branchIds = Stream.concat(network.getBranchStream(), network.getBoundaryLineStream(BoundaryLineFilter.UNPAIRED))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        return new StateMonitor(ContingencyContext.none(), branchIds,
                network.getVoltageLevelStream().map(Identifiable::getId).collect(Collectors.toSet()),
                network.getThreeWindingsTransformerStream().map(Identifiable::getId).collect(Collectors.toSet()));
    }
}
