/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.PowsyblException;
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
 * The RAO runs on a copy of the network and of the CRAC, never on the study's. {@link #prepare} only writes the CRAC's
 * JSON, on the FX thread since CRAC edits aren't held back by the network busy lock; {@link #compute} reads it back for
 * the network (the slow part: schema validation and import) and copies the network on the calling thread, the network
 * held busy, before handing both copies to the RAO's own thread. On the network, it switches the working
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
    private final byte[] cracJson;
    private final RaoParameters parameters;
    private final boolean fastRao;
    private volatile CompletableFuture<RaoResult> computation;

    private RaoRun(Network network, byte[] cracJson, RaoParameters parameters, boolean fastRao) {
        this.network = network;
        this.cracJson = cracJson;
        this.parameters = parameters;
        this.fastRao = fastRao;
    }

    /**
     * Prepares a RAO of {@code network} (a root network) through FastRAO or not with {@code crac}, whose JSON is
     * written here for {@link #compute} to copy it, and {@code parameters}, used as they are (see {@link #copyParameters}).
     */
    public static RaoRun prepare(Network network, Crac crac, RaoParameters parameters, boolean fastRao) {
        return new RaoRun(network, CracIO.toJson(crac), parameters, fastRao);
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
        CracElements.remedialActions(crac).sorted(Comparator.comparing(RemedialAction::getId))
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
     * Runs the RAO, to be called off the FX thread with the network held busy until it returns: interrupting the calling
     * thread abandons the RAO, once the copies are made.
     *
     * @throws CracNotReadableException when the CRAC no longer reads against the network (see {@link CracIO#fromJson})
     * @throws RaoFailedException when the RAO completes with a failed result
     */
    public RaoResultAndReport compute() throws Exception {
        Crac crac;
        try {
            crac = CracIO.fromJson(cracJson, network);
        } catch (IllegalStateException | IllegalArgumentException | OpenRaoException | PowsyblException e) {
            // a failed creation, or one throwing (no importer recognizing it, an exception of OpenRAO's or PowSyBl's)
            throw new CracNotReadableException(e.getMessage(), e);
        }
        // here, not on the RAO's thread: that one goes on after a cancellation, once the network is released
        Network networkCopy = NetworkSerDe.copy(network, NetworkFactory.findDefault(), TreeDataFormat.BIN);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.rao")
                .withTimestamp()
                .build();
        CompletableFuture<RaoResult> future = CompletableFuture.supplyAsync(() ->
                Rao.find(fastRao ? FAST_RAO : SEARCH_TREE_RAO).run(RaoInput.build(networkCopy, crac).build(), parameters, reportNode), EXECUTOR);
        computation = future;
        RaoResult result = Futures.await(future);
        if (result.getComputationStatus() == ComputationStatus.FAILURE) {
            throw new RaoFailedException(result.getExecutionDetails(), reportNode);
        }
        return new RaoResultAndReport(result, crac, reportNode);
    }

    /**
     * The CRAC no longer reads against the network, edited since its import: the message gives the creation report.
     */
    public static final class CracNotReadableException extends Exception {

        public CracNotReadableException(String message, Throwable cause) {
            super(message, cause);
        }
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
