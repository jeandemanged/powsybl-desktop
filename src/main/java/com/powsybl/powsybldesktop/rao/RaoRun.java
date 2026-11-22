/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.io.TreeDataFormat;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.openrao.data.crac.api.RaUsageLimits;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.ComputationStatus;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.Rao;
import com.powsybl.openrao.raoapi.RaoInput;
import com.powsybl.openrao.raoapi.parameters.ObjectiveFunctionParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoRangeActionsOptimizationParameters;
import com.powsybl.openrao.searchtreerao.commons.RaoUtil;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.utils.Futures;
import com.powsybl.powsybldesktop.utils.Messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A run of OpenRAO's search tree RAO, directly or through FastRAO, which runs it on the worst CNECs only, adding CNECs
 * until the result holds for all: {@link #prepare} on the FX thread, then {@link #compute} in the background.
 * <p>
 * The RAO runs on a copy of the network and of the CRAC, never on the study's. On the network, it switches the working
 * variant (an FX thread reading the network meanwhile would see its intermediate states, and thread-local variants
 * don't help: they make its multi-threaded optimization silently fall back to the initial situation) and leaves
 * variants behind (a second run then fails: "Target variants already exist"); its result is not applied to the network
 * anyway. It changes the CRAC (network actions added for the HVDC range actions), which the user may edit meanwhile.
 * OpenRAO ignores interruptions: a cancelled run is abandoned, its thread going on with the copies until done.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RaoRun {

    public static final String SEARCH_TREE_RAO = "SearchTreeRao";
    public static final String FAST_RAO = "FastRao";

    private static final int MAX_LISTED_IDS = 5;
    // daemon threads: an abandoned run doesn't keep the application from exiting
    private static final Executor EXECUTOR = runnable -> Thread.ofPlatform().daemon().name("rao").start(runnable);

    private final Network network;
    private final Crac crac;
    private final RaoParameters parameters;
    private final boolean fastRao;
    private volatile CompletableFuture<RaoResult> computation;

    private RaoRun(Network network, Crac crac, RaoParameters parameters, boolean fastRao) {
        this.network = network;
        this.crac = crac;
        this.parameters = parameters;
        this.fastRao = fastRao;
    }

    /**
     * Prepares a RAO of {@code network} (a root network) through FastRAO or not with {@code crac}, copied here,
     * and {@code parameters}, used as they are (see {@link #copyParameters}).
     *
     * @throws IllegalStateException when the CRAC can't be copied for the network (see {@link CracIO#copy})
     */
    public static RaoRun prepare(Network network, Crac crac, RaoParameters parameters, boolean fastRao) {
        return new RaoRun(network, CracIO.copy(crac, network), parameters, fastRao);
    }

    /**
     * Why the RAO (through FastRAO or not) would fail on, or misread, {@code crac} with {@code parameters} on {@code network}: one line
     * per reason, empty when it can run. Mirrors the checks of OpenRAO's {@code RaoUtil.checkParameters} (7.5.1), for the
     * inputs the application never gives (GLSKs, reference program), FastRAO's single curative instant, and the CRAC's
     * elements missing from the network.
     */
    public static List<String> problems(Network network, Crac crac, RaoParameters parameters, boolean fastRao) {
        List<String> problems = new ArrayList<>();
        int curativeInstants = crac.getInstants(InstantKind.CURATIVE).size();
        if (fastRao && curativeInstants > 1) {
            problems.add(Messages.get("rao.problem.fastRaoCurativeInstants", curativeInstants));
        }
        if (crac.getCnecs().isEmpty()) {
            problems.add(Messages.get("rao.problem.noCnec"));
        }
        crac.getContingencies().stream().sorted(Comparator.comparing(Contingency::getId))
                .forEach(contingency -> listed(CracElements.missing(network, contingency))
                        .ifPresent(ids -> problems.add(Messages.get("rao.problem.contingencyElements", contingency.getId(), ids))));
        crac.getCnecs().stream().<Cnec<?>>map(cnec -> cnec).sorted(Comparator.comparing(Cnec::getId))
                .forEach(cnec -> listed(CracElements.missing(network, cnec))
                        .ifPresent(ids -> problems.add(Messages.get("rao.problem.cnecElements", cnec.getId(), ids))));
        crac.getRemedialActions().stream().sorted(Comparator.comparing(RemedialAction::getId))
                .forEach(action -> listed(CracElements.missing(network, action))
                        .ifPresent(ids -> problems.add(Messages.get("rao.problem.remedialActionElements", action.getId(), ids))));
        ObjectiveFunctionParameters.ObjectiveFunctionType objective = parameters.getObjectiveFunctionParameters().getType();
        if (objective.relativePositiveMargins()) {
            problems.add(Messages.get("rao.problem.relativeMargins", objective));
        }
        if (parameters.getLoopFlowParameters().isPresent()) {
            problems.add(Messages.get("rao.problem.loopFlows"));
        }
        if (objective.costOptimization() && searchTreeParameters(parameters).flatMap(OpenRaoSearchTreeParameters::getMinMarginsParameters).isEmpty()) {
            problems.add(Messages.get("rao.problem.costlyMinMargins", objective));
        }
        if (!LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(parameters).getLoadFlowParameters().isHvdcAcEmulation()) {
            listed(network.getHvdcLineStream().filter(RaoRun::inAcEmulation).map(HvdcLine::getId).sorted().toList())
                    .ifPresent(ids -> problems.add(Messages.get("rao.problem.hvdcAcEmulation", ids)));
        }
        if (SearchTreeRaoRangeActionsOptimizationParameters.getPstModel(parameters) != SearchTreeRaoRangeActionsOptimizationParameters.PstModel.APPROXIMATED_INTEGERS
                && crac.getRaUsageLimitsPerInstant().values().stream().map(RaUsageLimits::getMaxElementaryActionsPerTso).anyMatch(limits -> !limits.isEmpty())) {
            problems.add(Messages.get("rao.problem.pstModel"));
        }
        try {
            RaoUtil.checkCurativeRaUsageLimit(crac);
        } catch (OpenRaoException e) {
            problems.add(Messages.get("rao.problem.usageLimits", e.getMessage()));
        }
        return problems;
    }

    private static Optional<OpenRaoSearchTreeParameters> searchTreeParameters(RaoParameters parameters) {
        return Optional.ofNullable(parameters.getExtension(OpenRaoSearchTreeParameters.class));
    }

    private static boolean inAcEmulation(HvdcLine line) {
        HvdcAngleDroopActivePowerControl control = line.getExtension(HvdcAngleDroopActivePowerControl.class);
        return control != null && control.isEnabled();
    }

    private static Optional<String> listed(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        String shown = String.join(", ", ids.stream().limit(MAX_LISTED_IDS).toList());
        return Optional.of(ids.size() <= MAX_LISTED_IDS ? shown : Messages.get("rao.problem.more", shown, ids.size() - MAX_LISTED_IDS));
    }

    /**
     * Runs the RAO, to be called off the FX thread: interrupting the calling thread abandons it.
     *
     * @throws RaoFailedException when the RAO completes with a failed result
     */
    public RaoResultAndReport compute() throws Exception {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.rao")
                .withTimestamp()
                .build();
        CompletableFuture<RaoResult> future = CompletableFuture.supplyAsync(() -> {
            Network copy = NetworkSerDe.copy(network, NetworkFactory.findDefault(), TreeDataFormat.BIN);
            return Rao.find(fastRao ? FAST_RAO : SEARCH_TREE_RAO).run(RaoInput.build(copy, crac).build(), parameters, reportNode);
        }, EXECUTOR);
        computation = future;
        RaoResult result = Futures.await(future);
        if (result.getComputationStatus() == ComputationStatus.FAILURE) {
            throw new RaoFailedException(result.getExecutionDetails(), reportNode);
        }
        return new RaoResultAndReport(result, crac, reportNode);
    }

    /**
     * The CRAC the run uses, a copy of the one it was prepared with: the result refers to its objects.
     */
    public Crac crac() {
        return crac;
    }

    /**
     * The underlying computation once started: a cancelled run's goes on until the RAO is done.
     */
    public Optional<CompletableFuture<RaoResult>> computation() {
        return Optional.ofNullable(computation);
    }

    /**
     * A copy of {@code parameters}, so that a run doesn't see the parameters window's in-place edits made meanwhile, AC
     * or DC as {@code mode} says: the copy's load flow parameters are a copy of the embedded ones (the application's).
     */
    public static RaoParameters copyParameters(RaoParameters parameters, SensitivityAnalysisMode mode) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        RaoParametersSupport.write(Objects.requireNonNull(parameters), json);
        RaoParameters copy = RaoParametersSupport.read(new ByteArrayInputStream(json.toByteArray()));
        LoadFlowParameters loadFlowParameters = RaoParametersSupport.sensitivityParameters(parameters).getLoadFlowParameters().copy();
        loadFlowParameters.setDc(mode.isDc(loadFlowParameters));
        RaoParametersSupport.sensitivityParameters(copy).setLoadFlowParameters(loadFlowParameters);
        return copy;
    }
}
