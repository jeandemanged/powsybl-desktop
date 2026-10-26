/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.limits;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.FlowsLimitsHolder;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.LoadingLimitsAdder;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.network.detail.DetailPart;
import com.powsybl.powsybldesktop.network.detail.DetailTables;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Detail of an equipment's flow limits, for each side: its operational limits groups, and in each group its current,
 * active power and apparent power limits, with the side's present value. Supports lines, tie lines, two- and
 * three-winding transformers and boundary lines.
 * <p>
 * Read-only ({@link #OperationalLimitsPane(GuiParameters)}, used by the violations popup), it shows the selected
 * groups only, highlighting the groups and limits crossed by the given violations. Editable ({@link #editable}, used
 * by the equipment detail views), it shows every group: groups are added, removed, selected and deselected, limit sets
 * added and removed, permanent and temporary limits edited in place, temporary limits added and removed. IIDM's limit
 * values are mutable, but a temporary limit's name, duration and fictitious flag aren't: changing one, or adding or
 * removing a temporary limit, replaces the whole limit set (keeping its temporary limits' properties).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class OperationalLimitsPane extends VBox implements DetailPart {

    private static final List<LimitType> FLOW_LIMIT_TYPES = List.of(LimitType.CURRENT, LimitType.ACTIVE_POWER, LimitType.APPARENT_POWER);
    private static final double CELL_SIZE = 24;
    private static final double LIMIT_TOLERANCE = 1e-9;
    // the acceptable duration of a temporary limit added from this pane, halved until free
    private static final int NEW_TEMPORARY_LIMIT_DURATION = 1200;

    private static final StringConverter<Integer> DURATION_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Integer duration) {
            return duration == null ? Messages.get("limits.permanent") : String.valueOf(duration);
        }

        @Override
        public Integer fromString(String text) {
            return DetailTables.INTEGER_FORMAT.fromString(text);
        }
    };

    private final Supplier<GuiParameters> guiParameters;
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    // editable mode only
    private Identifiable<?> equipment;
    private Runnable onEdit;
    private String shownStructure;
    private final List<Runnable> tableRefreshers = new ArrayList<>();

    /**
     * A side's limits groups, through {@link Branch}'s per-side or {@link FlowsLimitsHolder}'s methods.
     */
    private record Side(ThreeSides side, Terminal terminal, Supplier<Collection<OperationalLimitsGroup>> groups,
                        Supplier<List<OperationalLimitsGroup>> selectedGroups, Consumer<String> newGroup, Consumer<String> removeGroup,
                        Consumer<String> select, Consumer<String> deselect) {
    }

    /**
     * A permanent (null acceptable duration) or temporary limit.
     */
    record LimitRow(String name, double value, Integer acceptableDuration, boolean fictitious) {
    }

    public OperationalLimitsPane(GuiParameters guiParameters) {
        super(8);
        Objects.requireNonNull(guiParameters);
        this.guiParameters = () -> guiParameters;
    }

    private OperationalLimitsPane(Identifiable<?> equipment, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        super(8);
        this.guiParameters = Objects.requireNonNull(guiParameters);
        this.equipment = Objects.requireNonNull(equipment);
        this.onEdit = Objects.requireNonNull(onEdit);
        refresh();
    }

    /**
     * An editor of {@code equipment}'s limits.
     *
     * @param onEdit run after every applied edit
     */
    public static OperationalLimitsPane editable(Identifiable<?> equipment, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        return new OperationalLimitsPane(equipment, guiParameters, onEdit);
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Editable mode: rebuilds the pane if groups, limit sets or temporary limits were added, removed, (de)selected or
     * renamed meanwhile, re-reads the values otherwise.
     */
    @Override
    public void refresh() {
        if (equipment == null) {
            return;
        }
        String structure = structure();
        if (!structure.equals(shownStructure)) {
            shownStructure = structure;
            buildEditable();
        } else {
            tableRefreshers.forEach(Runnable::run);
        }
    }

    public void setEquipment(Identifiable<?> equipment, List<FlowViolation> violations) {
        setEquipment(equipment, violations, true);
    }

    /**
     * Read-only mode.
     *
     * @param presentValues whether to show the sides' present values, e.g. not along a post-contingency state's
     *                      violations, as the network holds its base case's
     */
    public void setEquipment(Identifiable<?> equipment, List<FlowViolation> violations, boolean presentValues) {
        getChildren().clear();
        List<Side> sides = sides(equipment);
        if (sides.isEmpty()) {
            getChildren().add(new Label(Messages.get("limits.noLimits")));
            return;
        }
        for (Side side : sides) {
            getChildren().add(sideTitle(side));
            if (side.selectedGroups().get().isEmpty()) {
                getChildren().add(new Label(Messages.get("limits.noSelectedGroup")));
            }
            for (OperationalLimitsGroup group : side.selectedGroups().get()) {
                List<LimitViolation> groupViolations = violations.stream()
                        .map(FlowViolation::violation)
                        .filter(v -> v.getSide() == side.side() && group.getId().equals(v.getOperationalLimitsGroupId()))
                        .toList();
                getChildren().add(groupNode(side, group, groupViolations, presentValues));
            }
        }
    }

    private static Label sideTitle(Side side) {
        Label sideTitle = new Label(Messages.get("limits.side", side.side().getNum()));
        sideTitle.getStyleClass().add("limits-side-title");
        return sideTitle;
    }

    private static List<Side> sides(Identifiable<?> equipment) {
        return switch (equipment) {
            case Branch<?> branch -> Arrays.stream(TwoSides.values()).map(s -> branchSide(branch, s)).toList();
            case ThreeWindingsTransformer transformer -> Arrays.stream(ThreeSides.values())
                    .map(s -> holderSide(s, transformer.getTerminal(s), transformer.getLeg(s)))
                    .toList();
            case BoundaryLine boundaryLine -> List.of(holderSide(ThreeSides.ONE, boundaryLine.getTerminal(), boundaryLine));
            default -> List.of();
        };
    }

    private static Side branchSide(Branch<?> branch, TwoSides side) {
        boolean one = side == TwoSides.ONE;
        return new Side(side.toThreeSides(), branch.getTerminal(side),
                () -> one ? branch.getOperationalLimitsGroups1() : branch.getOperationalLimitsGroups2(),
                () -> branch.getAllSelectedOperationalLimitsGroups(side),
                id -> {
                    if (one) {
                        branch.newOperationalLimitsGroup1(id);
                    } else {
                        branch.newOperationalLimitsGroup2(id);
                    }
                },
                id -> {
                    if (one) {
                        branch.removeOperationalLimitsGroup1(id);
                    } else {
                        branch.removeOperationalLimitsGroup2(id);
                    }
                },
                id -> branch.addSelectedOperationalLimitsGroups(side, id),
                id -> branch.deselectOperationalLimitsGroups(side, id));
    }

    private static Side holderSide(ThreeSides side, Terminal terminal, FlowsLimitsHolder holder) {
        return new Side(side, terminal, holder::getOperationalLimitsGroups, holder::getAllSelectedOperationalLimitsGroups,
                holder::newOperationalLimitsGroup, holder::removeOperationalLimitsGroup,
                holder::addSelectedOperationalLimitsGroups, holder::deselectOperationalLimitsGroups);
    }

    private VBox groupNode(Side side, OperationalLimitsGroup group, List<LimitViolation> groupViolations, boolean presentValues) {
        Label title = new Label(Messages.get("limits.group", group.getId()));
        title.getStyleClass().add("limits-group-title");
        VBox box = new VBox(6, title);
        box.getStyleClass().add("limits-group");
        if (!groupViolations.isEmpty()) {
            box.getStyleClass().add("limits-group-violated");
        }
        boolean empty = true;
        for (LimitType type : FLOW_LIMIT_TYPES) {
            Optional<? extends LoadingLimits> limits = group.getLoadingLimits(type);
            if (limits.isPresent()) {
                empty = false;
                List<LimitViolation> typeViolations = groupViolations.stream()
                        .filter(v -> v.getLimitType().name().equals(type.name()))
                        .toList();
                box.getChildren().addAll(limitsHeader(side, type, presentValues), limitsTable(limits.get(), quantity(type), typeViolations));
            }
        }
        if (empty) {
            box.getChildren().add(new Label(Messages.get("limits.noLimits")));
        }
        return box;
    }

    private Label limitsHeader(Side side, LimitType type, boolean presentValue) {
        String typeText = Messages.get("desktop.common.limitViolationType." + type.name());
        if (!presentValue) {
            return new Label(typeText);
        }
        double value = NetworkViolations.flowValue(side.terminal(), type);
        return new Label(Messages.get("limits.header", typeText, Double.isNaN(value) ? "-" : guiParameters.get().format(quantity(type), value)));
    }

    private static GuiParameters.Quantity quantity(LimitType type) {
        return type == LimitType.CURRENT ? GuiParameters.Quantity.CURRENT : GuiParameters.Quantity.POWER;
    }

    private static List<LimitRow> rows(LoadingLimits limits) {
        List<LimitRow> rows = new ArrayList<>();
        // a LOW detection kind limit set has no permanent limit: getPermanentLimit() throws
        if (limits.getDetectionKind() == DetectionKind.HIGH) {
            rows.add(new LimitRow(limits.getPermanentLimitName(), limits.getPermanentLimit(), null, false));
        }
        limits.getTemporaryLimits().forEach(tl -> rows.add(new LimitRow(tl.getName(), tl.getValue(), tl.getAcceptableDuration(), tl.isFictitious())));
        return rows;
    }

    private TableView<LimitRow> limitsTable(LoadingLimits limits, GuiParameters.Quantity quantity, List<LimitViolation> violations) {
        List<LimitRow> rows = rows(limits);
        TableView<LimitRow> table = new TableView<>();
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("limits.column.name", LimitRow::name));
        table.getColumns().add(column("limits.column.value", row -> limitText(row.value(), quantity)));
        table.getColumns().add(column("limits.column.acceptableDuration", OperationalLimitsPane::durationText));
        table.getColumns().add(column("limits.column.fictitious", row -> row.fictitious() ? Messages.get("limits.fictitious") : ""));
        table.getItems().setAll(rows);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(LimitRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("limit-crossed");
                if (!empty && row != null && isCrossed(row, violations)) {
                    getStyleClass().add("limit-crossed");
                }
            }
        });
        table.setFixedCellSize(CELL_SIZE);
        table.setPrefHeight((rows.size() + 1.5) * CELL_SIZE);
        table.setMinHeight(table.getPrefHeight());
        return table;
    }

    // as a string: MessageFormat would group the digits, e.g. "2,400 s"
    private static String durationText(LimitRow row) {
        return row.acceptableDuration() == null
                ? Messages.get("limits.permanent") : Messages.get("limits.duration", String.valueOf(row.acceptableDuration()));
    }

    // Double.MAX_VALUE is the conventional value of a last, never acceptable temporary limit
    private String limitText(double value, GuiParameters.Quantity quantity) {
        if (Double.isNaN(value)) {
            return "-";
        }
        return value == Double.MAX_VALUE ? "∞" : guiParameters.get().format(quantity, value);
    }

    // a violation's limit is the crossed one's original value, the permanent or a temporary limit - up to rounding:
    // OpenLoadFlow's goes through per-unit conversions
    static boolean isCrossed(LimitRow row, List<LimitViolation> violations) {
        return violations.stream().anyMatch(v -> Objects.equals(v.getLimitName(), row.name())
                && Math.abs(v.getLimit() - row.value()) <= LIMIT_TOLERANCE * Math.abs(row.value()));
    }

    private static TableColumn<LimitRow, String> column(String key, Function<LimitRow, String> getter) {
        TableColumn<LimitRow, String> column = new TableColumn<>(Messages.get(key));
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        return column;
    }

    // ----- editable mode -----

    // what, changed, requires rebuilding the pane rather than re-reading values
    private String structure() {
        return sides(equipment).stream().map(side -> side.groups().get().stream()
                        .sorted(Comparator.comparing(OperationalLimitsGroup::getId))
                        .map(group -> group.getId() + (side.selectedGroups().get().contains(group) ? "*" : "")
                                + FLOW_LIMIT_TYPES.stream()
                                .map(type -> group.getLoadingLimits(type)
                                        .map(limits -> type + "/" + limits.getDetectionKind() + rows(limits).stream()
                                                .map(row -> row.name() + "@" + row.acceptableDuration() + (row.fictitious() ? "f" : ""))
                                                .collect(Collectors.joining(",")))
                                        .orElse(""))
                                .collect(Collectors.joining(";")))
                        .collect(Collectors.joining("|")))
                .collect(Collectors.joining("#"));
    }

    private void buildEditable() {
        tableRefreshers.clear();
        getChildren().clear();
        for (Side side : sides(equipment)) {
            getChildren().add(sideTitle(side));
            side.groups().get().stream()
                    .sorted(Comparator.comparing(OperationalLimitsGroup::getId))
                    .forEach(group -> getChildren().add(editableGroupNode(side, group)));
            TextField idField = new TextField();
            idField.setPromptText(Messages.get("limits.edit.groupId"));
            Button addGroupButton = new Button(Messages.get("limits.edit.addGroup"));
            addGroupButton.disableProperty().bind(editable.not().or(idField.textProperty().isEmpty()));
            addGroupButton.setOnAction(event -> edit(addGroupButton, () -> side.newGroup().accept(idField.getText().trim())));
            HBox addGroup = new HBox(4, idField, addGroupButton);
            addGroup.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(addGroup);
        }
        if (getChildren().isEmpty()) {
            getChildren().add(new Label(Messages.get("limits.noLimits")));
        }
    }

    private VBox editableGroupNode(Side side, OperationalLimitsGroup group) {
        Label title = new Label(Messages.get("limits.group", group.getId()));
        title.getStyleClass().add("limits-group-title");
        CheckBox selected = new CheckBox(Messages.get("limits.edit.selected"));
        selected.setSelected(side.selectedGroups().get().contains(group));
        selected.disableProperty().bind(editable.not());
        selected.setOnAction(event -> edit(selected, () -> (selected.isSelected() ? side.select() : side.deselect()).accept(group.getId())));
        Button removeButton = new Button(Messages.get("limits.edit.removeGroup"));
        removeButton.disableProperty().bind(editable.not());
        removeButton.setOnAction(event -> edit(removeButton, () -> side.removeGroup().accept(group.getId())));
        HBox header = new HBox(8, title, selected, removeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, header);
        box.getStyleClass().add("limits-group");

        MenuButton addLimitsButton = new MenuButton(Messages.get("limits.edit.addLimits"));
        addLimitsButton.disableProperty().bind(editable.not());
        for (LimitType type : FLOW_LIMIT_TYPES) {
            Optional<? extends LoadingLimits> limits = group.getLoadingLimits(type);
            if (limits.isPresent()) {
                box.getChildren().addAll(editableLimitsHeader(side, group, type), editableLimitsTable(group, type));
            } else {
                MenuItem item = new MenuItem(Messages.get("desktop.common.limitViolationType." + type.name()));
                item.setOnAction(event -> edit(addLimitsButton, () -> newLimits(group, type).add()));
                addLimitsButton.getItems().add(item);
            }
        }
        if (!addLimitsButton.getItems().isEmpty()) {
            box.getChildren().add(addLimitsButton);
        }
        return box;
    }

    private HBox editableLimitsHeader(Side side, OperationalLimitsGroup group, LimitType type) {
        Label label = limitsHeader(side, type, true);
        tableRefreshers.add(() -> label.setText(limitsHeader(side, type, true).getText()));
        Button addButton = new Button(Messages.get("limits.edit.addTemporaryLimit"));
        addButton.disableProperty().bind(editable.not());
        addButton.setOnAction(event -> edit(addButton, () -> replace(group, type, rows -> {
            List<LimitRow> newRows = new ArrayList<>(rows);
            newRows.add(newTemporaryLimit(rows));
            return newRows;
        })));
        Button removeButton = new Button(Messages.get("limits.edit.removeLimits"));
        removeButton.disableProperty().bind(editable.not());
        removeButton.setOnAction(event -> edit(removeButton, () -> {
            switch (type) {
                case CURRENT -> group.removeCurrentLimits();
                case ACTIVE_POWER -> group.removeActivePowerLimits();
                default -> group.removeApparentPowerLimits();
            }
        }));
        HBox header = new HBox(8, label, addButton, removeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private TableView<LimitRow> editableLimitsTable(OperationalLimitsGroup group, LimitType type) {
        TableView<LimitRow> table = new TableView<>();
        table.editableProperty().bind(editable);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        StringConverter<Double> valueFormat = limitFormat(quantity(type));
        // the permanent limit's name and value are mutable, as is a temporary limit's value
        table.getColumns().add(DetailTables.editableColumn("limits.column.name", DetailTables.TEXT_FORMAT, LimitRow::name,
                (row, name) -> {
                    if (row.acceptableDuration() == null) {
                        loadingLimits(group, type).setPermanentLimitName(name);
                    } else {
                        replace(group, type, rows -> rows.stream().map(r -> r == row ? new LimitRow(name, r.value(), r.acceptableDuration(), r.fictitious()) : r).toList());
                    }
                }, onEdit));
        TableColumn<LimitRow, Double> valueColumn = DetailTables.editableColumn("limits.column.value", valueFormat, LimitRow::value,
                (row, value) -> {
                    if (row.acceptableDuration() == null) {
                        loadingLimits(group, type).setPermanentLimit(value);
                    } else {
                        loadingLimits(group, type).setTemporaryLimitValue(row.acceptableDuration(), value);
                    }
                }, onEdit);
        valueColumn.getStyleClass().add("right-aligned-column");
        table.getColumns().add(valueColumn);
        // a permanent limit has neither acceptable duration nor fictitious flag
        table.getColumns().add(DetailTables.editableColumn("limits.column.acceptableDuration", DURATION_FORMAT, LimitRow::acceptableDuration,
                (row, duration) -> replace(group, type, rows -> rows.stream()
                        .map(r -> r == row ? new LimitRow(r.name(), r.value(), duration, r.fictitious()) : r).toList()),
                onEdit, row -> row.acceptableDuration() != null));
        table.getColumns().add(DetailTables.checkBoxColumn("limits.column.fictitious",
                row -> row.acceptableDuration() == null ? null : row.fictitious(),
                (row, fictitious) -> replace(group, type, rows -> rows.stream()
                        .map(r -> r == row ? new LimitRow(r.name(), r.value(), r.acceptableDuration(), fictitious) : r).toList()),
                onEdit, editable));
        Runnable fill = () -> table.getItems().setAll(rows(loadingLimits(group, type)));
        fill.run();
        tableRefreshers.add(fill);
        table.setFixedCellSize(CELL_SIZE + 2);
        table.setPrefHeight((table.getItems().size() + 1.5) * (CELL_SIZE + 2));
        table.setMinHeight(table.getPrefHeight());

        Button removeButton = new Button(Messages.get("limits.edit.removeTemporaryLimit"));
        removeButton.disableProperty().bind(editable.not().or(table.getSelectionModel().selectedItemProperty().isNull()));
        removeButton.setOnAction(event -> {
            LimitRow selected = table.getSelectionModel().getSelectedItem();
            edit(removeButton, () -> {
                if (selected.acceptableDuration() == null) {
                    throw new PowsyblException(Messages.get("limits.edit.permanentRemoval"));
                }
                replace(group, type, rows -> rows.stream().filter(r -> r != selected).toList());
            });
        });
        table.setContextMenu(new ContextMenu(menuItem(removeButton)));
        return table;
    }

    private static MenuItem menuItem(Button button) {
        MenuItem item = new MenuItem(button.getText());
        item.disableProperty().bind(button.disableProperty());
        item.setOnAction(event -> button.fire());
        return item;
    }

    private StringConverter<Double> limitFormat(GuiParameters.Quantity quantity) {
        StringConverter<Double> format = TableColumnSupport.doubleFormat(guiParameters, quantity);
        return new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value != null && value == Double.MAX_VALUE ? "∞" : format.toString(value);
            }

            @Override
            public Double fromString(String text) {
                return "∞".equals(text.trim()) ? Double.MAX_VALUE : format.fromString(text);
            }
        };
    }

    private static LoadingLimits loadingLimits(OperationalLimitsGroup group, LimitType type) {
        return group.getLoadingLimits(type).orElseThrow();
    }

    private static LoadingLimitsAdder<?, ?> newLimits(OperationalLimitsGroup group, LimitType type) {
        return switch (type) {
            case CURRENT -> group.newCurrentLimits();
            case ACTIVE_POWER -> group.newActivePowerLimits();
            default -> group.newApparentPowerLimits();
        };
    }

    /**
     * Replaces the limit set of {@code type} by one with the rows {@code change} makes of its current ones.
     */
    private static void replace(OperationalLimitsGroup group, LimitType type, UnaryOperator<List<LimitRow>> change) {
        LoadingLimits limits = loadingLimits(group, type);
        List<LimitRow> rows = change.apply(rows(limits));
        LoadingLimitsAdder<?, ?> adder = newLimits(group, type).setDetectionKind(limits.getDetectionKind());
        for (LimitRow row : rows) {
            if (row.acceptableDuration() == null) {
                adder.setPermanentLimit(row.value()).setPermanentLimitName(row.name());
                continue;
            }
            var temporaryLimitAdder = adder.beginTemporaryLimit()
                    .setName(row.name())
                    .setValue(row.value())
                    .setAcceptableDuration(row.acceptableDuration())
                    .setFictitious(row.fictitious());
            // a renamed or re-timed temporary limit keeps its properties: found by its other attribute
            limits.getTemporaryLimits().stream()
                    .filter(tl -> tl.getName().equals(row.name()) || tl.getAcceptableDuration() == row.acceptableDuration())
                    .findFirst()
                    .ifPresent(tl -> tl.getPropertyNames().forEach(name -> temporaryLimitAdder.addProperty(name, tl.getProperty(name))));
            temporaryLimitAdder.endTemporaryLimit();
        }
        adder.add();
    }

    private static LimitRow newTemporaryLimit(List<LimitRow> rows) {
        int duration = NEW_TEMPORARY_LIMIT_DURATION;
        while (hasDuration(rows, duration) && duration > 1) {
            duration /= 2;
        }
        return new LimitRow(Messages.get("limits.duration", String.valueOf(duration)), Double.MAX_VALUE, duration, false);
    }

    private static boolean hasDuration(List<LimitRow> rows, int duration) {
        return rows.stream().anyMatch(row -> Objects.equals(row.acceptableDuration(), duration));
    }

    private void edit(Node node, Runnable edit) {
        DetailForm.apply(node, edit, onEdit);
    }
}
