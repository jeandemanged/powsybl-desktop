/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powsybl.commons.PowsyblException;
import com.powsybl.diagram.util.layout.algorithms.parameters.Atlas2Parameters;
import com.powsybl.diagram.util.layout.postprocessing.parameters.OverlapPreventionPostProcessingParameters;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.nad.svg.EdgeInfoEnum;
import com.powsybl.nad.svg.EdgeInfoParameters;
import com.powsybl.nad.svg.LabelProviderParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.json.JsonSecurityAnalysisParameters;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.layout.PositionVoltageLevelLayoutFactoryParameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Reads/writes {@link DesktopParameters} as a single JSON document. Load flow and security analysis sections use
 * PowSyBl's own JSON serialization; the diagram sections are written field by field, each field declared once
 * through a {@link Binder} that either writes it or reads it back, so both directions can't drift apart.
 * Reading is all-or-nothing: any malformed section or wrongly typed field fails the whole read. Missing fields
 * keep their default value, so a file written by an older version still loads.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class DesktopParametersJson {

    private static final String VERSION = "1.0";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DesktopParametersJson() {
    }

    public static ObjectNode toJson(DesktopParameters parameters) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        root.set("networkImport", networkFormatsToJson(parameters.networkImport()));
        root.set("networkExport", networkFormatsToJson(parameters.networkExport()));
        sld(new Writer(root.putObject("singleLineDiagram")), parameters.sld());
        nad(new Writer(root.putObject("networkAreaDiagram")), parameters.nad());
        root.set("loadFlow", loadFlowToJson(parameters.loadFlow()));
        root.set("securityAnalysis", securityAnalysisToJson(parameters.securityAnalysis()));
        return root;
    }

    public static DesktopParameters fromJson(JsonNode root) {
        requireObject(root, "root");
        DesktopParameters defaults = DesktopParameters.createDefault();
        Reader reader = new Reader(root, "root");
        DesktopSldParameters sld = defaults.sld();
        sld(reader.child("singleLineDiagram"), sld);
        DesktopNadParameters nad = defaults.nad();
        nad(reader.child("networkAreaDiagram"), nad);
        return new DesktopParameters(
                networkFormatsFromJson(root.get("networkImport"), "networkImport"),
                networkFormatsFromJson(root.get("networkExport"), "networkExport"),
                sld,
                nad,
                root.has("loadFlow") ? loadFlowFromJson(root.get("loadFlow")) : defaults.loadFlow(),
                root.has("securityAnalysis") ? securityAnalysisFromJson(root.get("securityAnalysis")) : defaults.securityAnalysis());
    }

    // Independent copies, e.g. for a background diagram render: it must neither see the parameters window's in-place
    // edits made meanwhile, nor make its own (the renderers adjust a few fields) visible to it
    public static DesktopSldParameters copy(DesktopSldParameters parameters) {
        ObjectNode node = MAPPER.createObjectNode();
        sld(new Writer(node), parameters);
        DesktopSldParameters copy = DesktopParameters.defaultSldParameters();
        sld(new Reader(node, "singleLineDiagram"), copy);
        return copy;
    }

    public static DesktopNadParameters copy(DesktopNadParameters parameters) {
        ObjectNode node = MAPPER.createObjectNode();
        nad(new Writer(node), parameters);
        DesktopNadParameters copy = new DesktopNadParameters();
        nad(new Reader(node, "networkAreaDiagram"), copy);
        return copy;
    }

    public static void write(DesktopParameters parameters, Path path) {
        write(toJson(parameters), path);
    }

    public static void write(JsonNode json, Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode readTree(Path path) {
        try {
            return MAPPER.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // PowSyBl's JSON readers and the parameter setters reject some invalid values with IllegalArgumentException
    // or IllegalStateException: normalized, for callers to handle any malformed file with a single exception type
    public static DesktopParameters read(Path path) {
        try {
            return fromJson(readTree(path));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new PowsyblException("Invalid parameters file " + path, e);
        }
    }

    private static ObjectNode networkFormatsToJson(Map<String, Properties> formats) {
        ObjectNode node = MAPPER.createObjectNode();
        new TreeMap<>(formats).forEach((format, properties) -> {
            if (!properties.isEmpty()) {
                ObjectNode formatNode = node.putObject(format);
                new TreeMap<>(properties).forEach((key, value) -> formatNode.put(key.toString(), value.toString()));
            }
        });
        return node;
    }

    private static Map<String, Properties> networkFormatsFromJson(JsonNode node, String name) {
        Map<String, Properties> formats = new LinkedHashMap<>();
        if (node == null) {
            return formats;
        }
        requireObject(node, name);
        node.properties().forEach(format -> {
            requireObject(format.getValue(), name + "." + format.getKey());
            Properties properties = new Properties();
            format.getValue().properties().forEach(entry -> {
                if (!entry.getValue().isTextual()) {
                    throw invalid(name + "." + format.getKey() + "." + entry.getKey());
                }
                properties.setProperty(entry.getKey(), entry.getValue().textValue());
            });
            formats.put(format.getKey(), properties);
        });
        return formats;
    }

    private static JsonNode loadFlowToJson(LoadFlowParameters parameters) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonLoadFlowParameters.write(parameters, out);
        return parse(out);
    }

    private static LoadFlowParameters loadFlowFromJson(JsonNode node) {
        requireObject(node, "loadFlow");
        LoadFlowParameters parameters = JsonLoadFlowParameters.read(new ByteArrayInputStream(bytes(node)));
        if (parameters.getExtension(OpenLoadFlowParameters.class) == null) {
            OpenLoadFlowParameters.create(parameters);
        }
        return parameters;
    }

    // the embedded load flow parameters are left out: they duplicate the load flow section, and MainModel
    // rewires the app's single LoadFlowParameters instance into the security analysis parameters anyway
    private static JsonNode securityAnalysisToJson(SecurityAnalysisParameters parameters) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonSecurityAnalysisParameters.write(parameters, out);
        ObjectNode node = (ObjectNode) parse(out);
        node.remove("load-flow-parameters");
        return node;
    }

    private static SecurityAnalysisParameters securityAnalysisFromJson(JsonNode node) {
        requireObject(node, "securityAnalysis");
        SecurityAnalysisParameters parameters = JsonSecurityAnalysisParameters.read(new ByteArrayInputStream(bytes(node)));
        if (parameters.getExtension(OpenSecurityAnalysisParameters.class) == null) {
            parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters());
        }
        return parameters;
    }

    private static JsonNode parse(ByteArrayOutputStream out) {
        try {
            return MAPPER.readTree(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void requireObject(JsonNode node, String name) {
        if (!node.isObject()) {
            throw invalid(name);
        }
    }

    private static PowsyblException invalid(String name) {
        return new PowsyblException("Invalid parameters JSON: unexpected value for '" + name + "'");
    }

    // ----- Single line diagram -----

    private static void sld(Binder b, DesktopSldParameters p) {
        b.enumeration("substationLayout", DesktopSldParameters.SubstationLayoutChoice.class, p::getSubstationLayoutChoice, p::setSubstationLayoutChoice);
        b.enumeration("voltageLevelLayout", DesktopSldParameters.VoltageLevelLayout.class, p::getVoltageLevelLayout, p::setVoltageLevelLayout);
        b.enumeration("componentLibrary", DesktopSldParameters.ComponentLibraryChoice.class, p::getComponentLibraryChoice, p::setComponentLibraryChoice);
        b.enumeration("styleProvider", DesktopSldParameters.StyleChoice.class, p::getStyleChoice, p::setStyleChoice);
        sldPositionLayout(b.child("positionLayout"), p.getPositionLayoutParameters());
        sldSvg(b.child("svg"), p.getSvgParameters());
        // SLD's LayoutParameters is Jackson-serializable as is (@JsonCreator constructor)
        b.bean("layout", LayoutParameters.class, p::getLayoutParameters, p::setLayoutParameters);
    }

    private static void sldPositionLayout(Binder b, PositionVoltageLevelLayoutFactoryParameters p) {
        b.bool("feederStacked", p::isFeederStacked, p::setFeederStacked);
        b.bool("removeUnnecessaryFictitiousNodes", p::isRemoveUnnecessaryFictitiousNodes, p::setRemoveUnnecessaryFictitiousNodes);
        b.bool("substituteSingularFictitiousByFeederNode", p::isSubstituteSingularFictitiousByFeederNode, p::setSubstituteSingularFictitiousByFeederNode);
        b.bool("substituteInternalMiddle2wtByEquipmentNodes", p::isSubstituteInternalMiddle2wtByEquipmentNodes, p::setSubstituteInternalMiddle2wtByEquipmentNodes);
        b.bool("handleShunts", p::isHandleShunts, p::setHandleShunts);
        b.bool("exceptionIfPatternNotHandled", p::isExceptionIfPatternNotHandled, p::setExceptionIfPatternNotHandled);
    }

    private static void sldSvg(Binder b, com.powsybl.sld.svg.SvgParameters p) {
        b.string("prefixId", p::getPrefixId, p::setPrefixId);
        b.string("languageTag", p::getLanguageTag, p::setLanguageTag);
        b.string("undefinedValueSymbol", p::getUndefinedValueSymbol, p::setUndefinedValueSymbol);
        b.integer("voltageValuePrecision", p::getVoltageValuePrecision, p::setVoltageValuePrecision);
        b.integer("powerValuePrecision", p::getPowerValuePrecision, p::setPowerValuePrecision);
        b.integer("angleValuePrecision", p::getAngleValuePrecision, p::setAngleValuePrecision);
        b.integer("currentValuePrecision", p::getCurrentValuePrecision, p::setCurrentValuePrecision);
        b.integer("percentageValuePrecision", p::getPercentageValuePrecision, p::setPercentageValuePrecision);
        b.string("activePowerUnit", p::getActivePowerUnit, p::setActivePowerUnit);
        b.string("reactivePowerUnit", p::getReactivePowerUnit, p::setReactivePowerUnit);
        b.string("currentUnit", p::getCurrentUnit, p::setCurrentUnit);
        b.number("busInfoMargin", p::getBusInfoMargin, p::setBusInfoMargin);
        b.number("feederInfosIntraMargin", p::getFeederInfosIntraMargin, p::setFeederInfosIntraMargin);
        b.number("feederInfosOuterMargin", p::getFeederInfosOuterMargin, p::setFeederInfosOuterMargin);
        b.bool("feederInfoSymmetry", p::isFeederInfoSymmetry, p::setFeederInfoSymmetry);
        b.bool("busesLegendAdded", p::isBusesLegendAdded, p::setBusesLegendAdded);
        b.bool("useName", p::isUseName, p::setUseName);
        b.number("angleLabelShift", p::getAngleLabelShift, p::setAngleLabelShift);
        b.bool("labelCentered", p::isLabelCentered, p::setLabelCentered);
        b.bool("labelDiagonal", p::isLabelDiagonal, p::setLabelDiagonal);
        b.bool("busLabelDiagonal", p::isBusLabelDiagonal, p::setBusLabelDiagonal);
        b.bool("tooltipEnabled", p::isTooltipEnabled, p::setTooltipEnabled);
        b.enumeration("cssLocation", com.powsybl.sld.svg.SvgParameters.CssLocation.class, p::getCssLocation, p::setCssLocation);
        b.bool("avoidSvgComponentsDuplication", p::isAvoidSVGComponentsDuplication, p::setAvoidSVGComponentsDuplication);
        b.bool("drawStraightWires", p::isDrawStraightWires, p::setDrawStraightWires);
        b.bool("showGrid", p::isShowGrid, p::setShowGrid);
        b.bool("showInternalNodes", p::isShowInternalNodes, p::setShowInternalNodes);
        b.bool("displayEquipmentNodesLabel", p::isDisplayEquipmentNodesLabel, p::setDisplayEquipmentNodesLabel);
        b.bool("displayConnectivityNodesId", p::isDisplayConnectivityNodesId, p::setDisplayConnectivityNodesId);
        b.bool("unifyVoltageLevelColors", p::isUnifyVoltageLevelColors, p::setUnifyVoltageLevelColors);
    }

    // ----- Network area diagram -----

    private static void nad(Binder b, DesktopNadParameters p) {
        b.enumeration("styleProvider", DesktopNadParameters.StyleChoice.class, p::getStyleChoice, p::setStyleChoice);
        b.enumeration("layoutAlgorithm", DesktopNadParameters.LayoutAlgorithm.class, p::getLayoutAlgorithm, p::setLayoutAlgorithm);
        nadLabels(b.child("labels"), p.getLabelParameters());
        nadSvg(b.child("svg"), p.getSvgParameters());
        nadLayout(b.child("layout"), p.getLayoutParameters());
        nadAtlas2(b.child("atlas2"), p);
        nadOverlapPrevention(b.child("overlapPrevention"), p);
    }

    private static void nadLabels(Binder b, LabelProviderParameters p) {
        b.bool("busLegend", p::isBusLegend, p::setBusLegend);
        b.bool("voltageLevelDetails", p::isVoltageLevelDetails, p::setVoltageLevelDetails);
        b.bool("substationDescriptionDisplayed", p::isSubstationDescriptionDisplayed, p::setSubstationDescriptionDisplayed);
        b.bool("idDisplayed", p::isIdDisplayed, p::setIdDisplayed);
        b.bool("doubleArrowsDisplayed", p::isDoubleArrowsDisplayed, p::setDoubleArrowsDisplayed);
        // EdgeInfoParameters is an immutable record: setting one side rebuilds it with the other three kept
        Binder edgeInfo = b.child("edgeInfo");
        edgeInfo.enumeration("infoSideExternal", EdgeInfoEnum.class, () -> p.getEdgeInfoParameters().infoSideExternal(), v -> {
            EdgeInfoParameters e = p.getEdgeInfoParameters();
            p.setEdgeInfoParameters(new EdgeInfoParameters(v, e.infoMiddleSide1(), e.infoMiddleSide2(), e.infoSideInternal()));
        });
        edgeInfo.enumeration("infoMiddleSide1", EdgeInfoEnum.class, () -> p.getEdgeInfoParameters().infoMiddleSide1(), v -> {
            EdgeInfoParameters e = p.getEdgeInfoParameters();
            p.setEdgeInfoParameters(new EdgeInfoParameters(e.infoSideExternal(), v, e.infoMiddleSide2(), e.infoSideInternal()));
        });
        edgeInfo.enumeration("infoMiddleSide2", EdgeInfoEnum.class, () -> p.getEdgeInfoParameters().infoMiddleSide2(), v -> {
            EdgeInfoParameters e = p.getEdgeInfoParameters();
            p.setEdgeInfoParameters(new EdgeInfoParameters(e.infoSideExternal(), e.infoMiddleSide1(), v, e.infoSideInternal()));
        });
        edgeInfo.enumeration("infoSideInternal", EdgeInfoEnum.class, () -> p.getEdgeInfoParameters().infoSideInternal(), v -> {
            EdgeInfoParameters e = p.getEdgeInfoParameters();
            p.setEdgeInfoParameters(new EdgeInfoParameters(e.infoSideExternal(), e.infoMiddleSide1(), e.infoMiddleSide2(), v));
        });
    }

    private static void nadSvg(Binder b, com.powsybl.nad.svg.SvgParameters p) {
        Binder padding = b.child("diagramPadding");
        padding.number("left", () -> p.getDiagramPadding().getLeft(), v -> p.getDiagramPadding().setLeft(v));
        padding.number("top", () -> p.getDiagramPadding().getTop(), v -> p.getDiagramPadding().setTop(v));
        padding.number("right", () -> p.getDiagramPadding().getRight(), v -> p.getDiagramPadding().setRight(v));
        padding.number("bottom", () -> p.getDiagramPadding().getBottom(), v -> p.getDiagramPadding().setBottom(v));
        // setFixedWidth/setFixedHeight/setFixedScale each flip sizeConstraint as a side effect: it must be read after them
        b.integer("fixedWidth", p::getFixedWidth, p::setFixedWidth);
        b.integer("fixedHeight", p::getFixedHeight, p::setFixedHeight);
        b.number("fixedScale", p::getFixedScale, p::setFixedScale);
        b.enumeration("sizeConstraint", com.powsybl.nad.svg.SvgParameters.SizeConstraint.class, p::getSizeConstraint, p::setSizeConstraint);
        b.bool("insertNameDesc", p::isInsertNameDesc, p::setInsertNameDesc);
        b.string("svgPrefix", p::getSvgPrefix, p::setSvgPrefix);
        b.enumeration("cssLocation", com.powsybl.nad.svg.SvgParameters.CssLocation.class, p::getCssLocation, p::setCssLocation);
        b.number("arrowShift", p::getArrowShift, p::setArrowShift);
        b.number("arrowLabelShift", p::getArrowLabelShift, p::setArrowLabelShift);
        b.string("arrowPathIn", p::getArrowPathIn, p::setArrowPathIn);
        b.string("arrowPathOut", p::getArrowPathOut, p::setArrowPathOut);
        b.number("pstArrowHeadSize", p::getPstArrowHeadSize, p::setPstArrowHeadSize);
        b.number("doubleArrowShiftFactorArrows", p::getDoubleArrowShiftFactorArrows, p::setDoubleArrowShiftFactorArrows);
        b.number("doubleArrowShiftFactorText", p::getDoubleArrowShiftFactorText, p::setDoubleArrowShiftFactorText);
        b.number("converterStationWidth", p::getConverterStationWidth, p::setConverterStationWidth);
        b.number("voltageLevelCircleRadius", p::getVoltageLevelCircleRadius, p::setVoltageLevelCircleRadius);
        b.number("fictitiousVoltageLevelCircleRadius", p::getFictitiousVoltageLevelCircleRadius, p::setFictitiousVoltageLevelCircleRadius);
        b.number("transformerCircleRadius", p::getTransformerCircleRadius, p::setTransformerCircleRadius);
        b.number("nodeHollowWidth", p::getNodeHollowWidth, p::setNodeHollowWidth);
        b.number("unknownBusNodeExtraRadius", p::getUnknownBusNodeExtraRadius, p::setUnknownBusNodeExtraRadius);
        b.number("interAnnulusSpace", p::getInterAnnulusSpace, p::setInterAnnulusSpace);
        b.number("edgesForkLength", p::getEdgesForkLength, p::setEdgesForkLength);
        b.number("edgesForkAperture", p::getEdgesForkAperture, p::setEdgesForkAperture);
        b.number("edgeStartShift", p::getEdgeStartShift, p::setEdgeStartShift);
        b.bool("edgeInfoAlongEdge", p::isEdgeInfoAlongEdge, p::setEdgeInfoAlongEdge);
        b.bool("edgeInfosIncluded", p::isEdgeInfosIncluded, p::setEdgeInfosIncluded);
        b.number("loopDistance", p::getLoopDistance, p::setLoopDistance);
        b.number("loopEdgesAperture", p::getLoopEdgesAperture, p::setLoopEdgesAperture);
        b.number("loopControlDistance", p::getLoopControlDistance, p::setLoopControlDistance);
        b.number("injectionAperture", p::getInjectionAperture, p::setInjectionAperture);
        b.number("injectionEdgeLength", p::getInjectionEdgeLength, p::setInjectionEdgeLength);
        b.number("injectionCircleRadius", p::getInjectionCircleRadius, p::setInjectionCircleRadius);
        b.bool("voltageLevelLegendsIncluded", p::isVoltageLevelLegendsIncluded, p::setVoltageLevelLegendsIncluded);
        b.string("languageTag", p::getLanguageTag, p::setLanguageTag);
        b.integer("voltageValuePrecision", p::getVoltageValuePrecision, p::setVoltageValuePrecision);
        b.integer("powerValuePrecision", p::getPowerValuePrecision, p::setPowerValuePrecision);
        b.integer("angleValuePrecision", p::getAngleValuePrecision, p::setAngleValuePrecision);
        b.integer("currentValuePrecision", p::getCurrentValuePrecision, p::setCurrentValuePrecision);
        b.integer("percentageValuePrecision", p::getPercentageValuePrecision, p::setPercentageValuePrecision);
        b.string("undefinedValueSymbol", p::getUndefinedValueSymbol, p::setUndefinedValueSymbol);
        b.bool("highlightGraph", p::isHighlightGraph, p::setHighlightGraph);
    }

    private static void nadLayout(Binder b, com.powsybl.nad.layout.LayoutParameters p) {
        b.bool("textNodesForceLayout", p::isTextNodesForceLayout, p::setTextNodesForceLayout);
        Binder shift = b.child("textNodeFixedShift");
        shift.number("x", () -> p.getTextNodeFixedShift().x(), v -> p.setTextNodeFixedShift(v, p.getTextNodeFixedShift().y()));
        shift.number("y", () -> p.getTextNodeFixedShift().y(), v -> p.setTextNodeFixedShift(p.getTextNodeFixedShift().x(), v));
        b.number("timeoutSeconds", p::getTimeoutSeconds, p::setTimeoutSeconds);
        b.number("textNodeEdgeConnectionYShift", p::getTextNodeEdgeConnectionYShift, p::setTextNodeEdgeConnectionYShift);
        b.bool("injectionsAdded", p::isInjectionsAdded, p::setInjectionsAdded);
        b.number("scaleFactor", p::getScaleFactor, p::setScaleFactor);
        b.integer("maxSteps", p::getMaxSteps, p::setMaxSteps);
    }

    private static void nadAtlas2(Binder b, DesktopNadParameters p) {
        Supplier<Atlas2Parameters> a = p::getAtlas2Parameters;
        b.integer("maxSteps", () -> a.get().getMaxSteps(), v -> p.updateAtlas2Parameters(x -> x.withMaxSteps(v)));
        b.number("repulsionIntensity", () -> a.get().getRepulsionIntensity(), v -> p.updateAtlas2Parameters(x -> x.withRepulsionIntensity(v)));
        b.number("edgeAttractionIntensity", () -> a.get().getEdgeAttractionIntensity(), v -> p.updateAtlas2Parameters(x -> x.withEdgeAttractionIntensity(v)));
        b.bool("attractToCenterEnabled", () -> a.get().isAttractToCenterEnabled(), v -> p.updateAtlas2Parameters(x -> x.withAttractToCenterEnabled(v)));
        b.number("attractToCenterIntensity", () -> a.get().getAttractToCenterIntensity(), v -> p.updateAtlas2Parameters(x -> x.withAttractToCenterIntensity(v)));
        b.number("speedFactor", () -> a.get().getSpeedFactor(), v -> p.updateAtlas2Parameters(x -> x.withSpeedFactor(v)));
        b.number("maxSpeedFactor", () -> a.get().getMaxSpeedFactor(), v -> p.updateAtlas2Parameters(x -> x.withMaxSpeedFactor(v)));
        b.number("swingTolerance", () -> a.get().getSwingTolerance(), v -> p.updateAtlas2Parameters(x -> x.withSwingTolerance(v)));
        b.number("maxGlobalSpeedIncreaseRatio", () -> a.get().getMaxGlobalSpeedIncreaseRatio(), v -> p.updateAtlas2Parameters(x -> x.withMaxGlobalSpeedIncreaseRatio(v)));
        b.number("barnesHutTheta", () -> a.get().getBarnesHutTheta(), v -> p.updateAtlas2Parameters(x -> x.withBarnesHutTheta(v)));
        b.integer("quadtreeCalculationIncrement", () -> a.get().getQuadtreeCalculationIncrement(), v -> p.updateAtlas2Parameters(x -> x.withQuadtreeCalculationIncrement(v)));
    }

    private static void nadOverlapPrevention(Binder b, DesktopNadParameters p) {
        Supplier<OverlapPreventionPostProcessingParameters> o = p::getOverlapPreventionParameters;
        b.number("pointSizeScale", () -> o.get().getPointSizeScale(), v -> p.updateOverlapPreventionParameters(x -> x.withPointSizeScale(v)));
        b.number("pointSizeOffset", () -> o.get().getPointSizeOffset(), v -> p.updateOverlapPreventionParameters(x -> x.withPointSizeOffset(v)));
        b.number("edgeAttractionIntensity", () -> o.get().getEdgeAttractionIntensity(), v -> p.updateOverlapPreventionParameters(x -> x.withEdgeAttractionIntensity(v)));
        b.number("repulsionNoOverlapIntensity", () -> o.get().getRepulsionNoOverlapIntensity(), v -> p.updateOverlapPreventionParameters(x -> x.withRepulsionNoOverlapIntensity(v)));
        b.number("repulsionWithOverlapIntensity", () -> o.get().getRepulsionWithOverlapIntensity(), v -> p.updateOverlapPreventionParameters(x -> x.withRepulsionWithOverlapIntensity(v)));
        b.number("repulsionZoneRatio", () -> o.get().getRepulsionZoneRatio(), v -> p.updateOverlapPreventionParameters(x -> x.withRepulsionZoneRatio(v)));
        b.number("attractToCenterIntensity", () -> o.get().getAttractToCenterIntensity(), v -> p.updateOverlapPreventionParameters(x -> x.withAttractToCenterIntensity(v)));
    }

    // ----- Binders -----

    private interface Binder {
        void bool(String name, BooleanSupplier getter, Consumer<Boolean> setter);

        void integer(String name, IntSupplier getter, IntConsumer setter);

        void number(String name, DoubleSupplier getter, DoubleConsumer setter);

        void string(String name, Supplier<String> getter, Consumer<String> setter);

        <E extends Enum<E>> void enumeration(String name, Class<E> type, Supplier<E> getter, Consumer<E> setter);

        <T> void bean(String name, Class<T> type, Supplier<T> getter, Consumer<T> setter);

        Binder child(String name);
    }

    private record Writer(ObjectNode node) implements Binder {
        @Override
        public void bool(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
            node.put(name, getter.getAsBoolean());
        }

        @Override
        public void integer(String name, IntSupplier getter, IntConsumer setter) {
            node.put(name, getter.getAsInt());
        }

        @Override
        public void number(String name, DoubleSupplier getter, DoubleConsumer setter) {
            node.put(name, getter.getAsDouble());
        }

        @Override
        public void string(String name, Supplier<String> getter, Consumer<String> setter) {
            node.put(name, getter.get());
        }

        @Override
        public <E extends Enum<E>> void enumeration(String name, Class<E> type, Supplier<E> getter, Consumer<E> setter) {
            E value = getter.get();
            node.put(name, value == null ? null : value.name());
        }

        @Override
        public <T> void bean(String name, Class<T> type, Supplier<T> getter, Consumer<T> setter) {
            node.set(name, MAPPER.valueToTree(getter.get()));
        }

        @Override
        public Binder child(String name) {
            return new Writer(node.putObject(name));
        }
    }

    // A missing field is skipped (the target keeps its default), a present one must have the expected JSON type.
    private record Reader(JsonNode node, String path) implements Binder {

        private JsonNode value(String name) {
            return node.get(name);
        }

        private String path(String name) {
            return path + "." + name;
        }

        @Override
        public void bool(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
            JsonNode value = value(name);
            if (value != null) {
                if (!value.isBoolean()) {
                    throw invalid(path(name));
                }
                setter.accept(value.booleanValue());
            }
        }

        @Override
        public void integer(String name, IntSupplier getter, IntConsumer setter) {
            JsonNode value = value(name);
            if (value != null) {
                if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                    throw invalid(path(name));
                }
                setter.accept(value.intValue());
            }
        }

        @Override
        public void number(String name, DoubleSupplier getter, DoubleConsumer setter) {
            JsonNode value = value(name);
            if (value != null) {
                if (!value.isNumber()) {
                    throw invalid(path(name));
                }
                setter.accept(value.doubleValue());
            }
        }

        @Override
        public void string(String name, Supplier<String> getter, Consumer<String> setter) {
            JsonNode value = value(name);
            if (value != null) {
                if (!value.isNull() && !value.isTextual()) {
                    throw invalid(path(name));
                }
                setter.accept(value.isNull() ? null : value.textValue());
            }
        }

        @Override
        public <E extends Enum<E>> void enumeration(String name, Class<E> type, Supplier<E> getter, Consumer<E> setter) {
            JsonNode value = value(name);
            if (value != null) {
                if (value.isNull()) {
                    setter.accept(null);
                    return;
                }
                if (!value.isTextual()) {
                    throw invalid(path(name));
                }
                try {
                    setter.accept(Enum.valueOf(type, value.textValue()));
                } catch (IllegalArgumentException e) {
                    throw invalid(path(name));
                }
            }
        }

        // merged over the current (default) value's JSON, so a field missing from the file keeps its default
        // instead of being zeroed by the bean's @JsonCreator constructor
        @Override
        public <T> void bean(String name, Class<T> type, Supplier<T> getter, Consumer<T> setter) {
            JsonNode value = value(name);
            if (value != null) {
                requireObject(value, path(name));
                ObjectNode merged = MAPPER.valueToTree(getter.get());
                merged.setAll((ObjectNode) value);
                try {
                    // a field the bean doesn't know (e.g. written by another powsybl-diagram version) is ignored,
                    // like everywhere else in this document, rather than failing the whole read
                    setter.accept(MAPPER.readerFor(type)
                            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(merged));
                } catch (IOException e) {
                    throw new PowsyblException("Invalid parameters JSON: unexpected value for '" + path(name) + "'", e);
                }
            }
        }

        @Override
        public Binder child(String name) {
            JsonNode value = value(name);
            if (value == null) {
                return new Reader(MissingNode.getInstance(), path(name));
            }
            requireObject(value, path(name));
            return new Reader(value, path(name));
        }
    }
}
