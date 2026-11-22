/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityFactorsIOTest {

    private static final SensitivityFactorMatrix LINES_TO_GENERATORS = new SensitivityFactorMatrix("lines", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("L1", "L2"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G1", "G2", "G3"), false, ContingencyContext.all());
    private static final SensitivityFactorMatrix BUS_TO_TARGET = new SensitivityFactorMatrix("voltages", SensitivityFunctionType.BUS_VOLTAGE,
            List.of("B1"), SensitivityVariableType.BUS_TARGET_VOLTAGE, List.of("G1"), false, ContingencyContext.specificContingency("C1"));
    private static final SensitivityFactorMatrix LINES_TO_SET = new SensitivityFactorMatrix("glsk", SensitivityFunctionType.BRANCH_CURRENT_2,
            List.of("L1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("FR"), true, ContingencyContext.none());

    @TempDir
    Path folder;

    private static List<String> sorted(List<SensitivityFactorMatrix> matrices) {
        return matrices.stream().flatMap(matrix -> matrix.factors().stream()).map(SensitivityFactor::toString).sorted().toList();
    }

    @Test
    void matricesRoundTrip() throws IOException {
        Path path = folder.resolve("matrices.json");
        List<SensitivityFactorMatrix> matrices = List.of(LINES_TO_GENERATORS, BUS_TO_TARGET, LINES_TO_SET);
        SensitivityFactorsIO.writeMatrices(matrices, path);
        assertEquals(matrices, SensitivityFactorsIO.readMatrices(path));
    }

    @Test
    void unsupportedMatricesVersionIsRefused() throws IOException {
        Path path = folder.resolve("matrices.json");
        Files.writeString(path, "{\"version\":\"2.0\",\"matrices\":[]}");
        assertThrows(IOException.class, () -> SensitivityFactorsIO.readMatrices(path));
    }

    @Test
    void powsyblFactorsAreWrittenExpandedAndReadBackGrouped() throws IOException {
        Path path = folder.resolve("factors.json");
        SensitivityFactorsIO.writeFactors(List.of(LINES_TO_GENERATORS, BUS_TO_TARGET), path);

        List<SensitivityFactorMatrix> read = SensitivityFactorsIO.readFactors(path, "factors");

        assertEquals(List.of("factors 1", "factors 2"), read.stream().map(SensitivityFactorMatrix::name).toList());
        assertEquals(List.of(LINES_TO_GENERATORS.functionIds(), BUS_TO_TARGET.functionIds()),
                read.stream().map(SensitivityFactorMatrix::functionIds).toList());
        assertEquals(sorted(List.of(LINES_TO_GENERATORS, BUS_TO_TARGET)), sorted(read));
    }

    @Test
    void factorsNotFormingAFullMatrixAreGroupedExactly() {
        List<SensitivityFactor> factors = List.of(
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER, "G1", false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER, "G2", false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "L2", SensitivityVariableType.INJECTION_ACTIVE_POWER, "G1", false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "L3", SensitivityVariableType.INJECTION_ACTIVE_POWER, "G1", false, ContingencyContext.all()),
                new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER, "G1", false, ContingencyContext.none()));

        List<SensitivityFactorMatrix> matrices = SensitivityFactorsIO.group(factors, "imported");

        assertEquals(List.of(List.of("L1"), List.of("L2", "L3"), List.of("L1")), matrices.stream().map(SensitivityFactorMatrix::functionIds).toList());
        assertEquals(List.of(List.of("G1", "G2"), List.of("G1"), List.of("G1")), matrices.stream().map(SensitivityFactorMatrix::variableIds).toList());
        assertEquals(factors.stream().map(SensitivityFactor::toString).sorted().toList(), sorted(matrices));
    }

    @Test
    void aSingleMatrixKeepsTheNameUnnumbered() {
        assertEquals("imported", SensitivityFactorsIO.group(LINES_TO_GENERATORS.factors(), "imported").getFirst().name());
    }

    @Test
    void variableSetsRoundTrip() throws IOException {
        Path path = folder.resolve("sets.json");
        List<SensitivityVariableSet> sets = List.of(
                new SensitivityVariableSet("FR", List.of(new WeightedSensitivityVariable("G1", 0.25), new WeightedSensitivityVariable("G2", 0.75))),
                new SensitivityVariableSet("BE", List.of(new WeightedSensitivityVariable("G3", 1))));
        SensitivityFactorsIO.writeVariableSets(sets, path);

        List<SensitivityVariableSet> read = SensitivityFactorsIO.readVariableSets(path);

        assertEquals(List.of("FR", "BE"), read.stream().map(SensitivityVariableSet::getId).toList());
        assertEquals(List.of("G1=0.25", "G2=0.75"), read.getFirst().getVariables().stream()
                .sorted(Comparator.comparing(WeightedSensitivityVariable::getId))
                .map(variable -> variable.getId() + "=" + variable.getWeight())
                .toList());
    }

    @Test
    void factorCountIsTheMatrixSize() {
        assertEquals(6, LINES_TO_GENERATORS.factorCount());
        assertEquals(6, LINES_TO_GENERATORS.factors().size());
        assertEquals("L1", LINES_TO_GENERATORS.factors().get(2).getFunctionId());
    }
}
