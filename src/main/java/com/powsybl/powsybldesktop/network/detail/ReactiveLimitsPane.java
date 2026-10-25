/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveCapabilityCurveAdder;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.iidm.network.ReactiveLimitsHolder;
import com.powsybl.iidm.network.ReactiveLimitsKind;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Editor of an equipment's reactive limits, min/max or capability curve, with a {@link CapabilityChart} preview. IIDM's
 * reactive limits are immutable, replaced as a whole: edits (kind, min/max Q, curve points added, removed or changed)
 * are staged here, previewed, and written by Apply (or dropped by Revert), so that no intermediate state (e.g. two
 * points at the same P while typing) reaches the network. Reactive capability shapes are shown but not editable,
 * though they can be replaced by another kind. Shared by every reactive limits holder's detail view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ReactiveLimitsPane extends SplitPane {

    // mutable, edited in place by the points table
    private static final class PointRow {
        private double p;
        private double minQ;
        private double maxQ;

        private PointRow(double p, double minQ, double maxQ) {
            this.p = p;
            this.minQ = minQ;
            this.maxQ = maxQ;
        }

        private CapabilityChart.Point point() {
            return new CapabilityChart.Point(p, minQ, maxQ);
        }
    }

    /**
     * The target and solved operating points to show, in generator convention.
     */
    public record OperatingPoints(double targetP, double targetQ, double solvedP, double solvedQ) {
    }

    private final ReactiveLimitsHolder holder;
    private final DoubleSupplier minP;
    private final DoubleSupplier maxP;
    private final Supplier<OperatingPoints> operatingPoints;
    private final Runnable onEdit;
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    private final ChoiceBox<ReactiveLimitsKind> kindChoiceBox = new ChoiceBox<>();
    private final TextField minQField = new TextField();
    private final TextField maxQField = new TextField();
    private final GridPane minMaxPane = new GridPane();
    private final TableView<PointRow> pointsTable = new TableView<>();
    private final VBox curvePane;
    private final Label shapeLabel = new Label(Messages.get("detail.reactiveLimits.shapeNotEditable"));
    private final Button applyButton = new Button(Messages.get("detail.action.apply"));
    private final CapabilityChart chart = new CapabilityChart();
    private final StringConverter<Double> format;

    private ReactiveLimitsKind kind;
    private double minQ;
    private double maxQ;
    private boolean loading;

    /**
     * @param minP for a min/max limits outline, and to seed a new curve
     */
    public ReactiveLimitsPane(ReactiveLimitsHolder holder, DoubleSupplier minP, DoubleSupplier maxP,
                              Supplier<OperatingPoints> operatingPoints, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        this.holder = Objects.requireNonNull(holder);
        this.minP = Objects.requireNonNull(minP);
        this.maxP = Objects.requireNonNull(maxP);
        this.operatingPoints = Objects.requireNonNull(operatingPoints);
        this.onEdit = Objects.requireNonNull(onEdit);
        format = TableColumnSupport.lenient(TableColumnSupport.doubleFormat(guiParameters, GuiParameters.Quantity.POWER));

        kindChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ReactiveLimitsKind value) {
                return value == null ? "" : Messages.get(switch (value) {
                    case MIN_MAX -> "detail.reactiveLimits.kind.minMax";
                    case CURVE -> "detail.reactiveLimits.kind.curve";
                    case SHAPE -> "detail.reactiveLimits.kind.shape";
                });
            }

            @Override
            public ReactiveLimitsKind fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        kindChoiceBox.disableProperty().bind(editable.not());
        kindChoiceBox.valueProperty().addListener((observable, oldKind, newKind) -> {
            if (!loading && newKind != null && newKind != kind) {
                changeKind(newKind);
            }
        });
        HBox kindBox = new HBox(8, new Label(Messages.get("detail.reactiveLimits.kind")), kindChoiceBox);
        kindBox.setAlignment(Pos.CENTER_LEFT);

        minMaxPane.setHgap(8);
        minMaxPane.setVgap(4);
        minMaxPane.addRow(0, new Label(Messages.get("detail.reactiveLimits.minQ")), minQField, new Label(DetailForm.MVAR));
        minMaxPane.addRow(1, new Label(Messages.get("detail.reactiveLimits.maxQ")), maxQField, new Label(DetailForm.MVAR));
        stageOnCommit(minQField, value -> minQ = value);
        stageOnCommit(maxQField, value -> maxQ = value);

        pointsTable.getColumns().setAll(List.of(
                pointColumn("detail.reactiveLimits.p", row -> row.p, (row, value) -> row.p = value),
                pointColumn("detail.reactiveLimits.minQ", row -> row.minQ, (row, value) -> row.minQ = value),
                pointColumn("detail.reactiveLimits.maxQ", row -> row.maxQ, (row, value) -> row.maxQ = value)));
        pointsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        pointsTable.setPlaceholder(new Label(Messages.get("detail.reactiveLimits.noPoints")));
        pointsTable.editableProperty().bind(editable);
        VBox.setVgrow(pointsTable, Priority.ALWAYS);
        Button addPointButton = new Button(Messages.get("detail.reactiveLimits.addPoint"));
        addPointButton.disableProperty().bind(editable.not());
        addPointButton.setOnAction(event -> addPoint());
        Button removePointButton = new Button(Messages.get("detail.reactiveLimits.removePoint"));
        removePointButton.disableProperty().bind(editable.not().or(pointsTable.getSelectionModel().selectedItemProperty().isNull()));
        removePointButton.setOnAction(event -> {
            pointsTable.getItems().remove(pointsTable.getSelectionModel().getSelectedItem());
            staged();
        });
        curvePane = new VBox(4, pointsTable, new HBox(4, addPointButton, removePointButton));
        VBox.setVgrow(curvePane, Priority.ALWAYS);

        applyButton.disableProperty().bind(editable.not().or(dirty.not()).or(kindChoiceBox.valueProperty().isEqualTo(ReactiveLimitsKind.SHAPE)));
        applyButton.setOnAction(event -> apply());
        Button revertButton = new Button(Messages.get("detail.action.revert"));
        revertButton.disableProperty().bind(dirty.not());
        revertButton.setOnAction(event -> load());
        HBox buttons = new HBox(4, applyButton, revertButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox editor = new VBox(8, kindBox, minMaxPane, curvePane, shapeLabel, buttons);
        editor.setPadding(new Insets(8));
        editor.setMinWidth(320);
        BorderPane chartPane = new BorderPane(chart);
        chartPane.setPadding(new Insets(8));
        getItems().setAll(editor, chartPane);
        setDividerPositions(0.4);
        load();
    }

    public BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Re-reads the limits unless edits are staged, and the operating points.
     */
    public void refresh() {
        if (!dirty.get()) {
            load();
        } else {
            updateChart();
        }
    }

    private void load() {
        ReactiveLimits limits = holder.getReactiveLimits();
        kind = limits == null ? ReactiveLimitsKind.MIN_MAX : limits.getKind();
        minQ = limits instanceof MinMaxReactiveLimits minMax ? minMax.getMinQ() : Double.NaN;
        maxQ = limits instanceof MinMaxReactiveLimits minMax ? minMax.getMaxQ() : Double.NaN;
        List<PointRow> rows = new ArrayList<>();
        if (limits instanceof ReactiveCapabilityCurve curve) {
            curve.getPoints().forEach(point -> rows.add(new PointRow(point.getP(), point.getMinQ(), point.getMaxQ())));
        }
        pointsTable.getItems().setAll(rows);
        dirty.set(false);
        show();
    }

    private void changeKind(ReactiveLimitsKind newKind) {
        if (newKind == ReactiveLimitsKind.CURVE && pointsTable.getItems().isEmpty()) {
            CapabilityChart.rectangle(minQ, maxQ, minP.getAsDouble(), maxP.getAsDouble())
                    .forEach(point -> pointsTable.getItems().add(new PointRow(point.p(), point.minQ(), point.maxQ())));
        } else if (newKind == ReactiveLimitsKind.MIN_MAX && kind == ReactiveLimitsKind.CURVE && !pointsTable.getItems().isEmpty()) {
            minQ = pointsTable.getItems().stream().mapToDouble(row -> row.minQ).min().orElse(Double.NaN);
            maxQ = pointsTable.getItems().stream().mapToDouble(row -> row.maxQ).max().orElse(Double.NaN);
        }
        kind = newKind;
        staged();
    }

    private void addPoint() {
        List<PointRow> rows = pointsTable.getItems();
        PointRow last = rows.isEmpty() ? null : rows.stream().max(Comparator.comparingDouble(row -> row.p)).orElseThrow();
        PointRow row = last == null ? new PointRow(0, 0, 0) : new PointRow(last.p + 1, last.minQ, last.maxQ);
        rows.add(row);
        pointsTable.getSelectionModel().select(row);
        staged();
    }

    private void staged() {
        dirty.set(true);
        show();
    }

    private void show() {
        loading = true;
        if (kind != ReactiveLimitsKind.SHAPE) {
            kindChoiceBox.getItems().setAll(ReactiveLimitsKind.MIN_MAX, ReactiveLimitsKind.CURVE);
        } else {
            kindChoiceBox.getItems().setAll(ReactiveLimitsKind.values());
        }
        kindChoiceBox.setValue(kind);
        loading = false;
        setVisible(minMaxPane, kind == ReactiveLimitsKind.MIN_MAX);
        setVisible(curvePane, kind == ReactiveLimitsKind.CURVE);
        setVisible(shapeLabel, kind == ReactiveLimitsKind.SHAPE);
        minQField.setText(format.toString(minQ));
        maxQField.setText(format.toString(maxQ));
        pointsTable.refresh();
        updateChart();
    }

    private void updateChart() {
        chart.setLimits(switch (kind) {
            case MIN_MAX -> CapabilityChart.rectangle(minQ, maxQ, minP.getAsDouble(), maxP.getAsDouble());
            case CURVE -> pointsTable.getItems().stream().map(PointRow::point).toList();
            case SHAPE -> List.of();
        });
        OperatingPoints points = operatingPoints.get();
        chart.setOperatingPoints(points.targetP(), points.targetQ(), points.solvedP(), points.solvedQ());
    }

    private void apply() {
        if (DetailForm.apply(applyButton, () -> {
            if (kind == ReactiveLimitsKind.MIN_MAX) {
                holder.newMinMaxReactiveLimits().setMinQ(minQ).setMaxQ(maxQ).add();
            } else {
                ReactiveCapabilityCurveAdder adder = holder.newReactiveCapabilityCurve();
                pointsTable.getItems().forEach(row -> adder.beginPoint().setP(row.p).setMinQ(row.minQ).setMaxQ(row.maxQ).endPoint());
                adder.add();
            }
        }, onEdit)) {
            load();
        }
    }

    private void stageOnCommit(TextField field, DoubleConsumer stage) {
        field.disableProperty().bind(editable.not());
        Runnable commit = () -> {
            if (field.getText().equals(format.toString(field == minQField ? minQ : maxQ))) {
                return;
            }
            Double value = format.fromString(field.getText());
            if (value == null) {
                TableColumnSupport.notifyEditError(field, new PowsyblException(
                        Messages.get("detail.editError.invalidNumber", field.getText().trim())));
                show();
            } else {
                stage.accept(value);
                staged();
            }
        };
        field.setOnAction(event -> commit.run());
        field.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) {
                commit.run();
            }
        });
    }

    private TableColumn<PointRow, Double> pointColumn(String titleKey, Function<PointRow, Double> getter,
                                                      BiConsumer<PointRow, Double> setter) {
        TableColumn<PointRow, Double> column = new TableColumn<>(Messages.get(titleKey));
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> {
            TableCell<PointRow, Double> cell = new TextFieldTableCell<>(format);
            cell.getStyleClass().add("editable-cell");
            return cell;
        });
        column.getStyleClass().add("right-aligned-column");
        column.setSortable(false);
        column.setOnEditCommit(event -> {
            if (event.getNewValue() != null) {
                setter.accept(event.getRowValue(), event.getNewValue());
                staged();
            } else {
                pointsTable.refresh();
            }
        });
        return column;
    }

    private static void setVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
