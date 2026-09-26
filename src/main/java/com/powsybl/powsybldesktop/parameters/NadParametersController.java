/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.diagram.util.layout.algorithms.parameters.Atlas2Parameters;
import com.powsybl.diagram.util.layout.postprocessing.parameters.OverlapPreventionPostProcessingParameters;
import com.powsybl.nad.NadParameters;
import com.powsybl.nad.layout.LayoutParameters;
import com.powsybl.nad.svg.EdgeInfoEnum;
import com.powsybl.nad.svg.EdgeInfoParameters;
import com.powsybl.nad.svg.LabelProviderParameters;
import com.powsybl.nad.svg.Padding;
import com.powsybl.nad.svg.SvgParameters;
import com.powsybl.nad.svg.iidm.DefaultLabelProviderFactory;
import com.powsybl.nad.svg.iidm.NominalVoltageStyleProvider;
import com.powsybl.nad.svg.iidm.StyleProviderFactory;
import com.powsybl.nad.svg.iidm.TopologicalStyleProvider;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Parameters-view tab form for {@link NadParameters}, covering every field of its {@link SvgParameters} and
 * {@link LayoutParameters} except {@code svgWidthAndHeightAdded} (forced by the app, see
 * {@link com.powsybl.powsybldesktop.network.NetworkAreaDiagramRenderer}) - see
 * {@link AbstractDiagramParametersController} for the shared shape.
 * Of the {@link NadParameters} factories, the component library and edge routing ones are left out (PowSyBl ships a
 * single implementation of each), as is the id provider one (internal SVG ids, no visible effect).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NadParametersController extends AbstractDiagramParametersController<DesktopNadParameters> {

    private enum StyleChoice {
        TOPOLOGICAL,
        NOMINAL_VOLTAGE
    }

    // NadParameters' default style factory is an anonymous method reference, so the non-default choice is
    // recognized by identity against this single instance
    private static final StyleProviderFactory NOMINAL_VOLTAGE_STYLE = NominalVoltageStyleProvider::new;

    private static final String CAT_STYLE_LABELS = "styleLabels";

    private static final String CAT_SIZING = "sizing";
    private static final String CAT_TEXT_META = "textMeta";
    private static final String CAT_SVG_OUTPUT = "svgOutput";
    private static final String CAT_ARROWS = "arrows";
    private static final String CAT_NODE_GEOMETRY = "nodeGeometry";
    private static final String CAT_EDGES = "edges";
    private static final String CAT_LOOPS = "loops";
    private static final String CAT_INJECTIONS = "injections";
    private static final String CAT_LEGENDS = "legends";
    private static final String CAT_LOCALIZATION = "localization";
    private static final String CAT_DEBUG = "debug";
    private static final String CAT_LAYOUT = "layout";
    private static final String CAT_FORCE_LAYOUT = "forceLayout";

    private static final Map<String, String> CATEGORY_TITLES = buildCategoryTitles();

    private static Map<String, String> buildCategoryTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(CAT_STYLE_LABELS, Messages.get("parameters.nad.category.styleLabels"));
        titles.put(CAT_SIZING, Messages.get("parameters.nad.category.sizing"));
        titles.put(CAT_TEXT_META, Messages.get("parameters.nad.category.textMeta"));
        titles.put(CAT_SVG_OUTPUT, Messages.get("parameters.nad.category.svgOutput"));
        titles.put(CAT_ARROWS, Messages.get("parameters.nad.category.arrows"));
        titles.put(CAT_NODE_GEOMETRY, Messages.get("parameters.nad.category.nodeGeometry"));
        titles.put(CAT_EDGES, Messages.get("parameters.nad.category.edges"));
        titles.put(CAT_LOOPS, Messages.get("parameters.nad.category.loops"));
        titles.put(CAT_INJECTIONS, Messages.get("parameters.nad.category.injections"));
        titles.put(CAT_LEGENDS, Messages.get("parameters.nad.category.legends"));
        titles.put(CAT_LOCALIZATION, Messages.get("parameters.nad.category.localization"));
        titles.put(CAT_DEBUG, Messages.get("parameters.nad.category.debug"));
        titles.put(CAT_LAYOUT, Messages.get("parameters.nad.category.layout"));
        titles.put(CAT_FORCE_LAYOUT, Messages.get("parameters.nad.category.forceLayout"));
        return titles;
    }

    @FXML
    private void initialize() {
        buildCategoryList(CATEGORY_TITLES);
    }

    @Override
    protected void buildFields() {
        addStyleLabelsFields();
        addSizingFields();
        addTextMetaFields();
        addSvgOutputFields();
        addArrowsFields();
        addNodeGeometryFields();
        addEdgesFields();
        addLoopsFields();
        addInjectionsFields();
        addLegendsFields();
        addLocalizationFields();
        addDebugFields();
        addLayoutFields();
        addForceLayoutFields();
    }

    private void addStyleLabelsFields() {
        addEnumField(CAT_STYLE_LABELS, label("styleProvider"), tooltip("styleProvider"), StyleChoice.class,
                p -> p.getStyleProviderFactory() == NOMINAL_VOLTAGE_STYLE ? StyleChoice.NOMINAL_VOLTAGE : StyleChoice.TOPOLOGICAL,
                (p, v) -> p.setStyleProviderFactory(v == StyleChoice.NOMINAL_VOLTAGE ? NOMINAL_VOLTAGE_STYLE : TopologicalStyleProvider::new));
        addBooleanField(CAT_STYLE_LABELS, label("busLegend"), tooltip("busLegend"),
                p -> labelParameters(p).isBusLegend(), (p, v) -> labelParameters(p).setBusLegend(v));
        addBooleanField(CAT_STYLE_LABELS, label("voltageLevelDetails"), tooltip("voltageLevelDetails"),
                p -> labelParameters(p).isVoltageLevelDetails(), (p, v) -> labelParameters(p).setVoltageLevelDetails(v));
        addBooleanField(CAT_STYLE_LABELS, label("substationDescriptionDisplayed"), tooltip("substationDescriptionDisplayed"),
                p -> labelParameters(p).isSubstationDescriptionDisplayed(), (p, v) -> labelParameters(p).setSubstationDescriptionDisplayed(v));
        addBooleanField(CAT_STYLE_LABELS, label("idDisplayed"), tooltip("idDisplayed"),
                p -> labelParameters(p).isIdDisplayed(), (p, v) -> labelParameters(p).setIdDisplayed(v));
        addBooleanField(CAT_STYLE_LABELS, label("doubleArrowsDisplayed"), tooltip("doubleArrowsDisplayed"),
                p -> labelParameters(p).isDoubleArrowsDisplayed(), (p, v) -> labelParameters(p).setDoubleArrowsDisplayed(v));
        addEdgeInfoField("infoSideExternal", EdgeInfoParameters::infoSideExternal,
                (e, v) -> new EdgeInfoParameters(v, e.infoMiddleSide1(), e.infoMiddleSide2(), e.infoSideInternal()));
        addEdgeInfoField("infoMiddleSide1", EdgeInfoParameters::infoMiddleSide1,
                (e, v) -> new EdgeInfoParameters(e.infoSideExternal(), v, e.infoMiddleSide2(), e.infoSideInternal()));
        addEdgeInfoField("infoMiddleSide2", EdgeInfoParameters::infoMiddleSide2,
                (e, v) -> new EdgeInfoParameters(e.infoSideExternal(), e.infoMiddleSide1(), v, e.infoSideInternal()));
        addEdgeInfoField("infoSideInternal", EdgeInfoParameters::infoSideInternal,
                (e, v) -> new EdgeInfoParameters(e.infoSideExternal(), e.infoMiddleSide1(), e.infoMiddleSide2(), v));
    }

    // EdgeInfoParameters is an immutable record: editing one side rebuilds it with the other three kept
    private void addEdgeInfoField(String param, Function<EdgeInfoParameters, EdgeInfoEnum> getter,
                                  BiFunction<EdgeInfoParameters, EdgeInfoEnum, EdgeInfoParameters> with) {
        addEnumField(CAT_STYLE_LABELS, label(param), tooltip(param), EdgeInfoEnum.class,
                p -> getter.apply(labelParameters(p).getEdgeInfoParameters()),
                (p, v) -> labelParameters(p).setEdgeInfoParameters(with.apply(labelParameters(p).getEdgeInfoParameters(), v)));
    }

    // the app never replaces NadParameters' default label provider factory, whose parameters are mutable in place
    private static LabelProviderParameters labelParameters(NadParameters parameters) {
        return ((DefaultLabelProviderFactory) parameters.getLabelProviderFactory()).getParameters();
    }

    private void addSizingFields() {
        addPaddingField();
        addSizeConstraintFields();
    }

    // NAD's Padding is a separate mutable class (own get/set per side), unlike SLD's immutable record,
    // but is still exposed here as four numeric fields for symmetry with the SLD form.
    private void addPaddingField() {
        TextField leftField = new TextField();
        TextField topField = new TextField();
        TextField rightField = new TextField();
        TextField bottomField = new TextField();
        refreshers.add(() -> {
            Padding padding = parametersProperty.getValue().getSvgParameters().getDiagramPadding();
            leftField.setText(Double.toString(padding.getLeft()));
            topField.setText(Double.toString(padding.getTop()));
            rightField.setText(Double.toString(padding.getRight()));
            bottomField.setText(Double.toString(padding.getBottom()));
        });
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                Padding padding = new Padding(Double.parseDouble(leftField.getText()), Double.parseDouble(topField.getText()),
                        Double.parseDouble(rightField.getText()), Double.parseDouble(bottomField.getText()));
                parametersProperty.getValue().getSvgParameters().setDiagramPadding(padding);
                notifyChange();
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(leftField, commit);
        bindCommit(topField, commit);
        bindCommit(rightField, commit);
        bindCommit(bottomField, commit);
        HBox box = fieldGroup(new Label(Messages.get("parameters.diagram.padding.left")), leftField,
                new Label(Messages.get("parameters.diagram.padding.top")), topField,
                new Label(Messages.get("parameters.diagram.padding.right")), rightField,
                new Label(Messages.get("parameters.diagram.padding.bottom")), bottomField);
        addRow(CAT_SIZING, label("diagramPadding"), tooltip("diagramPadding"), box);
    }

    // setFixedWidth/setFixedHeight/setFixedScale each silently flip sizeConstraint as a side effect (see
    // SvgParameters), so the mode dropdown is re-synced (via refresh()) after committing any of them.
    private void addSizeConstraintFields() {
        ChoiceBox<SvgParameters.SizeConstraint> modeChoiceBox = new ChoiceBox<>(
                FXCollections.observableArrayList(SvgParameters.SizeConstraint.values()));
        TextField widthField = new TextField();
        TextField heightField = new TextField();
        TextField scaleField = new TextField();

        refreshers.add(() -> {
            SvgParameters svgParameters = parametersProperty.getValue().getSvgParameters();
            modeChoiceBox.setValue(svgParameters.getSizeConstraint());
            widthField.setText(Integer.toString(svgParameters.getFixedWidth()));
            heightField.setText(Integer.toString(svgParameters.getFixedHeight()));
            scaleField.setText(Double.toString(svgParameters.getFixedScale()));
        });

        modeChoiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                parametersProperty.getValue().getSvgParameters().setSizeConstraint(newValue);
                notifyChange();
            }
        });
        bindCommit(widthField, () -> {
            if (refreshing) {
                return;
            }
            try {
                parametersProperty.getValue().getSvgParameters().setFixedWidth(Integer.parseInt(widthField.getText()));
            } catch (NumberFormatException e) {
                refresh();
                return;
            }
            refresh();
            notifyChange();
        });
        bindCommit(heightField, () -> {
            if (refreshing) {
                return;
            }
            try {
                parametersProperty.getValue().getSvgParameters().setFixedHeight(Integer.parseInt(heightField.getText()));
            } catch (NumberFormatException e) {
                refresh();
                return;
            }
            refresh();
            notifyChange();
        });
        bindCommit(scaleField, () -> {
            if (refreshing) {
                return;
            }
            try {
                parametersProperty.getValue().getSvgParameters().setFixedScale(Double.parseDouble(scaleField.getText()));
            } catch (NumberFormatException e) {
                refresh();
                return;
            }
            refresh();
            notifyChange();
        });

        HBox box = fieldGroup(modeChoiceBox,
                new Label(Messages.get("parameters.nad.param.fixedWidth.label")), widthField,
                new Label(Messages.get("parameters.nad.param.fixedHeight.label")), heightField,
                new Label(Messages.get("parameters.nad.param.fixedScale.label")), scaleField);
        addRow(CAT_SIZING, label("sizeConstraint"), tooltip("sizeConstraint"), box);
    }

    private void addTextMetaFields() {
        addBooleanField(CAT_TEXT_META, label("insertNameDesc"), tooltip("insertNameDesc"),
                p -> p.getSvgParameters().isInsertNameDesc(), (p, v) -> p.getSvgParameters().setInsertNameDesc(v));
        addStringField(CAT_TEXT_META, label("svgPrefix"), tooltip("svgPrefix"),
                p -> p.getSvgParameters().getSvgPrefix(), (p, v) -> p.getSvgParameters().setSvgPrefix(v));
    }

    private void addSvgOutputFields() {
        addEnumField(CAT_SVG_OUTPUT, label("cssLocation"), tooltip("cssLocation"), SvgParameters.CssLocation.class,
                p -> p.getSvgParameters().getCssLocation(), (p, v) -> p.getSvgParameters().setCssLocation(v));
    }

    private void addArrowsFields() {
        addDoubleField(CAT_ARROWS, label("arrowShift"), tooltip("arrowShift"),
                p -> p.getSvgParameters().getArrowShift(), (p, v) -> p.getSvgParameters().setArrowShift(v));
        addDoubleField(CAT_ARROWS, label("arrowLabelShift"), tooltip("arrowLabelShift"),
                p -> p.getSvgParameters().getArrowLabelShift(), (p, v) -> p.getSvgParameters().setArrowLabelShift(v));
        addStringField(CAT_ARROWS, label("arrowPathIn"), tooltip("arrowPathIn"),
                p -> p.getSvgParameters().getArrowPathIn(), (p, v) -> p.getSvgParameters().setArrowPathIn(v));
        addStringField(CAT_ARROWS, label("arrowPathOut"), tooltip("arrowPathOut"),
                p -> p.getSvgParameters().getArrowPathOut(), (p, v) -> p.getSvgParameters().setArrowPathOut(v));
        addDoubleField(CAT_ARROWS, label("pstArrowHeadSize"), tooltip("pstArrowHeadSize"),
                p -> p.getSvgParameters().getPstArrowHeadSize(), (p, v) -> p.getSvgParameters().setPstArrowHeadSize(v));
        addDoubleField(CAT_ARROWS, label("doubleArrowShiftFactorArrows"), tooltip("doubleArrowShiftFactorArrows"),
                p -> p.getSvgParameters().getDoubleArrowShiftFactorArrows(), (p, v) -> p.getSvgParameters().setDoubleArrowShiftFactorArrows(v));
        addDoubleField(CAT_ARROWS, label("doubleArrowShiftFactorText"), tooltip("doubleArrowShiftFactorText"),
                p -> p.getSvgParameters().getDoubleArrowShiftFactorText(), (p, v) -> p.getSvgParameters().setDoubleArrowShiftFactorText(v));
    }

    private void addNodeGeometryFields() {
        addDoubleField(CAT_NODE_GEOMETRY, label("converterStationWidth"), tooltip("converterStationWidth"),
                p -> p.getSvgParameters().getConverterStationWidth(), (p, v) -> p.getSvgParameters().setConverterStationWidth(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("voltageLevelCircleRadius"), tooltip("voltageLevelCircleRadius"),
                p -> p.getSvgParameters().getVoltageLevelCircleRadius(), (p, v) -> p.getSvgParameters().setVoltageLevelCircleRadius(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("fictitiousVoltageLevelCircleRadius"), tooltip("fictitiousVoltageLevelCircleRadius"),
                p -> p.getSvgParameters().getFictitiousVoltageLevelCircleRadius(), (p, v) -> p.getSvgParameters().setFictitiousVoltageLevelCircleRadius(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("transformerCircleRadius"), tooltip("transformerCircleRadius"),
                p -> p.getSvgParameters().getTransformerCircleRadius(), (p, v) -> p.getSvgParameters().setTransformerCircleRadius(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("nodeHollowWidth"), tooltip("nodeHollowWidth"),
                p -> p.getSvgParameters().getNodeHollowWidth(), (p, v) -> p.getSvgParameters().setNodeHollowWidth(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("unknownBusNodeExtraRadius"), tooltip("unknownBusNodeExtraRadius"),
                p -> p.getSvgParameters().getUnknownBusNodeExtraRadius(), (p, v) -> p.getSvgParameters().setUnknownBusNodeExtraRadius(v));
        addDoubleField(CAT_NODE_GEOMETRY, label("interAnnulusSpace"), tooltip("interAnnulusSpace"),
                p -> p.getSvgParameters().getInterAnnulusSpace(), (p, v) -> p.getSvgParameters().setInterAnnulusSpace(v));
    }

    private void addEdgesFields() {
        addDoubleField(CAT_EDGES, label("edgesForkLength"), tooltip("edgesForkLength"),
                p -> p.getSvgParameters().getEdgesForkLength(), (p, v) -> p.getSvgParameters().setEdgesForkLength(v));
        addDoubleField(CAT_EDGES, label("edgesForkAperture"), tooltip("edgesForkAperture"),
                p -> p.getSvgParameters().getEdgesForkAperture(), (p, v) -> p.getSvgParameters().setEdgesForkAperture(v));
        addDoubleField(CAT_EDGES, label("edgeStartShift"), tooltip("edgeStartShift"),
                p -> p.getSvgParameters().getEdgeStartShift(), (p, v) -> p.getSvgParameters().setEdgeStartShift(v));
        addBooleanField(CAT_EDGES, label("edgeInfoAlongEdge"), tooltip("edgeInfoAlongEdge"),
                p -> p.getSvgParameters().isEdgeInfoAlongEdge(), (p, v) -> p.getSvgParameters().setEdgeInfoAlongEdge(v));
        addBooleanField(CAT_EDGES, label("edgeInfosIncluded"), tooltip("edgeInfosIncluded"),
                p -> p.getSvgParameters().isEdgeInfosIncluded(), (p, v) -> p.getSvgParameters().setEdgeInfosIncluded(v));
    }

    private void addLoopsFields() {
        addDoubleField(CAT_LOOPS, label("loopDistance"), tooltip("loopDistance"),
                p -> p.getSvgParameters().getLoopDistance(), (p, v) -> p.getSvgParameters().setLoopDistance(v));
        addDoubleField(CAT_LOOPS, label("loopEdgesAperture"), tooltip("loopEdgesAperture"),
                p -> p.getSvgParameters().getLoopEdgesAperture(), (p, v) -> p.getSvgParameters().setLoopEdgesAperture(v));
        addDoubleField(CAT_LOOPS, label("loopControlDistance"), tooltip("loopControlDistance"),
                p -> p.getSvgParameters().getLoopControlDistance(), (p, v) -> p.getSvgParameters().setLoopControlDistance(v));
    }

    private void addInjectionsFields() {
        addDoubleField(CAT_INJECTIONS, label("injectionAperture"), tooltip("injectionAperture"),
                p -> p.getSvgParameters().getInjectionAperture(), (p, v) -> p.getSvgParameters().setInjectionAperture(v));
        addDoubleField(CAT_INJECTIONS, label("injectionEdgeLength"), tooltip("injectionEdgeLength"),
                p -> p.getSvgParameters().getInjectionEdgeLength(), (p, v) -> p.getSvgParameters().setInjectionEdgeLength(v));
        addDoubleField(CAT_INJECTIONS, label("injectionCircleRadius"), tooltip("injectionCircleRadius"),
                p -> p.getSvgParameters().getInjectionCircleRadius(), (p, v) -> p.getSvgParameters().setInjectionCircleRadius(v));
    }

    private void addLegendsFields() {
        addBooleanField(CAT_LEGENDS, label("voltageLevelLegendsIncluded"), tooltip("voltageLevelLegendsIncluded"),
                p -> p.getSvgParameters().isVoltageLevelLegendsIncluded(), (p, v) -> p.getSvgParameters().setVoltageLevelLegendsIncluded(v));
    }

    private void addLocalizationFields() {
        addStringField(CAT_LOCALIZATION, label("languageTag"), tooltip("languageTag"),
                p -> p.getSvgParameters().getLanguageTag(), (p, v) -> p.getSvgParameters().setLanguageTag(v));
        addIntField(CAT_LOCALIZATION, label("voltageValuePrecision"), tooltip("voltageValuePrecision"),
                p -> p.getSvgParameters().getVoltageValuePrecision(), (p, v) -> p.getSvgParameters().setVoltageValuePrecision(v));
        addIntField(CAT_LOCALIZATION, label("powerValuePrecision"), tooltip("powerValuePrecision"),
                p -> p.getSvgParameters().getPowerValuePrecision(), (p, v) -> p.getSvgParameters().setPowerValuePrecision(v));
        addIntField(CAT_LOCALIZATION, label("angleValuePrecision"), tooltip("angleValuePrecision"),
                p -> p.getSvgParameters().getAngleValuePrecision(), (p, v) -> p.getSvgParameters().setAngleValuePrecision(v));
        addIntField(CAT_LOCALIZATION, label("currentValuePrecision"), tooltip("currentValuePrecision"),
                p -> p.getSvgParameters().getCurrentValuePrecision(), (p, v) -> p.getSvgParameters().setCurrentValuePrecision(v));
        addIntField(CAT_LOCALIZATION, label("percentageValuePrecision"), tooltip("percentageValuePrecision"),
                p -> p.getSvgParameters().getPercentageValuePrecision(), (p, v) -> p.getSvgParameters().setPercentageValuePrecision(v));
        addStringField(CAT_LOCALIZATION, label("undefinedValueSymbol"), tooltip("undefinedValueSymbol"),
                p -> p.getSvgParameters().getUndefinedValueSymbol(), (p, v) -> p.getSvgParameters().setUndefinedValueSymbol(v));
    }

    private void addDebugFields() {
        addBooleanField(CAT_DEBUG, label("highlightGraph"), tooltip("highlightGraph"),
                p -> p.getSvgParameters().isHighlightGraph(), (p, v) -> p.getSvgParameters().setHighlightGraph(v));
    }

    private void addLayoutFields() {
        addEnumField(CAT_LAYOUT, label("layoutAlgorithm"), tooltip("layoutAlgorithm"), DesktopNadParameters.LayoutAlgorithm.class,
                DesktopNadParameters::getLayoutAlgorithm, DesktopNadParameters::setLayoutAlgorithm);
        addBooleanField(CAT_LAYOUT, label("textNodesForceLayout"), tooltip("textNodesForceLayout"),
                p -> p.getLayoutParameters().isTextNodesForceLayout(), (p, v) -> p.getLayoutParameters().setTextNodesForceLayout(v));
        addTextNodeFixedShiftField();
        addDoubleField(CAT_LAYOUT, label("timeoutSeconds"), tooltip("timeoutSeconds"),
                p -> p.getLayoutParameters().getTimeoutSeconds(), (p, v) -> p.getLayoutParameters().setTimeoutSeconds(v));
        addDoubleField(CAT_LAYOUT, label("textNodeEdgeConnectionYShift"), tooltip("textNodeEdgeConnectionYShift"),
                p -> p.getLayoutParameters().getTextNodeEdgeConnectionYShift(), (p, v) -> p.getLayoutParameters().setTextNodeEdgeConnectionYShift(v));
        addBooleanField(CAT_LAYOUT, label("injectionsAdded"), tooltip("injectionsAdded"),
                p -> p.getLayoutParameters().isInjectionsAdded(), (p, v) -> p.getLayoutParameters().setInjectionsAdded(v));
        addDoubleField(CAT_LAYOUT, label("scaleFactor"), tooltip("scaleFactor"),
                p -> p.getLayoutParameters().getScaleFactor(), (p, v) -> p.getLayoutParameters().setScaleFactor(v));
    }

    // getTextNodeFixedShift() returns a Point, but the setter takes two raw doubles (x, y).
    private void addTextNodeFixedShiftField() {
        TextField xField = new TextField();
        TextField yField = new TextField();
        refreshers.add(() -> {
            var shift = parametersProperty.getValue().getLayoutParameters().getTextNodeFixedShift();
            xField.setText(Double.toString(shift.x()));
            yField.setText(Double.toString(shift.y()));
        });
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                parametersProperty.getValue().getLayoutParameters().setTextNodeFixedShift(
                        Double.parseDouble(xField.getText()), Double.parseDouble(yField.getText()));
                notifyChange();
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(xField, commit);
        bindCommit(yField, commit);
        HBox box = fieldGroup(new Label("X:"), xField, new Label("Y:"), yField);
        addRow(CAT_LAYOUT, label("textNodeFixedShift"), tooltip("textNodeFixedShift"), box);
    }

    private void addForceLayoutFields() {
        addIntField(CAT_FORCE_LAYOUT, label("atlas2MaxSteps"), tooltip("atlas2MaxSteps"), p -> p.getAtlas2Parameters().getMaxSteps(),
                (p, v) -> p.setAtlas2Parameters(atlas2Builder(p.getAtlas2Parameters()).withMaxSteps(v).build()));
        addAtlas2Field("atlas2RepulsionIntensity", Atlas2Parameters::getRepulsionIntensity, Atlas2Parameters.Builder::withRepulsionIntensity);
        addAtlas2Field("atlas2EdgeAttractionIntensity", Atlas2Parameters::getEdgeAttractionIntensity, Atlas2Parameters.Builder::withEdgeAttractionIntensity);
        addBooleanField(CAT_FORCE_LAYOUT, label("atlas2AttractToCenterEnabled"), tooltip("atlas2AttractToCenterEnabled"),
                p -> p.getAtlas2Parameters().isAttractToCenterEnabled(),
                (p, v) -> p.setAtlas2Parameters(atlas2Builder(p.getAtlas2Parameters()).withAttractToCenterEnabled(v).build()));
        addAtlas2Field("atlas2AttractToCenterIntensity", Atlas2Parameters::getAttractToCenterIntensity, Atlas2Parameters.Builder::withAttractToCenterIntensity);
        addAtlas2Field("atlas2SpeedFactor", Atlas2Parameters::getSpeedFactor, Atlas2Parameters.Builder::withSpeedFactor);
        addAtlas2Field("atlas2MaxSpeedFactor", Atlas2Parameters::getMaxSpeedFactor, Atlas2Parameters.Builder::withMaxSpeedFactor);
        addAtlas2Field("atlas2SwingTolerance", Atlas2Parameters::getSwingTolerance, Atlas2Parameters.Builder::withSwingTolerance);
        addAtlas2Field("atlas2MaxGlobalSpeedIncreaseRatio", Atlas2Parameters::getMaxGlobalSpeedIncreaseRatio, Atlas2Parameters.Builder::withMaxGlobalSpeedIncreaseRatio);
        addAtlas2Field("atlas2BarnesHutTheta", Atlas2Parameters::getBarnesHutTheta, Atlas2Parameters.Builder::withBarnesHutTheta);
        addIntField(CAT_FORCE_LAYOUT, label("atlas2QuadtreeCalculationIncrement"), tooltip("atlas2QuadtreeCalculationIncrement"),
                p -> p.getAtlas2Parameters().getQuadtreeCalculationIncrement(),
                (p, v) -> p.setAtlas2Parameters(atlas2Builder(p.getAtlas2Parameters()).withQuadtreeCalculationIncrement(v).build()));

        addOverlapField("overlapPointSizeScale", OverlapPreventionPostProcessingParameters::getPointSizeScale,
                OverlapPreventionPostProcessingParameters.Builder::withPointSizeScale);
        addOverlapField("overlapPointSizeOffset", OverlapPreventionPostProcessingParameters::getPointSizeOffset,
                OverlapPreventionPostProcessingParameters.Builder::withPointSizeOffset);
        addOverlapField("overlapEdgeAttractionIntensity", OverlapPreventionPostProcessingParameters::getEdgeAttractionIntensity,
                OverlapPreventionPostProcessingParameters.Builder::withEdgeAttractionIntensity);
        addOverlapField("overlapRepulsionNoOverlapIntensity", OverlapPreventionPostProcessingParameters::getRepulsionNoOverlapIntensity,
                OverlapPreventionPostProcessingParameters.Builder::withRepulsionNoOverlapIntensity);
        addOverlapField("overlapRepulsionWithOverlapIntensity", OverlapPreventionPostProcessingParameters::getRepulsionWithOverlapIntensity,
                OverlapPreventionPostProcessingParameters.Builder::withRepulsionWithOverlapIntensity);
        addOverlapField("overlapRepulsionZoneRatio", OverlapPreventionPostProcessingParameters::getRepulsionZoneRatio,
                OverlapPreventionPostProcessingParameters.Builder::withRepulsionZoneRatio);
        addOverlapField("overlapAttractToCenterIntensity", OverlapPreventionPostProcessingParameters::getAttractToCenterIntensity,
                OverlapPreventionPostProcessingParameters.Builder::withAttractToCenterIntensity);

        addIntField(CAT_FORCE_LAYOUT, label("basicForceMaxSteps"), tooltip("basicForceMaxSteps"),
                p -> p.getLayoutParameters().getMaxSteps(), (p, v) -> p.getLayoutParameters().setMaxSteps(v));
    }

    // Atlas2Parameters and OverlapPreventionPostProcessingParameters are immutable, built through builders that can't
    // be seeded from an existing instance: editing one value rebuilds the whole object with the others copied over
    private void addAtlas2Field(String param, Function<Atlas2Parameters, Double> getter,
                                BiFunction<Atlas2Parameters.Builder, Double, Atlas2Parameters.Builder> with) {
        addDoubleField(CAT_FORCE_LAYOUT, label(param), tooltip(param), p -> getter.apply(p.getAtlas2Parameters()),
                (p, v) -> p.setAtlas2Parameters(with.apply(atlas2Builder(p.getAtlas2Parameters()), v).build()));
    }

    private void addOverlapField(String param, Function<OverlapPreventionPostProcessingParameters, Double> getter,
                                 BiFunction<OverlapPreventionPostProcessingParameters.Builder, Double, OverlapPreventionPostProcessingParameters.Builder> with) {
        addDoubleField(CAT_FORCE_LAYOUT, label(param), tooltip(param), p -> getter.apply(p.getOverlapPreventionParameters()),
                (p, v) -> p.setOverlapPreventionParameters(with.apply(overlapBuilder(p.getOverlapPreventionParameters()), v).build()));
    }

    private static Atlas2Parameters.Builder atlas2Builder(Atlas2Parameters parameters) {
        return new Atlas2Parameters.Builder()
                .withMaxSteps(parameters.getMaxSteps())
                .withRepulsionIntensity(parameters.getRepulsionIntensity())
                .withEdgeAttractionIntensity(parameters.getEdgeAttractionIntensity())
                .withAttractToCenterIntensity(parameters.getAttractToCenterIntensity())
                .withSpeedFactor(parameters.getSpeedFactor())
                .withMaxSpeedFactor(parameters.getMaxSpeedFactor())
                .withSwingTolerance(parameters.getSwingTolerance())
                .withMaxGlobalSpeedIncreaseRatio(parameters.getMaxGlobalSpeedIncreaseRatio())
                .withAttractToCenterEnabled(parameters.isAttractToCenterEnabled())
                .withBarnesHutTheta(parameters.getBarnesHutTheta())
                .withQuadtreeCalculationIncrement(parameters.getQuadtreeCalculationIncrement());
    }

    private static OverlapPreventionPostProcessingParameters.Builder overlapBuilder(OverlapPreventionPostProcessingParameters parameters) {
        return new OverlapPreventionPostProcessingParameters.Builder()
                .withPointSizeScale(parameters.getPointSizeScale())
                .withPointSizeOffset(parameters.getPointSizeOffset())
                .withEdgeAttractionIntensity(parameters.getEdgeAttractionIntensity())
                .withRepulsionNoOverlapIntensity(parameters.getRepulsionNoOverlapIntensity())
                .withRepulsionWithOverlapIntensity(parameters.getRepulsionWithOverlapIntensity())
                .withRepulsionZoneRatio(parameters.getRepulsionZoneRatio())
                .withAttractToCenterIntensity(parameters.getAttractToCenterIntensity());
    }

    private static String label(String param) {
        return Messages.get("parameters.nad.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("parameters.nad.param." + param + ".tooltip");
    }
}
