/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.raoapi.parameters.ObjectiveFunctionParameters.ObjectiveFunctionType;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.FastRaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RaoParametersSupportTest {

    @Test
    void parametersAlwaysHaveTheSearchTreeExtension() {
        assertNotNull(RaoParametersSupport.createDefault().getExtension(OpenRaoSearchTreeParameters.class));
        assertNotNull(RaoParametersSupport.createDefault().getExtension(FastRaoParameters.class));

        RaoParameters plain = new RaoParameters(ReportNode.NO_OP);
        OpenRaoSearchTreeParameters added = RaoParametersSupport.searchTree(plain);
        assertSame(added, plain.getExtension(OpenRaoSearchTreeParameters.class));
        assertSame(added, RaoParametersSupport.searchTree(plain));
        assertSame(added.getLoadFlowAndSensitivityParameters().getSensitivityWithLoadFlowParameters(), RaoParametersSupport.sensitivityParameters(plain));
        FastRaoParameters fastRao = RaoParametersSupport.fastRao(plain);
        assertSame(fastRao, plain.getExtension(FastRaoParameters.class));
        assertSame(fastRao, RaoParametersSupport.fastRao(plain));
    }

    @Test
    void optionalSectionsAreAddedInPlaceAndRemovedInACopy() {
        RaoParameters parameters = RaoParametersSupport.createDefault();
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        RaoParametersSupport.sensitivityParameters(parameters).setLoadFlowParameters(loadFlowParameters);
        parameters.getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MIN_COST);

        RaoParametersSupport.addMnec(parameters);
        RaoParametersSupport.addCostlyMinMargins(parameters);
        assertTrue(RaoParametersSupport.hasMnec(parameters));
        assertTrue(RaoParametersSupport.searchTree(parameters).getMnecParameters().isPresent());
        assertTrue(RaoParametersSupport.hasCostlyMinMargins(parameters));

        RaoParameters withoutMnec = RaoParametersSupport.withoutMnec(parameters);
        assertNotSame(parameters, withoutMnec);
        assertFalse(RaoParametersSupport.hasMnec(withoutMnec));
        assertFalse(RaoParametersSupport.searchTree(withoutMnec).getMnecParameters().isPresent());
        assertTrue(RaoParametersSupport.hasCostlyMinMargins(withoutMnec));
        assertEquals(ObjectiveFunctionType.MIN_COST, withoutMnec.getObjectiveFunctionParameters().getType());
        assertSame(loadFlowParameters, RaoParametersSupport.sensitivityParameters(withoutMnec).getLoadFlowParameters());
        assertTrue(RaoParametersSupport.hasMnec(parameters));

        RaoParameters withoutCostlyMinMargins = RaoParametersSupport.withoutCostlyMinMargins(parameters);
        assertFalse(RaoParametersSupport.hasCostlyMinMargins(withoutCostlyMinMargins));
        assertTrue(RaoParametersSupport.hasMnec(withoutCostlyMinMargins));
    }

    @Test
    void jsonWithoutTheEmbeddedLoadFlowParameters() {
        RaoParameters parameters = RaoParametersSupport.createDefault();
        RaoParametersSupport.sensitivityParameters(parameters).getLoadFlowParameters().setDc(true);
        RaoParametersSupport.searchTree(parameters).getMultithreadingParameters().setAvailableCPUs(5);
        RaoParametersSupport.fastRao(parameters).setMarginLimit(12.);

        ObjectNode json = RaoParametersSupport.toJsonWithoutLoadFlow(parameters);
        RaoParameters read = RaoParametersSupport.fromJson(json);

        assertTrue(json.at("/extensions/open-rao-search-tree-parameters/load-flow-and-sensitivity-computation/sensitivity-parameters/load-flow-parameters").isMissingNode());
        assertEquals(5, RaoParametersSupport.searchTree(read).getMultithreadingParameters().getAvailableCPUs());
        assertEquals(12., RaoParametersSupport.fastRao(read).getMarginLimit());
        assertFalse(RaoParametersSupport.sensitivityParameters(read).getLoadFlowParameters().isDc());
    }
}
