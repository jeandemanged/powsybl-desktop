/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.MnecParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.FastRaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoCostlyMinMarginParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoMnecParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

/**
 * The application's handling of OpenRAO's {@link RaoParameters}: they always carry the search tree RAO's
 * {@link OpenRaoSearchTreeParameters} extension (a plain {@code new RaoParameters()} doesn't) and FastRAO's
 * {@link FastRaoParameters}; the search tree one's sensitivity
 * parameters embed load flow parameters that the application keeps wired to its single instance. Their optional
 * sections (MNEC, costly min margins) can be added but not removed through their API: removing one goes through their
 * JSON, giving new parameters.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RaoParametersSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXTENSIONS = "extensions";
    private static final String SEARCH_TREE = "open-rao-search-tree-parameters";
    private static final String LOAD_FLOW_AND_SENSITIVITY = "load-flow-and-sensitivity-computation";
    private static final String SENSITIVITY = "sensitivity-parameters";
    private static final String LOAD_FLOW = "load-flow-parameters";
    private static final String MNEC = "mnec-parameters";
    private static final String COSTLY_MIN_MARGINS = "costly-min-margin-parameters";

    private RaoParametersSupport() {
    }

    public static RaoParameters createDefault() {
        RaoParameters parameters = new RaoParameters(ReportNode.NO_OP);
        searchTree(parameters);
        fastRao(parameters);
        return parameters;
    }

    /**
     * The search tree RAO's extension of {@code parameters}, added if missing.
     */
    public static OpenRaoSearchTreeParameters searchTree(RaoParameters parameters) {
        OpenRaoSearchTreeParameters extension = parameters.getExtension(OpenRaoSearchTreeParameters.class);
        if (extension == null) {
            extension = new OpenRaoSearchTreeParameters(ReportNode.NO_OP);
            parameters.addExtension(OpenRaoSearchTreeParameters.class, extension);
        }
        return extension;
    }

    /**
     * FastRAO's extension of {@code parameters}, added if missing: FastRAO adds it with default values otherwise, reporting
     * it as missing; the search tree RAO ignores it.
     */
    public static FastRaoParameters fastRao(RaoParameters parameters) {
        FastRaoParameters extension = parameters.getExtension(FastRaoParameters.class);
        if (extension == null) {
            extension = new FastRaoParameters();
            parameters.addExtension(FastRaoParameters.class, extension);
        }
        return extension;
    }

    /**
     * The sensitivity parameters the RAO computes with, embedding its load flow parameters.
     */
    public static SensitivityAnalysisParameters sensitivityParameters(RaoParameters parameters) {
        return searchTree(parameters).getLoadFlowAndSensitivityParameters().getSensitivityWithLoadFlowParameters();
    }

    /**
     * Whether the MNEC constraints are on: OpenRAO then keeps the margins of the monitored (not optimized) CNECs from
     * decreasing by more than the acceptable margin decrease.
     */
    public static boolean hasMnec(RaoParameters parameters) {
        return parameters.getMnecParameters().isPresent();
    }

    /**
     * Turns the MNEC constraints on, with default values for the missing parts.
     */
    public static void addMnec(RaoParameters parameters) {
        if (parameters.getMnecParameters().isEmpty()) {
            parameters.setMnecParameters(new MnecParameters());
        }
        if (searchTree(parameters).getMnecParameters().isEmpty()) {
            searchTree(parameters).setMnecParameters(new SearchTreeRaoMnecParameters());
        }
    }

    /**
     * A copy of {@code parameters} without MNEC constraints.
     */
    public static RaoParameters withoutMnec(RaoParameters parameters) {
        return edited(parameters, root -> {
            root.remove(MNEC);
            searchTreeNode(root).remove(MNEC);
        });
    }

    /**
     * Whether the costly min margin parameters are set, which the {@code MIN_COST} objective function needs.
     */
    public static boolean hasCostlyMinMargins(RaoParameters parameters) {
        return searchTree(parameters).getMinMarginsParameters().isPresent();
    }

    public static void addCostlyMinMargins(RaoParameters parameters) {
        if (!hasCostlyMinMargins(parameters)) {
            searchTree(parameters).setMinMarginsParameters(new SearchTreeRaoCostlyMinMarginParameters());
        }
    }

    public static RaoParameters withoutCostlyMinMargins(RaoParameters parameters) {
        return edited(parameters, root -> searchTreeNode(root).remove(COSTLY_MIN_MARGINS));
    }

    public static void write(RaoParameters parameters, OutputStream outputStream) {
        JsonRaoParameters.write(parameters, outputStream, ReportNode.NO_OP);
    }

    /**
     * Reads parameters written in OpenRAO's JSON format, adding the search tree RAO's and FastRAO's extensions if missing.
     */
    public static RaoParameters read(InputStream inputStream) {
        RaoParameters parameters = JsonRaoParameters.read(inputStream, ReportNode.NO_OP);
        searchTree(parameters);
        fastRao(parameters);
        return parameters;
    }

    /**
     * {@code parameters} in OpenRAO's JSON format, without the load flow parameters its sensitivity parameters embed.
     */
    public static ObjectNode toJsonWithoutLoadFlow(RaoParameters parameters) {
        ObjectNode root = toJson(parameters);
        JsonNode sensitivity = searchTreeNode(root).path(LOAD_FLOW_AND_SENSITIVITY).path(SENSITIVITY);
        if (sensitivity instanceof ObjectNode sensitivityNode) {
            sensitivityNode.remove(LOAD_FLOW);
        }
        return root;
    }

    /**
     * Reads parameters from {@code node}, in OpenRAO's JSON format (embedded load flow parameters missing or not).
     */
    public static RaoParameters fromJson(JsonNode node) {
        try {
            return read(new ByteArrayInputStream(MAPPER.writeValueAsBytes(node)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode toJson(RaoParameters parameters) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        write(parameters, json);
        try {
            return (ObjectNode) MAPPER.readTree(json.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectNode searchTreeNode(ObjectNode root) {
        return (ObjectNode) root.path(EXTENSIONS).path(SEARCH_TREE);
    }

    // the load flow parameters, which the JSON may not render exactly (extensions without a serializer), are kept
    private static RaoParameters edited(RaoParameters parameters, Consumer<ObjectNode> edit) {
        ObjectNode root = toJson(parameters);
        edit.accept(root);
        RaoParameters edited = fromJson(root);
        sensitivityParameters(edited).setLoadFlowParameters(sensitivityParameters(parameters).getLoadFlowParameters());
        return edited;
    }
}
