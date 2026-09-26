/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sld.SldParameters;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.svg.SvgParameters;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Parameters-view tab form for {@link SldParameters}, covering every field of its {@link SvgParameters} and
 * {@link LayoutParameters} except {@code svgWidthAndHeightAdded} (forced by the app, see
 * {@link com.powsybl.powsybldesktop.network.SubstationDiagramRenderer}) and {@code diagramName} (computed from the selected container on
 * every render, not user-editable) - see {@link AbstractDiagramParametersController} for the shared shape.
 * {@code componentsSize} (a non-fluent, {@code @JsonIgnore}d component-type-to-size lookup table) and
 * NAD's {@code maxSteps} are likewise excluded, per the plan.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SldParametersController extends AbstractDiagramParametersController<SldParameters> {

    private static final String CAT_IDENTIFICATION = "identification";
    private static final String CAT_LOCALIZATION = "localization";
    private static final String CAT_BUS_FEEDER = "busFeeder";
    private static final String CAT_LABELS = "labels";
    private static final String CAT_INTERACTION = "interaction";
    private static final String CAT_SVG_OUTPUT = "svgOutput";
    private static final String CAT_DRAWING_STYLE = "drawingStyle";
    private static final String CAT_BUS_SPACING = "busSpacing";
    private static final String CAT_CELL_DIMENSIONS = "cellDimensions";
    private static final String CAT_SNAKE_LINES = "snakeLines";
    private static final String CAT_COMPONENT_SIZING = "componentSizing";
    private static final String CAT_PADDING = "padding";
    private static final String CAT_ALIGNMENT_TOPOLOGY = "alignmentTopology";

    private static final Map<String, String> CATEGORY_TITLES = buildCategoryTitles();

    private static Map<String, String> buildCategoryTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(CAT_IDENTIFICATION, Messages.get("parameters.sld.category.identification"));
        titles.put(CAT_LOCALIZATION, Messages.get("parameters.sld.category.localization"));
        titles.put(CAT_BUS_FEEDER, Messages.get("parameters.sld.category.busFeeder"));
        titles.put(CAT_LABELS, Messages.get("parameters.sld.category.labels"));
        titles.put(CAT_INTERACTION, Messages.get("parameters.sld.category.interaction"));
        titles.put(CAT_SVG_OUTPUT, Messages.get("parameters.sld.category.svgOutput"));
        titles.put(CAT_DRAWING_STYLE, Messages.get("parameters.sld.category.drawingStyle"));
        titles.put(CAT_BUS_SPACING, Messages.get("parameters.sld.category.busSpacing"));
        titles.put(CAT_CELL_DIMENSIONS, Messages.get("parameters.sld.category.cellDimensions"));
        titles.put(CAT_SNAKE_LINES, Messages.get("parameters.sld.category.snakeLines"));
        titles.put(CAT_COMPONENT_SIZING, Messages.get("parameters.sld.category.componentSizing"));
        titles.put(CAT_PADDING, Messages.get("parameters.sld.category.padding"));
        titles.put(CAT_ALIGNMENT_TOPOLOGY, Messages.get("parameters.sld.category.alignmentTopology"));
        return titles;
    }

    @FXML
    private void initialize() {
        buildCategoryList(CATEGORY_TITLES);
    }

    @Override
    protected void buildFields() {
        addIdentificationFields();
        addLocalizationFields();
        addBusFeederFields();
        addLabelsFields();
        addInteractionFields();
        addSvgOutputFields();
        addDrawingStyleFields();
        addBusSpacingFields();
        addCellDimensionsFields();
        addSnakeLinesFields();
        addComponentSizingFields();
        addPaddingFields();
        addAlignmentTopologyFields();
    }

    private void addIdentificationFields() {
        addStringField(CAT_IDENTIFICATION, label("prefixId"), tooltip("prefixId"),
                p -> p.getSvgParameters().getPrefixId(), (p, v) -> p.getSvgParameters().setPrefixId(v));
    }

    private void addLocalizationFields() {
        addStringField(CAT_LOCALIZATION, label("languageTag"), tooltip("languageTag"),
                p -> p.getSvgParameters().getLanguageTag(), (p, v) -> p.getSvgParameters().setLanguageTag(v));
        addStringField(CAT_LOCALIZATION, label("undefinedValueSymbol"), tooltip("undefinedValueSymbol"),
                p -> p.getSvgParameters().getUndefinedValueSymbol(), (p, v) -> p.getSvgParameters().setUndefinedValueSymbol(v));
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
        addStringField(CAT_LOCALIZATION, label("activePowerUnit"), tooltip("activePowerUnit"),
                p -> p.getSvgParameters().getActivePowerUnit(), (p, v) -> p.getSvgParameters().setActivePowerUnit(v));
        addStringField(CAT_LOCALIZATION, label("reactivePowerUnit"), tooltip("reactivePowerUnit"),
                p -> p.getSvgParameters().getReactivePowerUnit(), (p, v) -> p.getSvgParameters().setReactivePowerUnit(v));
        addStringField(CAT_LOCALIZATION, label("currentUnit"), tooltip("currentUnit"),
                p -> p.getSvgParameters().getCurrentUnit(), (p, v) -> p.getSvgParameters().setCurrentUnit(v));
    }

    private void addBusFeederFields() {
        addDoubleField(CAT_BUS_FEEDER, label("busInfoMargin"), tooltip("busInfoMargin"),
                p -> p.getSvgParameters().getBusInfoMargin(), (p, v) -> p.getSvgParameters().setBusInfoMargin(v));
        addDoubleField(CAT_BUS_FEEDER, label("feederInfosIntraMargin"), tooltip("feederInfosIntraMargin"),
                p -> p.getSvgParameters().getFeederInfosIntraMargin(), (p, v) -> p.getSvgParameters().setFeederInfosIntraMargin(v));
        addDoubleField(CAT_BUS_FEEDER, label("feederInfosOuterMargin"), tooltip("feederInfosOuterMargin"),
                p -> p.getSvgParameters().getFeederInfosOuterMargin(), (p, v) -> p.getSvgParameters().setFeederInfosOuterMargin(v));
        addBooleanField(CAT_BUS_FEEDER, label("feederInfoSymmetry"), tooltip("feederInfoSymmetry"),
                p -> p.getSvgParameters().isFeederInfoSymmetry(), (p, v) -> p.getSvgParameters().setFeederInfoSymmetry(v));
        addBooleanField(CAT_BUS_FEEDER, label("busesLegendAdded"), tooltip("busesLegendAdded"),
                p -> p.getSvgParameters().isBusesLegendAdded(), (p, v) -> p.getSvgParameters().setBusesLegendAdded(v));
    }

    private void addLabelsFields() {
        addBooleanField(CAT_LABELS, label("useName"), tooltip("useName"),
                p -> p.getSvgParameters().isUseName(), (p, v) -> p.getSvgParameters().setUseName(v));
        addDoubleField(CAT_LABELS, label("angleLabelShift"), tooltip("angleLabelShift"),
                p -> p.getSvgParameters().getAngleLabelShift(), (p, v) -> p.getSvgParameters().setAngleLabelShift(v));
        addBooleanField(CAT_LABELS, label("labelCentered"), tooltip("labelCentered"),
                p -> p.getSvgParameters().isLabelCentered(), (p, v) -> p.getSvgParameters().setLabelCentered(v));
        addBooleanField(CAT_LABELS, label("labelDiagonal"), tooltip("labelDiagonal"),
                p -> p.getSvgParameters().isLabelDiagonal(), (p, v) -> p.getSvgParameters().setLabelDiagonal(v));
        addBooleanField(CAT_LABELS, label("busLabelDiagonal"), tooltip("busLabelDiagonal"),
                p -> p.getSvgParameters().isBusLabelDiagonal(), (p, v) -> p.getSvgParameters().setBusLabelDiagonal(v));
    }

    private void addInteractionFields() {
        addBooleanField(CAT_INTERACTION, label("tooltipEnabled"), tooltip("tooltipEnabled"),
                p -> p.getSvgParameters().isTooltipEnabled(), (p, v) -> p.getSvgParameters().setTooltipEnabled(v));
    }

    private void addSvgOutputFields() {
        addEnumField(CAT_SVG_OUTPUT, label("cssLocation"), tooltip("cssLocation"), SvgParameters.CssLocation.class,
                p -> p.getSvgParameters().getCssLocation(), (p, v) -> p.getSvgParameters().setCssLocation(v));
        addBooleanField(CAT_SVG_OUTPUT, label("avoidSvgComponentsDuplication"), tooltip("avoidSvgComponentsDuplication"),
                p -> p.getSvgParameters().isAvoidSVGComponentsDuplication(), (p, v) -> p.getSvgParameters().setAvoidSVGComponentsDuplication(v));
    }

    private void addDrawingStyleFields() {
        addBooleanField(CAT_DRAWING_STYLE, label("drawStraightWires"), tooltip("drawStraightWires"),
                p -> p.getSvgParameters().isDrawStraightWires(), (p, v) -> p.getSvgParameters().setDrawStraightWires(v));
        addBooleanField(CAT_DRAWING_STYLE, label("showGrid"), tooltip("showGrid"),
                p -> p.getSvgParameters().isShowGrid(), (p, v) -> p.getSvgParameters().setShowGrid(v));
        addBooleanField(CAT_DRAWING_STYLE, label("showInternalNodes"), tooltip("showInternalNodes"),
                p -> p.getSvgParameters().isShowInternalNodes(), (p, v) -> p.getSvgParameters().setShowInternalNodes(v));
        addBooleanField(CAT_DRAWING_STYLE, label("displayEquipmentNodesLabel"), tooltip("displayEquipmentNodesLabel"),
                p -> p.getSvgParameters().isDisplayEquipmentNodesLabel(), (p, v) -> p.getSvgParameters().setDisplayEquipmentNodesLabel(v));
        addBooleanField(CAT_DRAWING_STYLE, label("displayConnectivityNodesId"), tooltip("displayConnectivityNodesId"),
                p -> p.getSvgParameters().isDisplayConnectivityNodesId(), (p, v) -> p.getSvgParameters().setDisplayConnectivityNodesId(v));
        addBooleanField(CAT_DRAWING_STYLE, label("unifyVoltageLevelColors"), tooltip("unifyVoltageLevelColors"),
                p -> p.getSvgParameters().isUnifyVoltageLevelColors(), (p, v) -> p.getSvgParameters().setUnifyVoltageLevelColors(v));
    }

    private void addBusSpacingFields() {
        addDoubleField(CAT_BUS_SPACING, label("verticalSpaceBus"), tooltip("verticalSpaceBus"),
                p -> p.getLayoutParameters().getVerticalSpaceBus(), (p, v) -> p.getLayoutParameters().setVerticalSpaceBus(v));
        addDoubleField(CAT_BUS_SPACING, label("horizontalBusPadding"), tooltip("horizontalBusPadding"),
                p -> p.getLayoutParameters().getHorizontalBusPadding(), (p, v) -> p.getLayoutParameters().setHorizontalBusPadding(v));
    }

    private void addCellDimensionsFields() {
        addDoubleField(CAT_CELL_DIMENSIONS, label("cellWidth"), tooltip("cellWidth"),
                p -> p.getLayoutParameters().getCellWidth(), (p, v) -> p.getLayoutParameters().setCellWidth(v));
        addDoubleField(CAT_CELL_DIMENSIONS, label("externCellHeight"), tooltip("externCellHeight"),
                p -> p.getLayoutParameters().getExternCellHeight(), (p, v) -> p.getLayoutParameters().setExternCellHeight(v));
        addDoubleField(CAT_CELL_DIMENSIONS, label("internCellHeight"), tooltip("internCellHeight"),
                p -> p.getLayoutParameters().getInternCellHeight(), (p, v) -> p.getLayoutParameters().setInternCellHeight(v));
        addDoubleField(CAT_CELL_DIMENSIONS, label("stackHeight"), tooltip("stackHeight"),
                p -> p.getLayoutParameters().getStackHeight(), (p, v) -> p.getLayoutParameters().setStackHeight(v));
        addDoubleField(CAT_CELL_DIMENSIONS, label("minExternCellHeight"), tooltip("minExternCellHeight"),
                p -> p.getLayoutParameters().getMinExternCellHeight(), (p, v) -> p.getLayoutParameters().setMinExternCellHeight(v));
        addBooleanField(CAT_CELL_DIMENSIONS, label("adaptCellHeightToContent"), tooltip("adaptCellHeightToContent"),
                p -> p.getLayoutParameters().isAdaptCellHeightToContent(), (p, v) -> p.getLayoutParameters().setAdaptCellHeightToContent(v));
    }

    private void addSnakeLinesFields() {
        addDoubleField(CAT_SNAKE_LINES, label("horizontalSnakeLinePadding"), tooltip("horizontalSnakeLinePadding"),
                p -> p.getLayoutParameters().getHorizontalSnakeLinePadding(), (p, v) -> p.getLayoutParameters().setHorizontalSnakeLinePadding(v));
        addDoubleField(CAT_SNAKE_LINES, label("verticalSnakeLinePadding"), tooltip("verticalSnakeLinePadding"),
                p -> p.getLayoutParameters().getVerticalSnakeLinePadding(), (p, v) -> p.getLayoutParameters().setVerticalSnakeLinePadding(v));
        addDoubleField(CAT_SNAKE_LINES, label("spaceForFeederInfos"), tooltip("spaceForFeederInfos"),
                p -> p.getLayoutParameters().getSpaceForFeederInfos(), (p, v) -> p.getLayoutParameters().setSpaceForFeederInfos(v));
        addIntField(CAT_SNAKE_LINES, label("zoneLayoutSnakeLinePadding"), tooltip("zoneLayoutSnakeLinePadding"),
                p -> p.getLayoutParameters().getZoneLayoutSnakeLinePadding(), (p, v) -> p.getLayoutParameters().setZoneLayoutSnakeLinePadding(v));
    }

    private void addComponentSizingFields() {
        addDoubleField(CAT_COMPONENT_SIZING, label("maxComponentHeight"), tooltip("maxComponentHeight"),
                p -> p.getLayoutParameters().getMaxComponentHeight(), (p, v) -> p.getLayoutParameters().setMaxComponentHeight(v));
        addDoubleField(CAT_COMPONENT_SIZING, label("minSpaceBetweenComponents"), tooltip("minSpaceBetweenComponents"),
                p -> p.getLayoutParameters().getMinSpaceBetweenComponents(), (p, v) -> p.getLayoutParameters().setMinSpaceBetweenComponents(v));
    }

    private void addPaddingFields() {
        addPaddingField(CAT_PADDING, label("voltageLevelPadding"), tooltip("voltageLevelPadding"),
                p -> p.getLayoutParameters().getVoltageLevelPadding(),
                (p, l, t, r, b) -> p.getLayoutParameters().setVoltageLevelPadding(l, t, r, b));
        addPaddingField(CAT_PADDING, label("diagramPadding"), tooltip("diagramPadding"),
                p -> p.getLayoutParameters().getDiagramPadding(),
                (p, l, t, r, b) -> p.getLayoutParameters().setDiagrammPadding(l, t, r, b));
    }

    @FunctionalInterface
    private interface PaddingSetter {
        void set(SldParameters parameters, double left, double top, double right, double bottom);
    }

    // SLD's LayoutParameters.Padding is an immutable record (left()/top()/right()/bottom()), and both
    // padding setters take four raw doubles rather than a Padding instance - four fields in one row.
    private void addPaddingField(String category, String labelText, String tooltipText,
                                  Function<SldParameters, LayoutParameters.Padding> getter, PaddingSetter setter) {
        TextField leftField = new TextField();
        TextField topField = new TextField();
        TextField rightField = new TextField();
        TextField bottomField = new TextField();
        refreshers.add(() -> {
            LayoutParameters.Padding padding = getter.apply(parametersProperty.getValue());
            leftField.setText(Double.toString(padding.left()));
            topField.setText(Double.toString(padding.top()));
            rightField.setText(Double.toString(padding.right()));
            bottomField.setText(Double.toString(padding.bottom()));
        });
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                setter.set(parametersProperty.getValue(), Double.parseDouble(leftField.getText()),
                        Double.parseDouble(topField.getText()), Double.parseDouble(rightField.getText()),
                        Double.parseDouble(bottomField.getText()));
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
                new Label(Messages.get("parameters.diagram.padding.left")), leftField,
                new Label(Messages.get("parameters.diagram.padding.top")), topField,
                new Label(Messages.get("parameters.diagram.padding.right")), rightField,
                new Label(Messages.get("parameters.diagram.padding.bottom")), bottomField);
        box.setAlignment(Pos.CENTER_LEFT);
        addRow(category, labelText, tooltipText, box);
    }

    private void addAlignmentTopologyFields() {
        addEnumField(CAT_ALIGNMENT_TOPOLOGY, label("busbarsAlignment"), tooltip("busbarsAlignment"), LayoutParameters.Alignment.class,
                p -> p.getLayoutParameters().getBusbarsAlignment(), (p, v) -> p.getLayoutParameters().setBusbarsAlignment(v));
        addStringListField(CAT_ALIGNMENT_TOPOLOGY, label("componentsOnBusbars"), tooltip("componentsOnBusbars"),
                p -> p.getLayoutParameters().getComponentsOnBusbars(), (p, v) -> p.getLayoutParameters().setComponentsOnBusbars(v));
        addBooleanField(CAT_ALIGNMENT_TOPOLOGY, label("removeFictitiousSwitchNodes"), tooltip("removeFictitiousSwitchNodes"),
                p -> p.getLayoutParameters().isRemoveFictitiousSwitchNodes(), (p, v) -> p.getLayoutParameters().setRemoveFictitiousSwitchNodes(v));
        addBooleanField(CAT_ALIGNMENT_TOPOLOGY, label("displayTeePointsInVoltageLevels"), tooltip("displayTeePointsInVoltageLevels"),
                p -> p.getLayoutParameters().isDisplayTeePointsInVoltageLevels(), (p, v) -> p.getLayoutParameters().setDisplayTeePointsInVoltageLevels(v));
    }

    private static String label(String param) {
        return Messages.get("parameters.sld.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("parameters.sld.param." + param + ".tooltip");
    }
}
