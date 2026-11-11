/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.json.SensitivityJsonModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON files of sensitivity factors and variable sets, through streams rather than {@code path.toFile()}: the path can
 * be inside a study's zip file system.
 * <ul>
 *     <li>factor matrices: the app's own format, PowSyBl's (a flat list of {@link SensitivityFactor}s) not keeping
 *     how the user grouped the factors;</li>
 *     <li>PowSyBl's factors, for exchanges with other tools: written expanded, read back grouped into matrices;</li>
 *     <li>variable sets: PowSyBl's format, a list of {@link SensitivityVariableSet}s;</li>
 *     <li>results: PowSyBl's format.</li>
 * </ul>
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SensitivityFactorsIO {

    private static final String VERSION = "1.0";

    private record MatricesJson(String version, List<MatrixJson> matrices) {
    }

    private record MatrixJson(String name, SensitivityFunctionType functionType, List<String> functionIds,
                              SensitivityVariableType variableType, List<String> variableIds, boolean variableSet,
                              ContingencyContextType contingencyContextType, String contingencyId) {

        static MatrixJson of(SensitivityFactorMatrix matrix) {
            return new MatrixJson(matrix.name(), matrix.functionType(), matrix.functionIds(), matrix.variableType(), matrix.variableIds(),
                    matrix.variableSet(), matrix.contingencyContext().getContextType(), matrix.contingencyContext().getContingencyId());
        }

        SensitivityFactorMatrix toMatrix() {
            return new SensitivityFactorMatrix(name, functionType, functionIds, variableType, variableIds, variableSet,
                    ContingencyContext.create(contingencyId, contingencyContextType));
        }
    }

    private record GroupKey(SensitivityFunctionType functionType, SensitivityVariableType variableType, boolean variableSet,
                            ContingencyContext contingencyContext) {
    }

    private SensitivityFactorsIO() {
    }

    private static ObjectMapper mapper() {
        ObjectMapper mapper = JsonUtil.createObjectMapper();
        mapper.registerModule(new SensitivityJsonModule());
        return mapper;
    }

    public static List<SensitivityFactorMatrix> readMatrices(Path path) throws IOException {
        MatricesJson json;
        try (InputStream in = Files.newInputStream(path)) {
            json = mapper().readValue(in, MatricesJson.class);
        }
        if (json.version() == null || !json.version().startsWith("1.")) {
            throw new IOException("Unsupported sensitivity factors format version " + json.version() + " in " + path);
        }
        return json.matrices().stream().map(MatrixJson::toMatrix).toList();
    }

    public static void writeMatrices(List<SensitivityFactorMatrix> matrices, Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            mapper().writerWithDefaultPrettyPrinter().writeValue(out, new MatricesJson(VERSION, matrices.stream().map(MatrixJson::of).toList()));
        }
    }

    /**
     * Reads PowSyBl's factors, grouped into matrices named after {@code name} (numbered when there are several).
     */
    public static List<SensitivityFactorMatrix> readFactors(Path path, String name) throws IOException {
        List<SensitivityFactor> factors;
        try (InputStream in = Files.newInputStream(path)) {
            factors = mapper().readValue(in, new TypeReference<>() { });
        }
        return group(factors, name);
    }

    public static void writeFactors(List<SensitivityFactorMatrix> matrices, Path path) throws IOException {
        List<SensitivityFactor> factors = matrices.stream().flatMap(matrix -> matrix.factors().stream()).toList();
        try (OutputStream out = Files.newOutputStream(path)) {
            mapper().writerWithDefaultPrettyPrinter().writeValue(out, factors);
        }
    }

    public static List<SensitivityVariableSet> readVariableSets(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return mapper().readValue(in, new TypeReference<>() { });
        }
    }

    public static void writeVariableSets(List<SensitivityVariableSet> variableSets, Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            mapper().writerWithDefaultPrettyPrinter().writeValue(out, variableSets);
        }
    }

    public static SensitivityAnalysisResult readResult(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return mapper().readValue(in, SensitivityAnalysisResult.class);
        }
    }

    public static void writeResult(SensitivityAnalysisResult result, Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            mapper().writeValue(out, result);
        }
    }

    // Exact: the functions of one kind of factor having the same variables share a matrix, so that the matrices
    // expand back to the very same factors, in an order that may differ.
    static List<SensitivityFactorMatrix> group(List<SensitivityFactor> factors, String name) {
        Map<GroupKey, Map<String, List<String>>> variableIdsByFunctionIdByKey = new LinkedHashMap<>();
        for (SensitivityFactor factor : factors) {
            GroupKey key = new GroupKey(factor.getFunctionType(), factor.getVariableType(), factor.isVariableSet(), factor.getContingencyContext());
            variableIdsByFunctionIdByKey.computeIfAbsent(key, _ -> new LinkedHashMap<>())
                    .computeIfAbsent(factor.getFunctionId(), _ -> new ArrayList<>())
                    .add(factor.getVariableId());
        }
        List<SensitivityFactorMatrix> matrices = new ArrayList<>();
        variableIdsByFunctionIdByKey.forEach((key, variableIdsByFunctionId) -> {
            Map<List<String>, List<String>> functionIdsByVariableIds = new LinkedHashMap<>();
            variableIdsByFunctionId.forEach((functionId, variableIds) ->
                    functionIdsByVariableIds.computeIfAbsent(variableIds, _ -> new ArrayList<>()).add(functionId));
            functionIdsByVariableIds.forEach((variableIds, functionIds) -> matrices.add(new SensitivityFactorMatrix(name,
                    key.functionType(), functionIds, key.variableType(), variableIds, key.variableSet(), key.contingencyContext())));
        });
        if (matrices.size() == 1) {
            return matrices;
        }
        List<SensitivityFactorMatrix> numbered = new ArrayList<>(matrices.size());
        for (int i = 0; i < matrices.size(); i++) {
            SensitivityFactorMatrix matrix = matrices.get(i);
            numbered.add(new SensitivityFactorMatrix(name + " " + (i + 1), matrix.functionType(), matrix.functionIds(), matrix.variableType(),
                    matrix.variableIds(), matrix.variableSet(), matrix.contingencyContext()));
        }
        return numbered;
    }
}
