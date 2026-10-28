/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.AreaBoundary;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.network.edit.CreationForm;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.List;

/**
 * Area detail: type, interchange target and the interchanges of the last load flow, then tabs listing its voltage levels
 * and its boundaries (terminals or boundary line boundaries), with their AC or DC kind and flows, each list editable.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class AreaDetailController extends AbstractEquipmentDetailController<Area> {

    private static final StringConverter<String> TEXT = DetailTables.TEXT_FORMAT;

    // an area spans many voltage levels: none is linked in the header, all being listed in their tab
    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of();
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SINGLE_LINE;
    }

    @Override
    protected void buildMain(DetailForm form) {
        form.section("detail.section.characteristics")
                .text("edit.area.type", equipment::getAreaType)
                .doubleField("edit.area.interchangeTarget", DetailForm.MW, GuiParameters.Quantity.POWER,
                        () -> equipment.getInterchangeTarget().orElse(Double.NaN), equipment::setInterchangeTarget)
                .section("desktop.common.columnGroup.solvedValues")
                .value("areas.column.acInterchange", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getAcInterchange)
                .value("areas.column.dcInterchange", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getDcInterchange)
                .value("areas.column.interchange", DetailForm.MW, GuiParameters.Quantity.POWER, equipment::getInterchange);
    }

    @Override
    protected List<Tab> extraTabs() {
        return List.of(new Tab(Messages.get("desktop.common.equipment.voltageLevel.plural"), register(new VoltageLevelsPane())),
                new Tab(Messages.get("areas.column.boundaries"), register(new BoundariesPane())));
    }

    @Override
    protected List<ExtensionsPane.Editor<Area>> extensionEditors() {
        return ExtensionEditors.forIdentifiable();
    }

    // a table and the buttons editing its rows, disabled while the network is busy
    private abstract class AbstractListPane<R> extends VBox implements DetailPart {
        protected final BooleanProperty editable = new SimpleBooleanProperty(true);
        protected final TableView<R> table = new TableView<>();
        protected final HBox buttons = new HBox(6);

        AbstractListPane() {
            super(8);
            setPadding(new Insets(8));
            buttons.setAlignment(Pos.CENTER_LEFT);
            VBox.setVgrow(table, Priority.ALWAYS);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            getChildren().addAll(buttons, table);
        }

        @Override
        public BooleanProperty editableProperty() {
            return editable;
        }

        protected Button button(String textKey, String icon, Runnable action) {
            Button button = new Button(Messages.get(textKey), new FontIcon(icon));
            button.disableProperty().bind(editable.not());
            button.setOnAction(event -> DetailForm.apply(button, action, onEdit()));
            return button;
        }
    }

    private final class VoltageLevelsPane extends AbstractListPane<VoltageLevel> {
        private final ComboBox<VoltageLevel> candidates = new ComboBox<>();

        private VoltageLevelsPane() {
            table.getColumns().add(DetailTables.readOnlyColumn("desktop.common.column.name", TEXT, VoltageLevel::getNameOrId));
            table.getColumns().add(DetailTables.readOnlyColumn("desktop.common.column.id", TEXT, VoltageLevel::getId));
            candidates.setConverter(CreationForm.identifiableFormat());
            candidates.disableProperty().bind(editable.not());
            Button add = button("desktop.common.action.add", "mdi2p-plus", () -> {
                if (candidates.getValue() != null) {
                    equipment.addVoltageLevel(candidates.getValue());
                }
            });
            Button remove = button("desktop.common.action.remove", "mdi2d-delete", () -> {
                VoltageLevel selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    equipment.removeVoltageLevel(selected);
                }
            });
            buttons.getChildren().addAll(candidates, add, remove);
        }

        @Override
        public void refresh() {
            table.getItems().setAll(equipment.getVoltageLevelStream().sorted(Comparator.comparing(VoltageLevel::getNameOrId)).toList());
            candidates.getItems().setAll(equipment.getNetwork().getVoltageLevelStream()
                    .filter(voltageLevel -> !table.getItems().contains(voltageLevel))
                    .sorted(Comparator.comparing(VoltageLevel::getNameOrId)).toList());
        }
    }

    private final class BoundariesPane extends AbstractListPane<AreaBoundary> {
        private BoundariesPane() {
            table.getColumns().add(DetailTables.readOnlyColumn("areas.column.boundary", TEXT, AreaDetailController::describe));
            table.getColumns().add(DetailTables.readOnlyColumn("areas.column.ac", TEXT,
                    boundary -> Messages.get(boundary.isAc() ? "areas.boundary.ac" : "areas.boundary.dc")));
            table.getColumns().add(DetailTables.readOnlyColumn("desktop.common.column.p", doubleFormat(), AreaBoundary::getP));
            table.getColumns().add(DetailTables.readOnlyColumn("desktop.common.column.q", doubleFormat(), AreaBoundary::getQ));
            CheckBox ac = new CheckBox(Messages.get("areas.column.ac"));
            ac.setSelected(true);
            ac.disableProperty().bind(editable.not());
            Button add = new Button(Messages.get("desktop.common.action.add"), new FontIcon("mdi2p-plus"));
            add.disableProperty().bind(editable.not());
            add.setOnAction(event -> TerminalPickerDialog.show(add.getScene().getWindow(), sceneModel)
                    .ifPresent(terminal -> DetailForm.apply(add, () -> equipment.newAreaBoundary().setTerminal(terminal).setAc(ac.isSelected()).add(), onEdit())));
            Button remove = button("desktop.common.action.remove", "mdi2d-delete", () -> {
                AreaBoundary selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selected.getTerminal().ifPresentOrElse(equipment::removeAreaBoundary,
                            () -> selected.getBoundary().ifPresent(equipment::removeAreaBoundary));
                }
            });
            buttons.getChildren().addAll(ac, add, remove);
        }

        private StringConverter<Double> doubleFormat() {
            return new StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return value == null || value.isNaN() ? "-" : guiParameters().get().format(GuiParameters.Quantity.POWER, value);
                }

                @Override
                public Double fromString(String string) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void refresh() {
            table.getItems().setAll(equipment.getAreaBoundaryStream().toList());
        }
    }

    private static String describe(AreaBoundary boundary) {
        return boundary.getTerminal()
                .map(terminal -> terminal.getConnectable().getNameOrId() + (terminal.getSide() == null ? "" : " (" + terminal.getSide() + ")"))
                .orElseGet(() -> boundary.getBoundary().map(b -> b.getBoundaryLine().getNameOrId()).orElse("-"));
    }
}
