/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Component;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.control.Notifications;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Cell value/factory wiring shared by the Loads, Generators, ShuntCompensators, StaticVarCompensators, Lines,
 * Transformers, TieLines, BoundaryLines, Components, Substations and VoltageLevels tables, which display the same substation/voltage-level
 * link, connected checkbox, component, and formatted-double column shapes for their respective row entity type.
 * The connected checkbox toggles its terminal's connection (disconnect if connected, connect otherwise). The
 * "two-sided" variants stack a value per terminal (or per side's substation/voltage level) in a single cell,
 * for the Lines and TieLines tables. The
 * "multi-sided" variants do the same for a variable number of sides (2 or 3), for the Transformers table, whose
 * editable variant gives each side its own double-click-to-edit Label, the same look/behavior as
 * configureDoubleColumn's TextFieldTableCell but reimplemented on a plain Node since one TableCell here holds
 * several independently editable values. The
 * "nullable editable" variants show "-" and refuse to enter edit mode for a field that only exists on some rows,
 * for the BoundaryLines table's optional generation part. configureMultiSidedTapChangerColumn combines both: a
 * per-side IntStepperField plus an info button opening TapChangerStepsDialog, blank on a side without that tap changer
 * type, for the Transformers table's ratio/phase tap changer columns. configureInfoButtonColumn is the single-value
 * counterpart, for the ShuntCompensators table's model type column, whose info button opens ShuntCompensatorSectionsDialog.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class TableColumnSupport {

    // Marks a cell as belonging to an editable column so styles.css can give it a visual affordance
    // (e.g. a background tint) - plain TextField/ChoiceBox-backed cells otherwise look identical to read-only ones.
    private static final String EDITABLE_CELL_STYLE_CLASS = "editable-cell";

    // Briefly marks a cell after an edit, so styles.css can flash it red (rejected, e.g. an IIDM validation
    // exception) or green (applied), both removed again after their respective duration below.
    static final String EDIT_ERROR_STYLE_CLASS = "edit-error";
    static final String EDIT_SUCCESS_STYLE_CLASS = "edit-success";
    private static final Duration EDIT_SUCCESS_FLASH_DURATION = Duration.millis(200);
    private static final Duration EDIT_ERROR_FLASH_DURATION = Duration.seconds(1.5);

    private static Duration flashDuration(String styleClass) {
        return EDIT_ERROR_STYLE_CLASS.equals(styleClass) ? EDIT_ERROR_FLASH_DURATION : EDIT_SUCCESS_FLASH_DURATION;
    }

    private static final StringConverter<Double> DOUBLE_FORMAT = doubleFormat(2);

    // Most columns show 2 decimal places (DOUBLE_FORMAT); a few (the Parameters group's G/B susceptance/
    // conductance values, small numbers in Siemens) need more precision to not all round down to "0.000000"-ish
    // noise - see the Lines/Transformers/BoundaryLines/TieLines "Parameters" columns.
    private static StringConverter<Double> doubleFormat(int decimalPlaces) {
        String pattern = "%." + decimalPlaces + "f";
        return new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return isMissing(value) ? "-" : String.format(Locale.ROOT, pattern, value);
            }

            @Override
            public Double fromString(String text) {
                return Double.valueOf(text.trim());
            }
        };
    }

    private static final StringConverter<String> STRING_FORMAT = new StringConverter<>() {
        @Override
        public String toString(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }

        @Override
        public String fromString(String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
        }
    };

    private TableColumnSupport() {
    }

    static <S> void configureContainerColumn(TableColumn<S, S> column,
                                              Function<S, Optional<? extends Container<?>>> containerGetter,
                                              BiFunction<Container<?>, S, Node> linkFactory) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : containerGetter.apply(item).map(container -> linkFactory.apply(container, item)).orElse(null));
            }
        });
    }

    static <S> void configureTwoSidedContainerColumn(TableColumn<S, S> column,
                                                       Function<S, Optional<? extends Container<?>>> container1Getter,
                                                       Function<S, Optional<? extends Container<?>>> container2Getter,
                                                       BiFunction<Container<?>, S, Node> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(containerNode(container1Getter.apply(item), item, linkFactory),
                                containerNode(container2Getter.apply(item), item, linkFactory), Pos.CENTER_LEFT));
            }
        });
    }

    private static <S> Node containerNode(Optional<? extends Container<?>> container, S item, BiFunction<Container<?>, S, Node> linkFactory) {
        return container.<Node>map(c -> linkFactory.apply(c, item)).orElseGet(Label::new);
    }

    static <S> void configureConnectedColumn(TableColumn<S, S> column, Function<S, Terminal> terminalGetter, Consumer<Terminal> onToggle) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        // Shared by every cell this column creates (captured by the cellFactory lambda below), keyed by Terminal
        // rather than by cell instance - see flashConnected()'s comment for why.
        Map<Terminal, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    applyConnectedFlash(this, List.of(), flashStyles);
                } else {
                    Terminal terminal = terminalGetter.apply(item);
                    setGraphic(connectedCheckBox(this, terminal, onToggle, flashStyles));
                    applyConnectedFlash(this, List.of(terminal), flashStyles);
                }
            }
        });
    }

    static <S> void configureTwoSidedConnectedColumn(TableColumn<S, S> column, Function<S, Terminal> terminal1Getter,
                                                       Function<S, Terminal> terminal2Getter, Consumer<Terminal> onToggle) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        Map<Terminal, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    applyConnectedFlash(this, List.of(), flashStyles);
                } else {
                    Terminal terminal1 = terminal1Getter.apply(item);
                    Terminal terminal2 = terminal2Getter.apply(item);
                    setGraphic(twoSidedBox(connectedCheckBox(this, terminal1, onToggle, flashStyles),
                            connectedCheckBox(this, terminal2, onToggle, flashStyles), Pos.CENTER));
                    applyConnectedFlash(this, List.of(terminal1, terminal2), flashStyles);
                }
            }
        });
    }

    static <S> void configureComponentColumn(TableColumn<S, Integer> column, Function<S, Bus> busGetter,
                                              Function<Bus, Component> componentGetter) {
        column.setCellValueFactory(cellData -> {
            Bus bus = busGetter.apply(cellData.getValue());
            Component component = bus == null ? null : componentGetter.apply(bus);
            return new ReadOnlyObjectWrapper<>(component == null ? null : component.getNum());
        });
        column.setCellFactory(col -> new TableCell<S, Integer>() {
            @Override
            protected void updateItem(Integer num, boolean empty) {
                super.updateItem(num, empty);
                setText(empty ? null : num == null ? "-" : String.valueOf(num));
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureTwoSidedComponentColumn(TableColumn<S, S> column, Function<S, Bus> bus1Getter,
                                                       Function<S, Bus> bus2Getter, Function<Bus, Component> componentGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(componentText(bus1Getter.apply(item), componentGetter)),
                                new Label(componentText(bus2Getter.apply(item), componentGetter)), Pos.CENTER));
            }
        });
    }

    private static String componentText(Bus bus, Function<Bus, Component> componentGetter) {
        Component component = bus == null ? null : componentGetter.apply(bus);
        return component == null ? "-" : String.valueOf(component.getNum());
    }

    static <S> void configureDoubleColumn(TableColumn<S, Double> column) {
        configureDoubleColumn(column, DOUBLE_FORMAT);
    }

    // For a column needing more precision than the default 2 decimal places - see doubleFormat().
    static <S> void configureDoubleColumn(TableColumn<S, Double> column, int decimalPlaces) {
        configureDoubleColumn(column, doubleFormat(decimalPlaces));
    }

    private static <S> void configureDoubleColumn(TableColumn<S, Double> column, StringConverter<Double> format) {
        // Keyed by row item rather than held on the TableCell instance: some callers' onEditCommit handler
        // triggers a wider refresh (e.g. buses' fictitious P0/Q0, which fire mainModel.setUpdate()), which can
        // recycle this exact TableCell to a different row before the flash fades - same fix as
        // flashConnected/flashKeyed below.
        Map<S, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> {
            TableCell<S, Double> cell = new EditableDoubleTableCell<>(format, flashStyles);
            if (col.isEditable()) {
                cell.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            }
            return cell;
        });
    }

    // Same rendering as TextFieldTableCell.forTableColumn(DOUBLE_FORMAT), but catches a setter exception thrown
    // by the column's onEditCommit handler (e.g. an IIDM validation exception) instead of letting it propagate:
    // TableCell.commitEdit fires that handler via Event.fireEvent before it updates the cell's displayed value,
    // so wrapping super.commitEdit both stops the rejected value from ever being shown and lets us restore the
    // cell's pre-edit display by re-running updateItem with the unchanged item.
    private static final class EditableDoubleTableCell<S> extends TextFieldTableCell<S, Double> {
        private final Map<S, String> flashStyles;

        EditableDoubleTableCell(StringConverter<Double> format, Map<S, String> flashStyles) {
            super(format);
            this.flashStyles = flashStyles;
        }

        @Override
        public void commitEdit(Double newValue) {
            S row = getTableRow() == null ? null : getTableRow().getItem();
            try {
                super.commitEdit(newValue);
                if (row != null) {
                    flashKeyed(this, row, flashStyles, EDIT_SUCCESS_STYLE_CLASS);
                }
            } catch (PowsyblException e) {
                notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }

        // Runs unconditionally, even when empty: a cell recycled to an empty filler row otherwise keeps
        // whatever flash class a previous row left on it forever, since nothing else ever revisits it to clear it.
        @Override
        public void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            S row = empty || getTableRow() == null ? null : getTableRow().getItem();
            applyKeyedFlash(this, row, flashStyles);
        }
    }

    // Nullable like DOUBLE_FORMAT's "-": unlike configureNullableEditableDoubleColumn, every row can be edited here
    // (the field just may hold no value), so clearing the text field back to "-"/blank is how a value gets unset.
    static <S> void configureEditableTextColumn(TableColumn<S, String> column) {
        column.setCellFactory(col -> {
            TableCell<S, String> cell = new EditableStringTableCell<>();
            if (col.isEditable()) {
                cell.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            }
            return cell;
        });
    }

    private static final class EditableStringTableCell<S> extends TextFieldTableCell<S, String> {
        EditableStringTableCell() {
            super(STRING_FORMAT);
        }

        @Override
        public void commitEdit(String newValue) {
            try {
                super.commitEdit(newValue);
                flashEditSuccess(this);
            } catch (PowsyblException e) {
                notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }
    }

    // For a field that only exists on some rows (e.g. a boundary line's optional generation part): shows "-" and
    // refuses to enter edit mode where the getter returns null, editable like configureDoubleColumn otherwise.
    static <S> void configureNullableEditableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> getter,
                                                            BiConsumer<S, Double> setter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> new NullableEditableDoubleTableCell<>());
        column.setOnEditCommit(event -> setter.accept(event.getRowValue(), event.getNewValue()));
        column.setComparator(missingLast(column, TableColumnSupport::isMissing));
    }

    private static final class NullableEditableDoubleTableCell<S> extends TextFieldTableCell<S, Double> {
        NullableEditableDoubleTableCell() {
            super(DOUBLE_FORMAT);
        }

        @Override
        public void startEdit() {
            if (getItem() == null) {
                return;
            }
            super.startEdit();
        }

        @Override
        public void commitEdit(Double newValue) {
            try {
                super.commitEdit(newValue);
                flashEditSuccess(this);
            } catch (PowsyblException e) {
                notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }

        @Override
        public void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (!empty && item == null) {
                setText("-");
            }
            getStyleClass().remove(EDITABLE_CELL_STYLE_CLASS);
            if (!empty && item != null) {
                getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            }
        }
    }

    // Same "-" when absent as configureNullableEditableDoubleColumn, for a field that only exists on some rows.
    static <S> void configureNullableEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                             BiConsumer<S, Boolean> setter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Boolean>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else if (value == null) {
                    setGraphic(null);
                    setText("-");
                } else {
                    setText(null);
                    setGraphic(booleanCheckBox(this, value, checked -> setter.accept(getTableRow().getItem(), checked)));
                }
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    // Shared by the Lines/TieLines/Transformers/BoundaryLines "PATL I Violation" columns: an alert icon + text
    // when overloaded, nothing otherwise. Sorting isn't dealt with yet - plain boolean order is good enough for now.
    static <S> void configureOverloadColumn(TableColumn<S, Boolean> column, Function<S, Boolean> overloadedGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(overloadedGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Boolean>() {
            @Override
            protected void updateItem(Boolean overloaded, boolean empty) {
                super.updateItem(overloaded, empty);
                setGraphic(empty || !Boolean.TRUE.equals(overloaded) ? null : alertLabel(Messages.get("common.patlIViolation.overloaded")));
            }
        });
    }

    // Shared alert affordance (orange icon + text), also used by the voltage violation column below.
    static Label alertLabel(String text) {
        FontIcon icon = new FontIcon("mdi2a-alert");
        icon.getStyleClass().add("alert-icon");
        Label label = new Label(text, icon);
        label.getStyleClass().add("alert-label");
        return label;
    }

    // Shared by AbstractBusesController and BusbarSectionsController's "Voltage Violation" column: an alert
    // icon + text when the measured voltage breaches its voltage level's limit, nothing otherwise.
    static <S> void configureVoltageViolationColumn(TableColumn<S, VoltageViolation> column,
                                                      Function<S, Double> vGetter, Function<S, VoltageLevel> voltageLevelGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(
                VoltageViolation.of(vGetter.apply(cellData.getValue()), voltageLevelGetter.apply(cellData.getValue()))));
        column.setCellFactory(col -> new TableCell<S, VoltageViolation>() {
            @Override
            protected void updateItem(VoltageViolation violation, boolean empty) {
                super.updateItem(violation, empty);
                setGraphic(empty || violation == null || !violation.isViolation() ? null : voltageViolationLabel(violation));
            }
        });
    }

    private static Label voltageViolationLabel(VoltageViolation violation) {
        Label label = alertLabel(violation.text());
        label.setTooltip(new Tooltip(violation.limitTooltip()));
        return label;
    }

    // Generic read-only counterpart of configureNullableIntColumn, for a field whose type has no dedicated
    // formatting of its own (e.g. an Optional-backed enum) - "-" when absent, T::toString otherwise.
    static <S, T extends Comparable<T>> void configureNullableColumn(TableColumn<S, T> column, Function<S, T> valueGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : value.toString());
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureNullableIntColumn(TableColumn<S, Integer> column, Function<S, Integer> valueGetter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Integer>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value == null ? "-" : String.valueOf(value));
            }
        });
        column.setComparator(missingLast(column, Objects::isNull));
    }

    static <S> void configureNullableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> valueGetter) {
        configureNullableDoubleColumn(column, valueGetter, 2);
    }

    // For a column needing more precision than the default 2 decimal places - see doubleFormat().
    static <S> void configureNullableDoubleColumn(TableColumn<S, Double> column, Function<S, Double> valueGetter, int decimalPlaces) {
        StringConverter<Double> format = doubleFormat(decimalPlaces);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<S, Double>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : format.toString(value));
            }
        });
        column.setComparator(missingLast(column, TableColumnSupport::isMissing));
    }

    private static boolean isMissing(Double value) {
        return value == null || value.isNaN();
    }

    static <S> void configureTwoSidedDoubleColumn(TableColumn<S, S> column, Function<S, Double> value1Getter,
                                                   Function<S, Double> value2Getter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(doubleText(value1Getter.apply(item))), new Label(doubleText(value2Getter.apply(item))), Pos.CENTER_RIGHT));
            }
        });
    }

    private static String doubleText(Double value) {
        return isMissing(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }

    static <S> void configureTwoSidedTextColumn(TableColumn<S, S> column, Function<S, String> value1Getter,
                                                 Function<S, String> value2Getter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(new Label(value1Getter.apply(item)), new Label(value2Getter.apply(item)), Pos.CENTER_LEFT));
            }
        });
    }

    static <S, T> void configureTwoSidedLinkColumn(TableColumn<S, S> column, Function<S, T> value1Getter,
                                                    Function<S, T> value2Getter, BiFunction<T, S, Hyperlink> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : twoSidedBox(linkFactory.apply(value1Getter.apply(item), item), linkFactory.apply(value2Getter.apply(item), item), Pos.CENTER_LEFT));
            }
        });
    }

    // Tall enough for a per-side ChoiceBox (the Regulation Mode columns) rendered in the table's Consolas
    // font - its own computed preferred height at that font, ~31px, exceeds the 25px that used to be enough
    // for every other per-side control (Label/TextField/CheckBox/IntStepperField), and forcing it down to 25
    // clipped its text at the bottom instead of centering it. Pinning every side of every such column to this
    // same height (rather than each side's own natural size, e.g. a 17px Label next to a taller control)
    // is what keeps side 1 values horizontally aligned across columns, side 2 as well, side 3 too.
    private static final double SIDE_ROW_HEIGHT = 31;

    private static <T extends Node> T fixedSideHeight(T node) {
        if (node instanceof Region region) {
            region.setMinHeight(SIDE_ROW_HEIGHT);
            region.setPrefHeight(SIDE_ROW_HEIGHT);
        }
        return node;
    }

    private static VBox twoSidedBox(Node first, Node second, Pos alignment) {
        VBox box = new VBox(fixedSideHeight(first), fixedSideHeight(second));
        box.setAlignment(alignment);
        return box;
    }

    static <S> void configureMultiSidedContainerColumn(TableColumn<S, S> column,
                                                         Function<S, List<? extends Container<?>>> containersGetter,
                                                         BiFunction<Container<?>, S, Node> linkFactory) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(containersGetter.apply(item).stream().<Node>map(container -> linkFactory.apply(container, item)).toList(), Pos.CENTER_LEFT));
            }
        });
    }

    static <S> void configureMultiSidedConnectedColumn(TableColumn<S, S> column, Function<S, List<Terminal>> terminalsGetter, Consumer<Terminal> onToggle) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        Map<Terminal, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    applyConnectedFlash(this, List.of(), flashStyles);
                } else {
                    List<Terminal> terminals = terminalsGetter.apply(item);
                    setGraphic(sidedBox(terminals.stream().<Node>map(terminal -> connectedCheckBox(this, terminal, onToggle, flashStyles)).toList(), Pos.CENTER));
                    applyConnectedFlash(this, terminals, flashStyles);
                }
            }
        });
    }

    static <S> void configureMultiSidedComponentColumn(TableColumn<S, S> column, Function<S, List<Bus>> busesGetter,
                                                         Function<Bus, Component> componentGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(busesGetter.apply(item).stream().<Node>map(bus -> new Label(componentText(bus, componentGetter))).toList(), Pos.CENTER));
            }
        });
    }

    static <S> void configureMultiSidedDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(valuesGetter.apply(item).stream().<Node>map(value -> new Label(doubleText(value))).toList(), Pos.CENTER_RIGHT));
            }
        });
    }

    private static VBox sidedBox(List<Node> nodes, Pos alignment) {
        VBox box = new VBox();
        nodes.forEach(TableColumnSupport::fixedSideHeight);
        box.getChildren().addAll(nodes);
        box.setAlignment(alignment);
        return box;
    }

    // Sets one side's value given its index in the row's per-side list (e.g. a TwoWindingsTransformer's ratedU1/
    // ratedU2, or a ThreeWindingsTransformer leg's ratedU) - unlike BiConsumer, there's no built-in 2-arg-plus-index
    // functional interface for this.
    @FunctionalInterface
    interface IndexedDoubleSetter<S> {
        void set(S item, int index, double value);
    }

    // Editable counterpart of configureMultiSidedDoubleColumn: since a single TableCell here holds a variable
    // number of independently editable values, one TextFieldTableCell-style double-click-to-edit Label per side is
    // used instead of a plain read-only Label, rather than the whole cell's built-in edit state (which only tracks
    // one value per cell).
    static <S> void configureMultiSidedEditableDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter,
                                                              IndexedDoubleSetter<S> setter) {
        configureMultiSidedEditableDoubleColumn(column, valuesGetter, setter, DOUBLE_FORMAT);
    }

    // For a column needing more precision than the default 2 decimal places - see doubleFormat().
    static <S> void configureMultiSidedEditableDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter,
                                                              IndexedDoubleSetter<S> setter, int decimalPlaces) {
        configureMultiSidedEditableDoubleColumn(column, valuesGetter, setter, doubleFormat(decimalPlaces));
    }

    private static <S> void configureMultiSidedEditableDoubleColumn(TableColumn<S, S> column, Function<S, List<Double>> valuesGetter,
                                                              IndexedDoubleSetter<S> setter, StringConverter<Double> format) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                int sideCount = valuesGetter.apply(item).size();
                List<Node> fields = new ArrayList<>();
                for (int index = 0; index < sideCount; index++) {
                    int side = index;
                    fields.add(editableDoubleField(format, () -> valuesGetter.apply(item).get(side), value -> setter.set(item, side, value)));
                }
                setGraphic(sidedBox(fields, Pos.CENTER_RIGHT));
            }
        });
    }

    // A StackPane holding a Label (shown at rest) and a TextField (shown while editing) swapped in place, so the
    // per-side value looks and behaves like a plain configureDoubleColumn cell: double-click to edit, Enter/losing
    // focus commits, Escape cancels.
    private static StackPane editableDoubleField(StringConverter<Double> format, DoubleSupplier getter, DoubleConsumer setter) {
        Label label = new Label(format.toString(getter.getAsDouble()));
        label.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_RIGHT);

        TextField field = new TextField();
        field.setAlignment(Pos.CENTER_RIGHT);
        field.setVisible(false);
        field.setManaged(false);

        label.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                field.setText(format.toString(getter.getAsDouble()));
                showEditor(label, field, true);
                field.requestFocus();
                field.selectAll();
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                showEditor(label, field, false);
            }
        });
        Runnable commit = () -> {
            Double newValue;
            try {
                newValue = format.fromString(field.getText());
            } catch (NumberFormatException e) {
                showEditor(label, field, false);
                return;
            }
            if (Double.compare(newValue, getter.getAsDouble()) != 0) {
                try {
                    setter.accept(newValue);
                    flashEditSuccess(label);
                } catch (PowsyblException e) {
                    notifyEditError(label, e);
                }
            }
            label.setText(format.toString(getter.getAsDouble()));
            showEditor(label, field, false);
        };
        field.setOnAction(event -> commit.run());
        field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && field.isVisible()) {
                commit.run();
            }
        });
        return new StackPane(label, field);
    }

    private static void showEditor(Label label, TextField field, boolean editing) {
        label.setVisible(!editing);
        label.setManaged(!editing);
        field.setVisible(editing);
        field.setManaged(editing);
    }

    // TableView's descending sort doesn't negate the column comparator, it swaps the compared values
    // (see com.sun.javafx.scene.control.TableColumnComparatorBase), which would otherwise flip a
    // nulls-last comparator into nulls-first. Consulting the column's own sort type here keeps
    // missing ("-") values last regardless of sort direction.
    private static <S, T extends Comparable<T>> Comparator<T> missingLast(TableColumn<S, T> column, Predicate<T> isMissing) {
        return (left, right) -> {
            boolean descending = column.getSortType() == TableColumn.SortType.DESCENDING;
            boolean leftMissing = isMissing.test(left);
            boolean rightMissing = isMissing.test(right);
            if (leftMissing && rightMissing) {
                return 0;
            }
            if (leftMissing) {
                return descending ? -1 : 1;
            }
            if (rightMissing) {
                return descending ? 1 : -1;
            }
            return left.compareTo(right);
        };
    }

    // Editable counterpart of a multi-sided column for a value that may not exist on every side (e.g. a
    // transformer leg without a ratio/phase tap changer): a blank slot where tapChangersGetter's list holds
    // Optional.empty(), an IntStepperField bounded to [low, high] tap position plus an info button otherwise.
    // Unlike configureMultiSidedEditableDoubleColumn, no separate setter is needed - each present value is the
    // mutable TapChanger itself, so the field writes straight through to it.
    //
    // Unlike every other multi-sided column, the per-side nodes here are pooled per TableCell (one TapChangerSlot
    // per side, created lazily and never discarded) and only rebound in updateItem, the same way SpinnerTableCell
    // reuses its single IntStepperField - it's expensive to construct (buttons/icons/CSS applied), so rebuilding
    // one per side on every updateItem call (as configureMultiSidedEditableDoubleColumn's plain Labels can afford
    // to) made scrolling/refreshing the Transformers table noticeably slow.
    static <S> void configureMultiSidedTapChangerColumn(TableColumn<S, S> column, Function<S, String> nameGetter,
                                                          Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new MultiSidedTapChangerTableCell<>(nameGetter, tapChangersGetter));
    }

    private static final class MultiSidedTapChangerTableCell<S> extends TableCell<S, S> {
        private final Function<S, String> nameGetter;
        private final Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter;
        private final List<TapChangerSlot> slots = new ArrayList<>();

        MultiSidedTapChangerTableCell(Function<S, String> nameGetter,
                                       Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter) {
            this.nameGetter = nameGetter;
            this.tapChangersGetter = tapChangersGetter;
        }

        @Override
        protected void updateItem(S item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            List<Optional<? extends TapChanger<?, ?, ?, ?>>> tapChangers = tapChangersGetter.apply(item);
            while (slots.size() < tapChangers.size()) {
                slots.add(new TapChangerSlot(this));
            }
            String name = nameGetter.apply(item);
            for (int side = 0; side < tapChangers.size(); side++) {
                slots.get(side).bind(name, side, tapChangers.size(), tapChangers.get(side));
            }
            setGraphic(sidedBox(slots.subList(0, tapChangers.size()).stream().<Node>map(slot -> slot.root).toList(), Pos.CENTER));
        }
    }

    // One side's reusable node pair: a blank Label (shown at rest, when this side has no such tap changer) and
    // an info-button-plus-IntStepperField HBox (shown otherwise), swapped in place by bind() rather than rebuilt,
    // so the field/Button/Tooltip are constructed once per TableCell and merely rebound to a different TapChanger
    // as the cell is recycled to a different row.
    private static final class TapChangerSlot {
        private final Label blankLabel = new Label();
        private final IntStepperField stepperField = new IntStepperField();
        private final HBox presentBox;
        private final StackPane root;
        private TapChanger<?, ?, ?, ?> tapChanger;
        private String name;
        private int side;
        private int totalSides;

        TapChangerSlot(TableCell<?, ?> ownerCell) {
            Button infoButton = new Button();
            infoButton.setGraphic(new FontIcon("mdi2i-information-outline"));
            infoButton.getStyleClass().add("icon-button");
            infoButton.setTooltip(new Tooltip(Messages.get("transformers.tapChanger.stepsTooltip")));
            infoButton.setOnAction(event -> {
                if (tapChanger != null) {
                    // A two-windings transformer's tap changer isn't per side (totalSides == 1 here), so its
                    // dialog header shouldn't claim a side; a three-windings transformer's leg number is
                    // 1-based, matching ThreeWindingsTransformer.Leg's own naming.
                    Integer sideNumber = totalSides > 1 ? side + 1 : null;
                    TapChangerStepsDialog.show(infoButton.getScene().getWindow(), name, sideNumber, tapChanger);
                    // The dialog can flip hasLoadTapChangingCapabilities, which decides whether this row's
                    // Regulating/Regulation Mode/Regulation Value/Target Deadband/Solved Tap columns are blank -
                    // refresh so they pick that up as soon as the (modal) dialog closes.
                    TableView<?> tableView = ownerCell.getTableView();
                    if (tableView != null) {
                        tableView.refresh();
                    }
                }
            });

            presentBox = new HBox(4, infoButton, stepperField.root);
            presentBox.setAlignment(Pos.CENTER_LEFT);
            root = new StackPane(blankLabel, presentBox);
        }

        void bind(String name, int side, int totalSides, Optional<? extends TapChanger<?, ?, ?, ?>> tapChangerOpt) {
            this.name = name;
            this.side = side;
            this.totalSides = totalSides;
            tapChanger = tapChangerOpt.orElse(null);
            boolean present = tapChanger != null;
            blankLabel.setVisible(!present);
            blankLabel.setManaged(!present);
            presentBox.setVisible(present);
            presentBox.setManaged(present);
            if (present) {
                stepperField.bind(tapChanger.getLowTapPosition(), tapChanger.getHighTapPosition(),
                        tapChanger.getTapPosition(), tapChanger::setTapPosition);
            }
        }
    }

    // Shared blank-slot rule for every per-side tap changer sub-field column below (Regulating, Regulation Mode,
    // Regulation Value, Target Deadband, Solved Tap): besides a side lacking this tap changer type altogether,
    // IIDM itself refuses to enable regulation on one that doesn't support on-load tap changing, so those fields
    // are left blank there too rather than shown disabled.
    private static boolean tapChangerFieldEnabled(Optional<? extends TapChanger<?, ?, ?, ?>> tapChangerOpt) {
        return tapChangerOpt.filter(TapChanger::hasLoadTapChangingCapabilities).isPresent();
    }

    static <S> void configureMultiSidedTapChangerBooleanColumn(TableColumn<S, S> column,
                                                                 Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                 Function<TapChanger<?, ?, ?, ?>, Boolean> getter,
                                                                 BiConsumer<TapChanger<?, ?, ?, ?>, Boolean> setter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                List<Node> nodes = tapChangersGetter.apply(item).stream()
                        .<Node>map(opt -> tapChangerFieldEnabled(opt)
                                ? booleanCheckBox(this, getter.apply(opt.get()), checked -> setter.accept(opt.get(), checked))
                                : new Label())
                        .toList();
                setGraphic(sidedBox(nodes, Pos.CENTER));
            }
        });
    }

    static <S> void configureMultiSidedTapChangerDoubleColumn(TableColumn<S, S> column,
                                                                Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                Function<TapChanger<?, ?, ?, ?>, Double> getter,
                                                                BiConsumer<TapChanger<?, ?, ?, ?>, Double> setter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                List<Node> nodes = tapChangersGetter.apply(item).stream()
                        .<Node>map(opt -> tapChangerFieldEnabled(opt)
                                ? editableDoubleField(DOUBLE_FORMAT, () -> getter.apply(opt.get()), value -> setter.accept(opt.get(), value))
                                : new Label())
                        .toList();
                setGraphic(sidedBox(nodes, Pos.CENTER_RIGHT));
            }
        });
    }

    static <S> void configureMultiSidedTapChangerNullableIntColumn(TableColumn<S, S> column,
                                                                     Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                     Function<TapChanger<?, ?, ?, ?>, Integer> getter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null
                        : sidedBox(tapChangersGetter.apply(item).stream()
                                .<Node>map(opt -> new Label(tapChangerFieldEnabled(opt) ? nullableIntText(getter.apply(opt.get())) : ""))
                                .toList(), Pos.CENTER_RIGHT));
            }
        });
    }

    private static String nullableIntText(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    static <S, T> void configureMultiSidedTapChangerChoiceColumn(TableColumn<S, S> column,
                                                                   Function<S, List<Optional<? extends TapChanger<?, ?, ?, ?>>>> tapChangersGetter,
                                                                   Function<TapChanger<?, ?, ?, ?>, T> getter,
                                                                   BiConsumer<TapChanger<?, ?, ?, ?>, T> setter,
                                                                   List<T> choices, StringConverter<T> converter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                List<Node> nodes = tapChangersGetter.apply(item).stream()
                        .<Node>map(opt -> tapChangerFieldEnabled(opt)
                                ? editableChoiceField(getter.apply(opt.get()), choices, converter, value -> setter.accept(opt.get(), value))
                                : new Label())
                        .toList();
                setGraphic(sidedBox(nodes, Pos.CENTER));
            }
        });
    }

    // A plain ChoiceBox rather than editableDoubleField's double-click-to-edit Label/TextField swap - a dropdown
    // is already a single always-visible control, so there's no "at rest" display to swap out of. The revert
    // on a rejected edit re-selects oldValue, which re-fires this same listener - reverting is guarded so that
    // doesn't re-run the setter with oldValue as if it were a new edit.
    private static <T> ChoiceBox<T> editableChoiceField(T value, List<T> choices, StringConverter<T> converter, Consumer<T> setter) {
        ChoiceBox<T> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(choices));
        choiceBox.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
        choiceBox.setMaxWidth(Double.MAX_VALUE);
        choiceBox.setConverter(converter);
        choiceBox.getSelectionModel().select(value);
        boolean[] reverting = {false};
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (reverting[0] || Objects.equals(newValue, oldValue)) {
                return;
            }
            try {
                setter.accept(newValue);
                flashEditSuccess(choiceBox);
            } catch (PowsyblException e) {
                notifyEditError(choiceBox, e);
                reverting[0] = true;
                choiceBox.getSelectionModel().select(oldValue);
                reverting[0] = false;
            }
        });
        return choiceBox;
    }

    // Single-value counterpart of configureMultiSidedTapChangerColumn's per-side info button: a Label plus a
    // fixed info button in one HBox, for a column whose row has exactly one value rather than a variable number
    // of sides. Used by the ShuntCompensators table's model type column, whose button opens
    // ShuntCompensatorSectionsDialog.
    static <S> void configureInfoButtonColumn(TableColumn<S, S> column, Function<S, String> textGetter,
                                               String tooltipKey, BiConsumer<Window, S> onInfoClick) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new InfoButtonTableCell<>(textGetter, tooltipKey, onInfoClick));
        // The cell value is the row item itself (S isn't Comparable), so sorting needs an explicit comparator
        // rather than the column's default cast-to-Comparable one - sort by the same text the cell displays.
        column.setComparator(Comparator.comparing(textGetter));
    }

    private static final class InfoButtonTableCell<S> extends TableCell<S, S> {
        private final Function<S, String> textGetter;
        private final Label label = new Label();
        private final Button infoButton = new Button();
        private final HBox box;
        private S current;

        InfoButtonTableCell(Function<S, String> textGetter, String tooltipKey, BiConsumer<Window, S> onInfoClick) {
            this.textGetter = textGetter;
            infoButton.setGraphic(new FontIcon("mdi2i-information-outline"));
            infoButton.getStyleClass().add("icon-button");
            infoButton.setTooltip(new Tooltip(Messages.get(tooltipKey)));
            infoButton.setOnAction(event -> {
                if (current != null) {
                    onInfoClick.accept(infoButton.getScene().getWindow(), current);
                }
            });
            box = new HBox(4, infoButton, label);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(S item, boolean empty) {
            super.updateItem(item, empty);
            current = item;
            if (empty || item == null) {
                setGraphic(null);
            } else {
                label.setText(textGetter.apply(item));
                setGraphic(box);
            }
        }
    }

    // The section field's [min, max] range is per-row (clamped to each shunt compensator's own maximum section
    // count), so each cell binds its own bounds rather than sharing one across the column.
    static <S> void configureSpinnerIntColumn(TableColumn<S, S> column, Function<S, Integer> valueGetter,
                                               Function<S, Integer> maxGetter, BiConsumer<S, Integer> setter) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new SpinnerTableCell<>(valueGetter, maxGetter, setter));
    }

    private static final class SpinnerTableCell<S> extends TableCell<S, S> {
        private final Function<S, Integer> valueGetter;
        private final Function<S, Integer> maxGetter;
        private final BiConsumer<S, Integer> setter;
        private final IntStepperField stepperField = new IntStepperField();

        SpinnerTableCell(Function<S, Integer> valueGetter, Function<S, Integer> maxGetter, BiConsumer<S, Integer> setter) {
            this.valueGetter = valueGetter;
            this.maxGetter = maxGetter;
            this.setter = setter;
        }

        @Override
        protected void updateItem(S item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                stepperField.bind(0, maxGetter.apply(item), valueGetter.apply(item), newValue -> setter.accept(item, newValue));
                setGraphic(stepperField.root);
            }
        }
    }

    // Editable int field used in place of a Spinner<Integer>: the same double-click-to-edit Label/TextField pair
    // as editableDoubleField (so it looks and is sized exactly like the RatedU/RatedS columns), plus a plus/minus
    // icon button pair stacked to its right, one hidden whenever the current value is already at that bound.
    // Only setVisible is toggled on those buttons (never setManaged), so each keeps its fixed top/bottom slot
    // instead of the remaining one re-centering into the hidden one's place.
    private static final class IntStepperField {
        // Fixed rather than content-driven (e.g. a plain Label's natural width, or a parent's leftover-space
        // growth), so the field is exactly as wide in the Transformers tap changer columns - nested inside an
        // extra info-button HBox and StackPane slot - as in the Shunt Compensators section column, and doesn't
        // resize as the value gains or loses digits.
        private static final double FIELD_WIDTH = 32;

        private final Label label = new Label();
        private final TextField textField = new TextField();
        private final Button incrementButton = new Button();
        private final Button decrementButton = new Button();
        private final HBox root;
        private int min;
        private int max;
        private int value;
        private Consumer<Integer> onChange;

        IntStepperField() {
            label.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            label.setPrefWidth(FIELD_WIDTH);
            label.setMinWidth(FIELD_WIDTH);
            label.setMaxWidth(FIELD_WIDTH);
            label.setAlignment(Pos.CENTER_RIGHT);

            textField.setPrefWidth(FIELD_WIDTH);
            textField.setMinWidth(FIELD_WIDTH);
            textField.setMaxWidth(FIELD_WIDTH);
            textField.setAlignment(Pos.CENTER_RIGHT);
            textField.setVisible(false);
            textField.setManaged(false);

            label.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    textField.setText(String.valueOf(value));
                    showEditor(label, textField, true);
                    textField.requestFocus();
                    textField.selectAll();
                }
            });
            textField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    showEditor(label, textField, false);
                }
            });
            Runnable commitFromText = () -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(textField.getText().trim());
                } catch (NumberFormatException e) {
                    showEditor(label, textField, false);
                    return;
                }
                commit(parsed);
                showEditor(label, textField, false);
            };
            textField.setOnAction(event -> commitFromText.run());
            textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused && textField.isVisible()) {
                    commitFromText.run();
                }
            });

            FontIcon plusIcon = new FontIcon("mdi2p-plus");
            plusIcon.setIconSize(8);
            incrementButton.setGraphic(plusIcon);
            FontIcon minusIcon = new FontIcon("mdi2m-minus");
            minusIcon.setIconSize(8);
            decrementButton.setGraphic(minusIcon);
            incrementButton.getStyleClass().addAll("icon-button", "stepper-button");
            decrementButton.getStyleClass().addAll("icon-button", "stepper-button");
            incrementButton.setFocusTraversable(false);
            decrementButton.setFocusTraversable(false);
            incrementButton.setOnAction(event -> commit(value + 1));
            decrementButton.setOnAction(event -> commit(value - 1));

            StackPane fieldStack = new StackPane(label, textField);
            VBox buttons = new VBox(incrementButton, decrementButton);
            buttons.setAlignment(Pos.CENTER);
            root = new HBox(2, fieldStack, buttons);
            root.setAlignment(Pos.CENTER_LEFT);
        }

        void bind(int min, int max, int value, Consumer<Integer> onChange) {
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            display(value);
        }

        private void display(int newValue) {
            value = newValue;
            label.setText(String.valueOf(value));
            incrementButton.setVisible(value < max);
            decrementButton.setVisible(value > min);
        }

        private void commit(int newValue) {
            int clamped = Math.min(max, Math.max(min, newValue));
            if (clamped == value) {
                display(value);
                return;
            }
            try {
                onChange.accept(clamped);
                display(clamped);
                flashEditSuccess(label);
            } catch (PowsyblException e) {
                notifyEditError(label, e);
                display(value);
            }
        }
    }

    // editable == null means "always editable" (no per-row disabling) - every checkbox column whose value
    // is meaningful on every row.
    static <S> void configureEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                    BiConsumer<S, Boolean> setter) {
        configureEditableBooleanColumn(column, getter, setter, null);
    }

    // For a row where the field can't be edited (e.g. a switch's retain status, only meaningful in a
    // node/breaker voltage level): the checkbox is disabled (greyed out by the default skin) rather than
    // hidden, so the field's current value stays visible.
    //
    // Flash state is keyed by row identity rather than held on the TableCell instance, the same fix as
    // EditableDoubleTableCell/configureConnectedColumn: setter can trigger a wider mainModel.setUpdate()
    // (e.g. a switch's open/retained status affecting the diagram), which refreshes this very table and
    // can recycle this exact TableCell to a different row before the flash's duration expires - a flash
    // applied directly to the cell would then land on whichever row got recycled into it.
    static <S> void configureEditableBooleanColumn(TableColumn<S, Boolean> column, Function<S, Boolean> getter,
                                                    BiConsumer<S, Boolean> setter, Predicate<S> editable) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        Map<S, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new EditableBooleanTableCell<>(setter, editable, flashStyles));
    }

    private static final class EditableBooleanTableCell<S> extends TableCell<S, Boolean> {
        private final BiConsumer<S, Boolean> setter;
        private final Predicate<S> editable;
        private final Map<S, String> flashStyles;

        EditableBooleanTableCell(BiConsumer<S, Boolean> setter, Predicate<S> editable, Map<S, String> flashStyles) {
            this.setter = setter;
            this.editable = editable;
            this.flashStyles = flashStyles;
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            S row = empty || getTableRow() == null ? null : getTableRow().getItem();
            setGraphic(empty || value == null ? null : checkBox(value, row));
            applyKeyedFlash(this, row, flashStyles);
        }

        private CheckBox checkBox(boolean value, S row) {
            CheckBox checkBox = new CheckBox();
            checkBox.setSelected(value);
            checkBox.setDisable(editable != null && (row == null || !editable.test(row)));
            checkBox.setOnAction(event -> onAction(checkBox));
            return checkBox;
        }

        private void onAction(CheckBox checkBox) {
            S currentRow = getTableRow().getItem();
            boolean newValue = checkBox.isSelected();
            try {
                setter.accept(currentRow, newValue);
                flashKeyed(this, currentRow, flashStyles, EDIT_SUCCESS_STYLE_CLASS);
            } catch (PowsyblException e) {
                checkBox.setSelected(!newValue);
                notifyEditError(this, e);
            }
        }
    }

    static <S, T> void configureChoiceColumn(TableColumn<S, T> column, Function<S, T> getter, BiConsumer<S, T> setter,
                                              List<T> choices, StringConverter<T> converter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        ObservableList<T> items = FXCollections.observableArrayList(choices);
        column.setCellFactory(col -> {
            TableCell<S, T> cell = new EditableChoiceTableCell<>(converter, items);
            cell.getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            return cell;
        });
        column.setOnEditCommit(event -> setter.accept(event.getRowValue(), event.getNewValue()));
    }

    // Same commitEdit-wrapping as EditableDoubleTableCell, for ChoiceBoxTableCell.forTableColumn-shaped columns.
    private static final class EditableChoiceTableCell<S, T> extends ChoiceBoxTableCell<S, T> {
        EditableChoiceTableCell(StringConverter<T> converter, ObservableList<T> items) {
            super(converter, items);
        }

        @Override
        public void commitEdit(T newValue) {
            try {
                super.commitEdit(newValue);
                flashEditSuccess(this);
            } catch (PowsyblException e) {
                notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }
    }

    private static CheckBox connectedCheckBox(TableCell<?, ?> cell, Terminal terminal, Consumer<Terminal> onToggle, Map<Terminal, String> flashStyles) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(terminal.isConnected());
        checkBox.setOnAction(event -> {
            try {
                toggleConnection(terminal);
                flashConnected(cell, terminal, flashStyles, EDIT_SUCCESS_STYLE_CLASS);
                onToggle.accept(terminal);
            } catch (PowsyblException e) {
                checkBox.setSelected(terminal.isConnected());
                showEditErrorPopup(cell, e);
                flashConnected(cell, terminal, flashStyles, EDIT_ERROR_STYLE_CLASS);
            }
        });
        return checkBox;
    }

    // onToggle (wired by every caller to mainModel.setUpdate(terminal.getVoltageLevel())) triggers a full-table
    // refresh, which can make the
    // TableView's virtualization recycle this exact TableCell instance to a different row before the flash's
    // duration expires - unlike every other editable cell type, which never forces such a refresh
    // mid-edit. So the flash state is keyed by Terminal in a map instead of held on the TableCell instance: applied
    // immediately here for instant feedback, then re-derived by applyConnectedFlash() from each cell's updateItem
    // (called again both by onToggle's refresh and by the tableView.refresh() below), so it always lands on
    // whichever cell currently displays this terminal rather than wherever this cell ends up being recycled to.
    private static void flashConnected(TableCell<?, ?> cell, Terminal terminal, Map<Terminal, String> flashStyles, String styleClass) {
        flashStyles.put(terminal, styleClass);
        applyConnectedFlash(cell, List.of(terminal), flashStyles);
        PauseTransition pause = new PauseTransition(flashDuration(styleClass));
        pause.setOnFinished(event -> {
            flashStyles.remove(terminal);
            TableView<?> tableView = cell.getTableView();
            if (tableView != null) {
                tableView.refresh();
            }
        });
        pause.play();
    }

    // Re-derives a connected-column cell's flash style from flashStyles - error taking priority over success if
    // both are present, which can only happen for the two-/multi-sided variants stacking several terminals in
    // one cell. Called from every configure*ConnectedColumn's updateItem, so a recycled cell always reflects
    // whichever terminal(s) it currently displays rather than carrying over a stale style from a previous row.
    private static void applyConnectedFlash(TableCell<?, ?> cell, List<Terminal> terminals, Map<Terminal, String> flashStyles) {
        cell.getStyleClass().removeAll(EDIT_SUCCESS_STYLE_CLASS, EDIT_ERROR_STYLE_CLASS);
        if (terminals.stream().anyMatch(t -> EDIT_ERROR_STYLE_CLASS.equals(flashStyles.get(t)))) {
            cell.getStyleClass().add(EDIT_ERROR_STYLE_CLASS);
        } else if (terminals.stream().anyMatch(t -> EDIT_SUCCESS_STYLE_CLASS.equals(flashStyles.get(t)))) {
            cell.getStyleClass().add(EDIT_SUCCESS_STYLE_CLASS);
        }
    }

    // Single-key counterpart of flashConnected/applyConnectedFlash, for a column whose cell corresponds to exactly
    // one row identity rather than a list of terminals (e.g. a shunt compensator section number or a tap changer
    // position, in ShuntCompensatorSectionsDialog/TapChangerStepsDialog, both of which call tableView.refresh()
    // on commit to update sibling rows/columns derived from the edited value). Same reasoning as flashConnected:
    // that refresh can recycle the editing TableCell to a different row before the flash fades, so the state is
    // keyed by the row's identity (K) and re-derived in updateItem instead of held on the cell instance.
    static <K> void flashKeyed(TableCell<?, ?> cell, K key, Map<K, String> flashStyles, String styleClass) {
        flashStyles.put(key, styleClass);
        applyKeyedFlash(cell, key, flashStyles);
        PauseTransition pause = new PauseTransition(flashDuration(styleClass));
        pause.setOnFinished(event -> {
            flashStyles.remove(key);
            TableView<?> tableView = cell.getTableView();
            if (tableView != null) {
                tableView.refresh();
            }
        });
        pause.play();
    }

    static <K> void applyKeyedFlash(TableCell<?, ?> cell, K key, Map<K, String> flashStyles) {
        cell.getStyleClass().removeAll(EDIT_SUCCESS_STYLE_CLASS, EDIT_ERROR_STYLE_CLASS);
        String style = flashStyles.get(key);
        if (style != null) {
            cell.getStyleClass().add(style);
        }
    }

    // Shared by every editable boolean column so its checkbox renders identically to connectedCheckBox's.
    private static CheckBox booleanCheckBox(TableCell<?, ?> cell, boolean selected, Consumer<Boolean> onChange) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(selected);
        checkBox.setOnAction(event -> {
            boolean newValue = checkBox.isSelected();
            try {
                onChange.accept(newValue);
                flashEditSuccess(cell);
            } catch (PowsyblException e) {
                checkBox.setSelected(!newValue);
                notifyEditError(cell, e);
            }
        });
        return checkBox;
    }

    // Reports a rejected edit (e.g. an IIDM validation exception from a setter such as Generator::setVoltageRegulatorOn):
    // a ControlsFX popup with the exception message (not its stack trace), anchored to the failing cell/field, plus a
    // brief red flash on it. Does not revert the displayed value - callers do that themselves, since how to revert
    // (re-run updateItem, reselect a checkbox, reset a spinner) differs per cell type. Takes a plain Node rather than
    // a TableCell so the multi-sided editable double column's per-side text fields can share it too.
    static void notifyEditError(Node node, PowsyblException exception) {
        showEditErrorPopup(node, exception);
        flash(node, EDIT_ERROR_STYLE_CLASS);
    }

    private static void showEditErrorPopup(Node node, PowsyblException exception) {
        Notifications.create()
                .owner(node)
                .title(Messages.get("common.editError.title"))
                .text(exception.getMessage())
                .showError();
    }

    // Briefly flashes a cell/field green after a setter call applied successfully.
    static void flashEditSuccess(Node node) {
        flash(node, EDIT_SUCCESS_STYLE_CLASS);
    }

    private static void flash(Node node, String styleClass) {
        node.getStyleClass().add(styleClass);
        PauseTransition pause = new PauseTransition(flashDuration(styleClass));
        pause.setOnFinished(event -> node.getStyleClass().remove(styleClass));
        pause.play();
    }

    private static void toggleConnection(Terminal terminal) {
        if (terminal.isConnected()) {
            terminal.disconnect();
        } else {
            terminal.connect();
        }
    }
}
