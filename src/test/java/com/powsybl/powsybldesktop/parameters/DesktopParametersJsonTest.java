/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.nad.svg.EdgeInfoEnum;
import com.powsybl.nad.svg.EdgeInfoParameters;
import com.powsybl.nad.svg.SvgParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class DesktopParametersJsonTest {

    private static DesktopParameters customParameters() {
        Properties iidmImport = new Properties();
        iidmImport.setProperty("iidm.import.xml.throw-exception-if-extension-not-found", "true");
        DesktopParameters defaults = DesktopParameters.createDefault();
        DesktopParameters parameters = new DesktopParameters(Map.of("IIDM", iidmImport), Map.of("IIDM", new Properties()),
                defaults.sld(), defaults.nad(), defaults.loadFlow(), defaults.securityAnalysis());

        parameters.sld().setSubstationLayoutChoice(DesktopSldParameters.SubstationLayoutChoice.VERTICAL)
                .setComponentLibraryChoice(DesktopSldParameters.ComponentLibraryChoice.FLAT_DESIGN)
                .setStyleChoice(DesktopSldParameters.StyleChoice.NOMINAL_VOLTAGE)
                .setVoltageLevelLayout(DesktopSldParameters.VoltageLevelLayout.POSITION_BY_CLUSTERING);
        parameters.sld().getPositionLayoutParameters().setFeederStacked(false);
        parameters.sld().getSvgParameters().setUseName(false).setPowerValuePrecision(4).setActivePowerUnit("kW");
        parameters.sld().getLayoutParameters().setCellWidth(123.5).setVoltageLevelPadding(1, 2, 3, 4);

        parameters.nad().setStyleChoice(DesktopNadParameters.StyleChoice.NOMINAL_VOLTAGE)
                .setLayoutAlgorithm(DesktopNadParameters.LayoutAlgorithm.BASIC_FORCE)
                .updateAtlas2Parameters(b -> b.withMaxSteps(42).withAttractToCenterEnabled(false))
                .updateOverlapPreventionParameters(b -> b.withRepulsionZoneRatio(0.25));
        parameters.nad().getLabelParameters().setIdDisplayed(true)
                .setEdgeInfoParameters(new EdgeInfoParameters(EdgeInfoEnum.REACTIVE_POWER, EdgeInfoEnum.EMPTY, EdgeInfoEnum.EMPTY, EdgeInfoEnum.CURRENT));
        parameters.nad().getSvgParameters().setFixedWidth(800).setSizeConstraint(SvgParameters.SizeConstraint.FIXED_SCALE)
                .setSvgPrefix("prefix");
        parameters.nad().getSvgParameters().getDiagramPadding().setLeft(12);
        parameters.nad().getLayoutParameters().setTextNodeFixedShift(5, 6).setMaxSteps(77);

        parameters.loadFlow().setDc(true).setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        OpenLoadFlowParameters.get(parameters.loadFlow()).setMaxNewtonRaphsonIterations(33);
        parameters.securityAnalysis().getIncreasedViolationsParameters().setFlowProportionalThreshold(0.3);
        parameters.securityAnalysis().getExtension(OpenSecurityAnalysisParameters.class).setThreadCount(3);
        return parameters;
    }

    @Test
    void roundTripsEverySection() {
        DesktopParameters original = customParameters();
        ObjectNode json = DesktopParametersJson.toJson(original);
        DesktopParameters read = DesktopParametersJson.fromJson(json);

        assertEquals(json, DesktopParametersJson.toJson(read));
        assertEquals("true", read.networkImport().get("IIDM").getProperty("iidm.import.xml.throw-exception-if-extension-not-found"));
        assertEquals(DesktopSldParameters.SubstationLayoutChoice.VERTICAL, read.sld().getSubstationLayoutChoice());
        assertEquals(DesktopSldParameters.ComponentLibraryChoice.FLAT_DESIGN, read.sld().getComponentLibraryChoice());
        assertEquals(DesktopSldParameters.StyleChoice.NOMINAL_VOLTAGE, read.sld().getStyleChoice());
        assertFalse(read.sld().getPositionLayoutParameters().isFeederStacked());
        assertEquals("kW", read.sld().getSvgParameters().getActivePowerUnit());
        assertEquals(123.5, read.sld().getLayoutParameters().getCellWidth());
        assertEquals(3, read.sld().getLayoutParameters().getVoltageLevelPadding().right());
        assertEquals(DesktopNadParameters.StyleChoice.NOMINAL_VOLTAGE, read.nad().getStyleChoice());
        assertEquals(42, read.nad().getAtlas2Parameters().getMaxSteps());
        assertFalse(read.nad().getAtlas2Parameters().isAttractToCenterEnabled());
        assertEquals(0.25, read.nad().getOverlapPreventionParameters().getRepulsionZoneRatio());
        assertEquals(EdgeInfoEnum.CURRENT, read.nad().getLabelParameters().getEdgeInfoParameters().infoSideInternal());
        assertEquals(800, read.nad().getSvgParameters().getFixedWidth());
        assertEquals(SvgParameters.SizeConstraint.FIXED_SCALE, read.nad().getSvgParameters().getSizeConstraint());
        assertEquals(12, read.nad().getSvgParameters().getDiagramPadding().getLeft());
        assertEquals(6, read.nad().getLayoutParameters().getTextNodeFixedShift().y());
        assertTrue(read.loadFlow().isDc());
        assertEquals(33, OpenLoadFlowParameters.get(read.loadFlow()).getMaxNewtonRaphsonIterations());
        assertEquals(0.3, read.securityAnalysis().getIncreasedViolationsParameters().getFlowProportionalThreshold());
        assertEquals(3, read.securityAnalysis().getExtension(OpenSecurityAnalysisParameters.class).getThreadCount());
    }

    @Test
    void emptyNetworkFormatsAndEmbeddedLoadFlowAreNotWritten() {
        ObjectNode json = DesktopParametersJson.toJson(customParameters());
        assertFalse(json.get("networkExport").has("IIDM"));
        assertFalse(json.get("securityAnalysis").has("load-flow-parameters"));
    }

    @Test
    void missingFieldsKeepDefaults() {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.putObject("singleLineDiagram").putObject("layout").put("cellWidth", 99.0);
        DesktopParameters read = DesktopParametersJson.fromJson(json);

        ObjectNode expected = DesktopParametersJson.toJson(DesktopParameters.createDefault());
        ((ObjectNode) expected.get("singleLineDiagram").get("layout")).put("cellWidth", 99.0);
        assertEquals(expected, DesktopParametersJson.toJson(read));
    }

    @Test
    void wronglyTypedFieldFailsTheWholeRead() {
        ObjectNode json = DesktopParametersJson.toJson(DesktopParameters.createDefault());
        ((ObjectNode) json.get("networkAreaDiagram").get("svg")).put("fixedWidth", "wide");
        assertThrows(PowsyblException.class, () -> DesktopParametersJson.fromJson(json));

        ObjectNode unknownEnum = DesktopParametersJson.toJson(DesktopParameters.createDefault());
        ((ObjectNode) unknownEnum.get("singleLineDiagram")).put("styleProvider", "UNKNOWN");
        assertThrows(PowsyblException.class, () -> DesktopParametersJson.fromJson(unknownEnum));
    }
}
