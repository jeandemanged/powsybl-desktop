/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.map.MapBaseVoltages;
import com.powsybl.powsybldesktop.map.MapController;
import com.powsybl.powsybldesktop.report.ReportNodeTreeCell;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Parameters-view tab form for {@link GuiParameters}, the settings the map, logs and reports views also edit from
 * their own toolbars - see {@link AbstractDiagramParametersController} for the shared shape.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GuiParametersController extends AbstractDiagramParametersController<GuiParameters> {

    private static final String CAT_MAP = "map";
    private static final String CAT_LOGS = "logs";
    private static final String CAT_REPORTS = "reports";

    private final MapBaseVoltages mapBaseVoltages = new MapBaseVoltages();

    @FXML
    private void initialize() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(CAT_MAP, Messages.get("main.toolbar.map"));
        titles.put(CAT_LOGS, Messages.get("main.toolbar.logs"));
        titles.put(CAT_REPORTS, Messages.get("main.toolbar.reports"));
        buildCategoryList(titles);
    }

    public void setParametersModel(ParametersModel parametersModel) {
        setParametersProperty(parametersModel.guiParametersProperty());
        setOnChange(parametersModel::guiParametersChanged);
        // edited from the map, logs and reports views too
        listenerManager.listen(parametersModel.guiParametersRevisionProperty(), (observable, oldValue, newValue) -> refresh());
    }

    @Override
    protected void buildFields() {
        addChoiceField(CAT_MAP, label("basemap"), List.of(MapController.Basemap.values()), MapController.Basemap::getLabel,
                GuiParameters::getMapBasemap, GuiParameters::setMapBasemap);
        addBaseVoltagesField();
        addChoiceField(CAT_LOGS, label("logsMinLevel"), List.of(GuiParameters.LogLevel.values()),
                level -> Messages.get("logs.filter." + level.name().toLowerCase(Locale.ROOT)),
                GuiParameters::getLogsMinLevel, GuiParameters::setLogsMinLevel);
        addChoiceField(CAT_LOGS, label("logsOrder"), List.of(true, false),
                newestOnTop -> Messages.get(newestOnTop ? "logs.order.newestOnTop" : "logs.order.newestOnBottom"),
                GuiParameters::isLogsNewestOnTop, GuiParameters::setLogsNewestOnTop);
        addChoiceField(CAT_REPORTS, label("reportsMinSeverity"), ReportNodeTreeCell.SEVERITIES,
                severity -> Messages.get("reports.filter." + severity.toLowerCase(Locale.ROOT)),
                GuiParameters::getReportsMinSeverity, GuiParameters::setReportsMinSeverity);
    }

    private <V> void addChoiceField(String category, String label, List<V> values, Function<V, String> toLabel,
                                    Function<GuiParameters, V> getter, BiConsumer<GuiParameters, V> setter) {
        ChoiceBox<V> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(values));
        choiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(V value) {
                return value == null ? "" : toLabel.apply(value);
            }

            @Override
            public V fromString(String string) {
                return null;
            }
        });
        refreshers.add(() -> choiceBox.setValue(getter.apply(parametersProperty.getValue())));
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                setter.accept(parametersProperty.getValue(), newValue);
                notifyChange();
            }
        });
        addRow(category, label, null, choiceBox);
    }

    private void addBaseVoltagesField() {
        MapBaseVoltages.CheckBoxes checkBoxes = mapBaseVoltages.createCheckBoxes(hidden -> {
            parametersProperty.getValue().setMapHiddenBaseVoltages(hidden);
            notifyChange();
        });
        refreshers.add(() -> checkBoxes.setHidden(parametersProperty.getValue().getMapHiddenBaseVoltages()));
        Hyperlink checkAllLink = new Hyperlink(Messages.get("map.baseVoltages.checkAll"));
        checkAllLink.setOnAction(event -> checkBoxes.setAllSelected(true));
        Hyperlink checkNoneLink = new Hyperlink(Messages.get("map.baseVoltages.checkNone"));
        checkNoneLink.setOnAction(event -> checkBoxes.setAllSelected(false));
        VBox box = new VBox(4, new HBox(4, checkAllLink, checkNoneLink));
        box.getChildren().addAll(checkBoxes.getCheckBoxes());
        addRow(CAT_MAP, Messages.get("map.baseVoltages"), null, box);
    }

    private static String label(String param) {
        return Messages.get("parameters.gui.param." + param + ".label");
    }
}
