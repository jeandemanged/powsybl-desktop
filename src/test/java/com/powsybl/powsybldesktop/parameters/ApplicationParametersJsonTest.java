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
import com.powsybl.powsybldesktop.map.MapController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ApplicationParametersJsonTest {

    private static ApplicationParameters customParameters() {
        Properties iidmImport = new Properties();
        iidmImport.setProperty("iidm.import.xml.throw-exception-if-extension-not-found", "true");
        ApplicationParameters defaults = ApplicationParameters.createDefault();
        ApplicationParameters parameters = new ApplicationParameters(Map.of("IIDM", iidmImport), Map.of("IIDM", new Properties()),
                defaults.sld(), defaults.nad(), defaults.loadFlow(), defaults.securityAnalysis(), defaults.gui());

        parameters.sld().setSubstationLayoutChoice(DesktopSldParameters.SubstationLayoutChoice.VERTICAL)
                .setComponentLibraryChoice(DesktopSldParameters.ComponentLibraryChoice.FLAT_DESIGN)
                .setStyleChoice(DesktopSldParameters.StyleChoice.NOMINAL_VOLTAGE)
                .setVoltageLevelLayout(DesktopSldParameters.VoltageLevelLayout.POSITION_BY_CLUSTERING);
        parameters.sld().getPositionLayoutParameters().setFeederStacked(false);
        parameters.sld().getSvgParameters().setUseName(false).setActivePowerUnit("kW");
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

        parameters.gui().setMapBasemap(MapController.Basemap.OPEN_STREET_MAP);
        parameters.gui().setMapHiddenBaseVoltages(Set.of("vl300to500", "vl0to30"));
        parameters.gui().setLogsMinLevel(GuiParameters.LogLevel.WARN);
        parameters.gui().setLogsNewestOnTop(false);
        parameters.gui().setReportsMinSeverity("DETAIL");
        parameters.gui().setDecimals(GuiParameters.Quantity.RHO, 4);
        parameters.gui().setDecimals(GuiParameters.Quantity.POWER, 0);
        return parameters;
    }

    @Test
    void roundTripsEverySection() {
        ApplicationParameters original = customParameters();
        ObjectNode json = ApplicationParametersJson.toJson(original);
        ApplicationParameters read = ApplicationParametersJson.fromJson(json);

        assertEquals(json, ApplicationParametersJson.toJson(read));
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
        assertEquals(MapController.Basemap.OPEN_STREET_MAP, read.gui().getMapBasemap());
        assertEquals(Set.of("vl300to500", "vl0to30"), read.gui().getMapHiddenBaseVoltages());
        assertEquals(GuiParameters.LogLevel.WARN, read.gui().getLogsMinLevel());
        assertFalse(read.gui().isLogsNewestOnTop());
        assertEquals("DETAIL", read.gui().getReportsMinSeverity());
        assertEquals(4, read.gui().getDecimals(GuiParameters.Quantity.RHO));
        assertEquals(0, read.gui().getDecimals(GuiParameters.Quantity.POWER));
        assertEquals(4, json.get("gui").get("decimals").get("rho").intValue());
        assertEquals(0, json.get("gui").get("decimals").get("power").intValue());
        // the GUI parameters' decimals are the only ones written
        assertFalse(json.get("singleLineDiagram").get("svg").has("powerValuePrecision"));
        assertFalse(json.get("networkAreaDiagram").get("svg").has("powerValuePrecision"));
    }

    @Test
    void emptyNetworkFormatsAndEmbeddedLoadFlowAreNotWritten() {
        ObjectNode json = ApplicationParametersJson.toJson(customParameters());
        assertFalse(json.get("networkExport").has("IIDM"));
        assertFalse(json.get("securityAnalysis").has("load-flow-parameters"));
    }

    @Test
    void missingFieldsKeepDefaults() {
        ObjectNode json = new ObjectMapper().createObjectNode();
        json.putObject("singleLineDiagram").putObject("layout").put("cellWidth", 99.0);
        ApplicationParameters read = ApplicationParametersJson.fromJson(json);

        ObjectNode expected = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) expected.get("singleLineDiagram").get("layout")).put("cellWidth", 99.0);
        assertEquals(expected, ApplicationParametersJson.toJson(read));
    }

    @Test
    void wronglyTypedFieldFailsTheWholeRead() {
        ObjectNode json = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) json.get("networkAreaDiagram").get("svg")).put("fixedWidth", "wide");
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.fromJson(json));

        ObjectNode unknownEnum = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) unknownEnum.get("singleLineDiagram")).put("styleProvider", "UNKNOWN");
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.fromJson(unknownEnum));

        ObjectNode hiddenBaseVoltagesNotArray = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) hiddenBaseVoltagesNotArray.get("gui").get("map")).put("hiddenBaseVoltages", "vl300to500");
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.fromJson(hiddenBaseVoltagesNotArray));
    }

    @Test
    void invalidGuiValueFailsTheRead(@TempDir Path tempDir) {
        ObjectNode unknownSeverity = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) unknownSeverity.get("gui").get("reports")).put("minSeverity", "FATAL");
        Path unknownSeverityFile = tempDir.resolve("unknownSeverity.json");
        ApplicationParametersJson.write(unknownSeverity, unknownSeverityFile);
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.read(unknownSeverityFile));

        ObjectNode nullBasemap = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) nullBasemap.get("gui").get("map")).putNull("basemap");
        Path nullBasemapFile = tempDir.resolve("nullBasemap.json");
        ApplicationParametersJson.write(nullBasemap, nullBasemapFile);
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.read(nullBasemapFile));

        ObjectNode negativeDecimals = ApplicationParametersJson.toJson(ApplicationParameters.createDefault());
        ((ObjectNode) negativeDecimals.get("gui").get("decimals")).put("voltage", -1);
        Path negativeDecimalsFile = tempDir.resolve("negativeDecimals.json");
        ApplicationParametersJson.write(negativeDecimals, negativeDecimalsFile);
        assertThrows(PowsyblException.class, () -> ApplicationParametersJson.read(negativeDecimalsFile));
    }
}
