/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.powsybldesktop.network.edit.PositionEdits;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Editor of a line's or boundary line's route ({@code LinePosition}), an immutable list of coordinates: points added,
 * removed, moved or changed are staged here and written by Apply (or dropped by Revert), as in
 * {@link ReactiveLimitsPane}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class CoordinatesPane extends VBox {

    private static final int VISIBLE_ROWS = 8;

    // mutable, edited in place by the table
    private static final class PointRow {
        private double latitude;
        private double longitude;

        private PointRow(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private final Identifiable<?> line;
    private final DetailForm form;
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final TableView<PointRow> table = new TableView<>();
    private final Button applyButton = new Button(Messages.get("detail.action.apply"));
    private final StringConverter<Double> format;

    CoordinatesPane(Identifiable<?> line, DetailForm form) {
        super(4);
        this.line = Objects.requireNonNull(line);
        this.form = Objects.requireNonNull(form);
        format = TableColumnSupport.doubleFormat(form.getGuiParameters(), GuiParameters.Quantity.COORDINATE);
        BooleanProperty editable = form.editableProperty();

        TableColumn<PointRow, Integer> indexColumn = new TableColumn<>("#");
        indexColumn.setSortable(false);
        indexColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(table.getItems().indexOf(cellData.getValue()) + 1));
        indexColumn.setPrefWidth(40);
        table.getColumns().setAll(List.of(indexColumn,
                column("detail.extension.substationPosition.latitude", row -> row.latitude, (row, value) -> row.latitude = value),
                column("detail.extension.substationPosition.longitude", row -> row.longitude, (row, value) -> row.longitude = value)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(Messages.get("detail.extension.linePosition.noPoints")));
        table.editableProperty().bind(editable);
        DetailTables.fitHeight(table, VISIBLE_ROWS);

        ReadOnlyIntegerProperty selected = table.getSelectionModel().selectedIndexProperty();
        Button addButton = button("detail.extension.linePosition.add", editable.not(), () -> add(table.getItems().size()));
        Button insertButton = button("detail.extension.linePosition.insert", editable.not().or(selected.lessThan(0)),
                () -> add(table.getSelectionModel().getSelectedIndex()));
        Button removeButton = button("desktop.common.action.remove", editable.not().or(selected.lessThan(0)), () -> {
            table.getItems().remove(table.getSelectionModel().getSelectedIndex());
            staged();
        });
        Button upButton = button("detail.extension.linePosition.up", editable.not().or(selected.lessThan(1)), () -> move(-1));
        Button downButton = button("detail.extension.linePosition.down", editable.not().or(selected.lessThan(0))
                .or(selected.greaterThanOrEqualTo(Bindings.size(table.getItems()).subtract(1))), () -> move(1));
        // not disabled without straight route: the substations' positions may change meanwhile, an error explains instead
        Button straightButton = button("detail.extension.linePosition.straight", editable.not(), null);
        straightButton.setOnAction(_ -> straight(straightButton));
        applyButton.disableProperty().bind(editable.not().or(dirty.not()));
        applyButton.setOnAction(_ -> apply());
        Button revertButton = button("detail.action.revert", dirty.not(), this::load);

        FlowPane pointButtons = new FlowPane(4, 4, addButton, insertButton, removeButton, upButton, downButton, straightButton);
        FlowPane applyButtons = new FlowPane(4, 4, applyButton, revertButton);
        getChildren().addAll(table, pointButtons, applyButtons);
        load();
    }

    /**
     * Re-reads the route unless edits are staged.
     */
    void refresh() {
        if (!dirty.get()) {
            load();
        }
    }

    private void load() {
        table.getItems().setAll(PositionEdits.route(line).stream()
                .map(coordinate -> new PointRow(coordinate.getLatitude(), coordinate.getLongitude()))
                .toList());
        dirty.set(false);
    }

    // a copy of the neighbouring point, or the middle of the two it goes between, for the user to adjust
    private void add(int index) {
        List<PointRow> rows = table.getItems();
        PointRow row;
        if (rows.isEmpty()) {
            row = new PointRow(0, 0);
        } else if (index == 0 || index >= rows.size()) {
            PointRow neighbour = index == 0 ? rows.getFirst() : rows.getLast();
            row = new PointRow(neighbour.latitude, neighbour.longitude);
        } else {
            PointRow before = rows.get(index - 1);
            PointRow after = rows.get(index);
            row = new PointRow((before.latitude + after.latitude) / 2, (before.longitude + after.longitude) / 2);
        }
        rows.add(index, row);
        table.getSelectionModel().select(index);
        staged();
    }

    private void move(int delta) {
        int index = table.getSelectionModel().getSelectedIndex();
        Collections.swap(table.getItems(), index, index + delta);
        table.getSelectionModel().select(index + delta);
        staged();
    }

    private void straight(Button button) {
        PositionEdits.straightRoute(line).ifPresentOrElse(route -> {
            table.getItems().setAll(route.stream().map(coordinate -> new PointRow(coordinate.getLatitude(), coordinate.getLongitude())).toList());
            staged();
        }, () -> TableColumnSupport.notifyEditError(button, new PowsyblException(Messages.get("edit.position.noStraightRoute"))));
    }

    private void staged() {
        dirty.set(true);
        table.refresh();
    }

    private void apply() {
        List<Coordinate> route = table.getItems().stream().map(row -> new Coordinate(row.latitude, row.longitude)).toList();
        if (form.apply(applyButton, () -> PositionEdits.setRoute(line, route))) {
            load();
        }
    }

    private static Button button(String key, ObservableBooleanValue disabled, Runnable action) {
        Button button = new Button(Messages.get(key));
        button.disableProperty().bind(disabled);
        if (action != null) {
            button.setOnAction(_ -> action.run());
        }
        return button;
    }

    private TableColumn<PointRow, Double> column(String titleKey, Function<PointRow, Double> getter, BiConsumer<PointRow, Double> setter) {
        TableColumn<PointRow, Double> column = DetailTables.editableColumn(titleKey, format, getter, setter, this::staged);
        column.getStyleClass().add("right-aligned-column");
        return column;
    }
}
