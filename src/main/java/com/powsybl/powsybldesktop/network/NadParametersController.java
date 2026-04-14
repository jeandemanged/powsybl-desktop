/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.nad.NadParameters;
import com.powsybl.nad.layout.LayoutParameters;
import com.powsybl.nad.svg.Padding;
import com.powsybl.nad.svg.SvgParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Popup form for {@link NadParameters}, covering every field of its {@link SvgParameters} and
 * {@link LayoutParameters} except {@code svgWidthAndHeightAdded} (forced by the app, see
 * {@link NetworkAreaDiagramRenderer}) and {@code maxSteps} (excluded per the plan) - see
 * {@link AbstractDiagramParametersController} for the shared shape.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NadParametersController extends AbstractDiagramParametersController<NadParameters> {

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

    private static final Map<String, String> CATEGORY_TITLES = buildCategoryTitles();

    private static Map<String, String> buildCategoryTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(CAT_SIZING, Messages.get("substations.nadParameters.category.sizing"));
        titles.put(CAT_TEXT_META, Messages.get("substations.nadParameters.category.textMeta"));
        titles.put(CAT_SVG_OUTPUT, Messages.get("substations.nadParameters.category.svgOutput"));
        titles.put(CAT_ARROWS, Messages.get("substations.nadParameters.category.arrows"));
        titles.put(CAT_NODE_GEOMETRY, Messages.get("substations.nadParameters.category.nodeGeometry"));
        titles.put(CAT_EDGES, Messages.get("substations.nadParameters.category.edges"));
        titles.put(CAT_LOOPS, Messages.get("substations.nadParameters.category.loops"));
        titles.put(CAT_INJECTIONS, Messages.get("substations.nadParameters.category.injections"));
        titles.put(CAT_LEGENDS, Messages.get("substations.nadParameters.category.legends"));
        titles.put(CAT_LOCALIZATION, Messages.get("substations.nadParameters.category.localization"));
        titles.put(CAT_DEBUG, Messages.get("substations.nadParameters.category.debug"));
        titles.put(CAT_LAYOUT, Messages.get("substations.nadParameters.category.layout"));
        return titles;
    }

    @FXML
    private void initialize() {
        buildCategoryList(CATEGORY_TITLES);
    }

    @Override
    protected void buildFields() {
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
    }

    private void addSizingFields() {
        addPaddingField();
        addSizeConstraintFields();
    }

    // NAD's Padding is a separate mutable class (own get/set per side), unlike SLD's immutable record,
    // but is still exposed here as four numeric fields for symmetry with the SLD popup.
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
        HBox box = new HBox(5,
                new Label(Messages.get("substations.diagram.padding.left")), leftField,
                new Label(Messages.get("substations.diagram.padding.top")), topField,
                new Label(Messages.get("substations.diagram.padding.right")), rightField,
                new Label(Messages.get("substations.diagram.padding.bottom")), bottomField);
        box.setAlignment(Pos.CENTER_LEFT);
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

        HBox box = new HBox(5, modeChoiceBox,
                new Label(Messages.get("substations.nadParameters.param.fixedWidth.label")), widthField,
                new Label(Messages.get("substations.nadParameters.param.fixedHeight.label")), heightField,
                new Label(Messages.get("substations.nadParameters.param.fixedScale.label")), scaleField);
        box.setAlignment(Pos.CENTER_LEFT);
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
        HBox box = new HBox(5, new Label("X:"), xField, new Label("Y:"), yField);
        box.setAlignment(Pos.CENTER_LEFT);
        addRow(CAT_LAYOUT, label("textNodeFixedShift"), tooltip("textNodeFixedShift"), box);
    }

    private static String label(String param) {
        return Messages.get("substations.nadParameters.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("substations.nadParameters.param." + param + ".tooltip");
    }
}
