/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.SearchableComboBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Always-visible editors in table cells, edited like the parameters and detail views' fields: a value is committed on
 * Enter or focus loss, Escape reverts what is being typed, Up/Down commit then move to the field above/below in the same
 * column. A rejected value pops an error up and flashes red, an applied one flashes green.
 * <p>
 * A table rebuilds its cells whenever its items change (e.g. its refresh after a network update) and reuses them for
 * other rows. So the flash is keyed by the row a field shows rather than held on the field ({@link KeyedFlash}), and
 * the focus, with what is being typed, follows its row to the field showing it after the rebuild.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CellFields {

    /**
     * What identifies a row across table rebuilds: the row item, or its index for rows recreated by every refresh (e.g.
     * records, not equal to themselves once edited), in unsorted tables only.
     */
    public enum RowKey {
        ITEM,
        INDEX
    }

    // table property: the focus left by a field whose cell was rebound to another row, see CellTextField.leave
    private static final Object PENDING_FOCUS = new Object();

    private CellFields() {
    }

    /**
     * Edits {@code column}'s values in text fields, committed through its onEditCommit handler, which rejects a value by
     * throwing a PowsyblException; a row not {@code rowEditable} shows its value as text.
     */
    public static <S, T> void textColumn(TableColumn<S, T> column, StringConverter<T> converter, Predicate<S> rowEditable, RowKey rowKey) {
        KeyedFlash flash = new KeyedFlash();
        column.setCellFactory(_ -> new TextCell<>(flash, converter, rowEditable, rowKey));
        TableColumnSupport.disableUnlessTableEditable(column);
    }

    private static final class TextCell<S, T> extends TableCell<S, T> {
        private final CellTextField field;
        private final StringConverter<T> converter;
        private final Predicate<S> rowEditable;
        private final RowKey rowKey;

        private TextCell(KeyedFlash flash, StringConverter<T> converter, Predicate<S> rowEditable, RowKey rowKey) {
            field = new CellTextField(this, flash);
            this.converter = converter;
            this.rowEditable = rowEditable;
            this.rowKey = rowKey;
        }

        @Override
        protected void updateItem(T value, boolean empty) {
            super.updateItem(value, empty);
            S row = rowOf(this, empty);
            if (row != null && rowEditable.test(row)) {
                setText(null);
                int index = getIndex();
                // read again after an edit, from the row the key identifies (an edit can replace or move rows)
                Supplier<T> current = rowKey == RowKey.INDEX ? () -> getTableColumn().getCellData(index) : () -> getTableColumn().getCellData(row);
                field.bind(rowKey == RowKey.INDEX ? index : row, () -> converter.toString(current.get()),
                    text -> commit(this, converter.fromString(text)));
                setGraphic(field);
            } else {
                field.unbind();
                setGraphic(null);
                setText(row == null ? null : converter.toString(value));
            }
        }
    }

    /**
     * Edits {@code column}'s values in choice boxes, committed through its onEditCommit handler like {@link #textColumn}.
     */
    public static <S, T> void choiceColumn(TableColumn<S, T> column, List<T> choices, StringConverter<T> converter) {
        choiceColumn(column, (cell, flash) -> new CellChoiceBox<>(cell, flash, choices, converter));
    }

    /**
     * Like {@link #choiceColumn}, with a search field filtering long lists of choices (e.g. countries).
     */
    public static <S, T> void searchableChoiceColumn(TableColumn<S, T> column, List<T> choices, StringConverter<T> converter) {
        choiceColumn(column, (cell, flash) -> new CellSearchableComboBox<>(cell, flash, choices, converter));
    }

    private static <S, T, E extends Node & ChoiceEditor<T>> void choiceColumn(TableColumn<S, T> column,
                                                                             BiFunction<TableCell<?, ?>, KeyedFlash, E> editorFactory) {
        KeyedFlash flash = new KeyedFlash();
        column.setCellFactory(_ -> new TableCell<>() {
            private final E choiceBox = editorFactory.apply(this, flash);

            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                S row = rowOf(this, empty);
                if (row == null) {
                    choiceBox.unbind();
                    setGraphic(null);
                } else {
                    choiceBox.bind(row, () -> getTableColumn().getCellData(row), newValue -> commit(this, newValue));
                    setGraphic(choiceBox);
                }
            }
        });
        TableColumnSupport.disableUnlessTableEditable(column);
    }

    /**
     * Binds one side's editor to a row, or returns false to leave that side blank (e.g. a leg without tap changer).
     */
    @FunctionalInterface
    interface SideBinder<S, E> {
        boolean bind(E editor, Object key, S row, int side);
    }

    /**
     * Stacks one editor per side of a row (a value per transformer leg, or a single value), at the per-side height of
     * the table's other multi-sided columns (see TableColumnSupport.sideHeight); editors are created once per cell and
     * rebound.
     */
    static <S, E extends Node & CellEditor> void sidedColumn(TableColumn<S, S> column, Function<S, Integer> sideCount,
                                                            BiFunction<TableCell<?, ?>, KeyedFlash, E> editorFactory,
                                                            SideBinder<S, E> binder) {
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        KeyedFlash flash = new KeyedFlash();
        column.setCellFactory(_ -> new SidedCell<>(sideCount, cell -> editorFactory.apply(cell, flash), binder));
        TableColumnSupport.disableUnlessTableEditable(column);
    }

    private static final class SidedCell<S, E extends Node & CellEditor> extends TableCell<S, S> {
        private final Function<S, Integer> sideCount;
        private final Function<TableCell<?, ?>, E> editorFactory;
        private final SideBinder<S, E> binder;
        private final List<E> editors = new ArrayList<>();
        private final VBox box = new VBox();

        private SidedCell(Function<S, Integer> sideCount, Function<TableCell<?, ?>, E> editorFactory, SideBinder<S, E> binder) {
            this.sideCount = sideCount;
            this.editorFactory = editorFactory;
            this.binder = binder;
            // a single value centered in a multi-sided row, as its other single-valued columns
            box.setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(S row, boolean empty) {
            super.updateItem(row, empty);
            int count = empty || row == null ? 0 : sideCount.apply(row);
            while (editors.size() < count) {
                E editor = editorFactory.apply(this);
                editors.add(editor);
                box.getChildren().add(new StackPane(editor));
            }
            for (int side = 0; side < editors.size(); side++) {
                E editor = editors.get(side);
                boolean bound = side < count && binder.bind(editor, List.of(row, side), row, side);
                if (!bound) {
                    editor.unbind();
                }
                editor.setVisible(bound);
                Node slot = box.getChildren().get(side);
                slot.setVisible(side < count);
                slot.setManaged(side < count);
                TableColumnSupport.sideHeight(slot, count);
            }
            setGraphic(count == 0 ? null : box);
        }
    }

    static <S> S rowOf(TableCell<S, ?> cell, boolean empty) {
        TableView<S> table = cell.getTableView();
        int index = cell.getIndex();
        return empty || table == null || index < 0 || index >= table.getItems().size() ? null : table.getItems().get(index);
    }

    private static <S, T> void commit(TableCell<S, T> cell, T value) {
        TableView<S> table = cell.getTableView();
        TableColumn<S, T> column = cell.getTableColumn();
        Event.fireEvent(column, new TableColumn.CellEditEvent<>(table, new TablePosition<>(table, cell.getIndex(), column),
            TableColumn.editCommitEvent(), value));
    }

    private static <S> void selectCell(TableCell<S, ?> cell) {
        TableView<S> table = cell.getTableView();
        if (table != null && cell.getIndex() >= 0) {
            table.getSelectionModel().clearAndSelect(cell.getIndex(), cell.getTableColumn());
        }
    }

    // edit runs a setter; edited is the key it edited, which a table rebuild during the edit may have unbound
    private static void apply(Node editor, KeyedFlash flash, Object edited, String text, Runnable edit) {
        try {
            edit.run();
        } catch (PowsyblException e) {
            reject(editor, flash, edited, e);
            return;
        } catch (NumberFormatException e) {
            reject(editor, flash, edited, new PowsyblException(Messages.get("desktop.common.editError.invalidNumber", text.trim()), e));
            return;
        } catch (IllegalArgumentException e) {
            // some IIDM validations throw it, e.g. extensions'
            reject(editor, flash, edited, new PowsyblException(e.getMessage(), e));
            return;
        }
        flash.flash(editor, edited, TableColumnSupport.EDIT_SUCCESS_STYLE_CLASS);
    }

    private static void reject(Node editor, KeyedFlash flash, Object edited, PowsyblException exception) {
        TableColumnSupport.showEditErrorPopup(editor, exception);
        flash.flash(editor, edited, TableColumnSupport.EDIT_ERROR_STYLE_CLASS);
    }

    /**
     * An editor of a cell's value, bound to the row its cell shows.
     */
    interface CellEditor {
        void unbind();
    }

    /**
     * The edit feedback of a column's cells, keyed by the row (or row side) a node shows rather than held on the node:
     * a table rebuild can rebind the node to another row before the flash fades.
     */
    static final class KeyedFlash {
        private record Flash(String styleClass, PauseTransition pause) {
        }

        private final Map<Object, Flash> flashes = new HashMap<>();
        // the nodes showing a flash, to clear it when it fades
        private final Map<Node, Object> flashing = new HashMap<>();

        void flash(Node node, Object key, String styleClass) {
            PauseTransition pause = new PauseTransition(TableColumnSupport.flashDuration(styleClass));
            Flash previous = flashes.put(key, new Flash(styleClass, pause));
            if (previous != null) {
                previous.pause().stop();
            }
            show(node, key);
            pause.setOnFinished(_ -> {
                flashes.remove(key);
                List.copyOf(flashing.entrySet()).stream()
                    .filter(entry -> entry.getValue().equals(key))
                    .forEach(entry -> show(entry.getKey(), key));
            });
            pause.play();
        }

        /**
         * Shows on {@code node}, now showing {@code key}'s row (null for none), that row's flash if any.
         */
        void show(Node node, Object key) {
            node.getStyleClass().removeAll(TableColumnSupport.EDIT_SUCCESS_STYLE_CLASS, TableColumnSupport.EDIT_ERROR_STYLE_CLASS);
            Flash flash = key == null ? null : flashes.get(key);
            if (flash == null) {
                flashing.remove(node);
            } else {
                node.getStyleClass().add(flash.styleClass());
                flashing.put(node, key);
            }
        }
    }

    // typed: the text being typed, null if unchanged
    private record PendingFocus(TableColumn<?, ?> column, Object key, String typed, int anchor, int caret) {
    }

    /**
     * A text field editing one value of a cell, of the row identified by its key.
     */
    static final class CellTextField extends TextField implements CellEditor {
        private static final String STYLE_CLASS = "cell-text-field";

        private final TableCell<?, ?> cell;
        private final KeyedFlash flash;
        private Object key;
        private Supplier<String> display;
        private Consumer<String> commit;
        private String shown;
        private PendingFocus restoring;

        CellTextField(TableCell<?, ?> cell, KeyedFlash flash) {
            this.cell = cell;
            this.flash = flash;
            getStyleClass().add(STYLE_CLASS);
            // as wide as its text, like a text cell, for the columns fitting their content
            prefColumnCountProperty().bind(textProperty().length());
            setOnAction(_ -> commitIfChanged());
            focusedProperty().addListener((_, _, focused) -> {
                if (focused) {
                    selectCell(cell);
                } else {
                    commitIfChanged();
                }
            });
            addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                switch (event.getCode()) {
                    case ESCAPE -> show();
                    case UP -> move(-1);
                    case DOWN -> move(1);
                    default -> {
                        return;
                    }
                }
                event.consume();
            });
        }

        /**
         * Shows {@code display}'s value, unless being typed in for the same row, and commits text with {@code commit}.
         */
        void bind(Object key, Supplier<String> display, Consumer<String> commit) {
            leave(key);
            boolean sameRow = key.equals(this.key);
            this.key = key;
            this.display = display;
            this.commit = commit;
            if (!sameRow || !isFocused() || getText().equals(shown)) {
                show();
            }
            flash.show(this, key);
            restoreFocus();
        }

        @Override
        public void unbind() {
            leave(null);
            key = null;
            display = null;
            commit = null;
            flash.show(this, null);
        }

        /**
         * Commits {@code text} as if typed, e.g. from a stepper's buttons.
         */
        void apply(String text) {
            Object edited = key;
            CellFields.apply(this, flash, edited, text, () -> commit.accept(text));
            if (Objects.equals(key, edited)) {
                show();
            }
        }

        private void commitIfChanged() {
            if (commit == null || getText().equals(shown)) {
                return;
            }
            // not while its network is busy, e.g. disabled while being typed in
            if (isDisabled()) {
                show();
            } else {
                apply(getText());
            }
        }

        private void show() {
            shown = display.get();
            setText(shown);
        }

        // Rebound to another row while focused, by a table rebuild: hands the focus and what is being typed over to the
        // field showing this row next, if any by the end of the layout pass, and leaves the table focused meanwhile.
        private void leave(Object newKey) {
            TableView<?> table = cell.getTableView();
            if (!isFocused() || key == null || key.equals(newKey) || table == null || table.getScene() == null) {
                return;
            }
            PendingFocus pending = new PendingFocus(cell.getTableColumn(), key, getText().equals(shown) ? null : getText(), getAnchor(), getCaretPosition());
            table.getProperties().put(PENDING_FOCUS, pending);
            Scene scene = table.getScene();
            scene.addPostLayoutPulseListener(new Runnable() {
                @Override
                public void run() {
                    table.getProperties().remove(PENDING_FOCUS, pending);
                    Platform.runLater(() -> scene.removePostLayoutPulseListener(this));
                }
            });
            // not committed by the focus loss
            show();
            table.requestFocus();
        }

        private void restoreFocus() {
            TableView<?> table = cell.getTableView();
            if (table != null && table.getProperties().get(PENDING_FOCUS) instanceof PendingFocus pending
                    && pending.column() == cell.getTableColumn() && pending.key().equals(key)) {
                table.getProperties().remove(PENDING_FOCUS);
                if (pending.typed() != null) {
                    setText(pending.typed());
                }
                // once laid out: a cell is made visible after being bound
                restoring = pending;
                requestLayout();
            }
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (restoring != null) {
                PendingFocus pending = restoring;
                restoring = null;
                requestFocus();
                if (pending.typed() == null) {
                    selectAll();
                } else {
                    selectRange(pending.anchor(), pending.caret());
                }
            }
        }

        // the next field in this column: this cell's next side, else the nearest row's field, scrolled to
        private void move(int direction) {
            TableView<?> table = cell.getTableView();
            TableColumn<?, ?> column = cell.getTableColumn();
            int row = cell.getIndex();
            List<CellTextField> sides = fieldsIn(cell, column);
            int side = sides.indexOf(this) + direction;
            commitIfChanged();
            if (side >= 0 && side < sides.size()) {
                sides.get(side).focus();
                return;
            }
            VirtualFlow<?> flow = (VirtualFlow<?>) table.lookup(".virtual-flow");
            for (row += direction; row >= 0 && row < table.getItems().size(); row += direction) {
                flow.scrollTo(row);
                table.layout();
                IndexedCell<?> rowCell = flow.getVisibleCell(row);
                List<CellTextField> fields = rowCell == null ? List.of() : fieldsIn(rowCell, column);
                if (!fields.isEmpty()) {
                    (direction > 0 ? fields.getFirst() : fields.getLast()).focus();
                    return;
                }
            }
        }

        private static List<CellTextField> fieldsIn(Node node, TableColumn<?, ?> column) {
            return node.lookupAll("." + STYLE_CLASS).stream()
                .map(CellTextField.class::cast)
                .filter(field -> field.commit != null && field.cell.getTableColumn() == column)
                .toList();
        }

        private void focus() {
            requestFocus();
            selectAll();
        }
    }

    /**
     * A choice box editing one value of a cell, applied when picked.
     */
    static final class CellChoiceBox<T> extends ChoiceBox<T> implements ChoiceEditor<T> {
        private final ChoiceBinding<T> binding;

        CellChoiceBox(TableCell<?, ?> cell, KeyedFlash flash, List<T> choices, StringConverter<T> converter) {
            super(FXCollections.observableArrayList(choices));
            if (converter != null) {
                setConverter(converter);
            }
            setMaxWidth(Double.MAX_VALUE);
            focusedProperty().addListener((_, _, focused) -> {
                if (focused) {
                    selectCell(cell);
                }
            });
            binding = new ChoiceBinding<>(this, flash, valueProperty());
        }

        @Override
        public void bind(Object key, Supplier<T> getter, Consumer<T> setter) {
            binding.bind(key, getter, setter);
        }

        @Override
        public void unbind() {
            binding.unbind();
        }
    }

    /**
     * A combo box with a search field, editing one value of a cell, applied when picked.
     */
    static final class CellSearchableComboBox<T> extends SearchableComboBox<T> implements ChoiceEditor<T> {
        private final ChoiceBinding<T> binding;

        CellSearchableComboBox(TableCell<?, ?> cell, KeyedFlash flash, List<T> choices, StringConverter<T> converter) {
            super(FXCollections.observableArrayList(choices));
            setConverter(converter);
            setMaxWidth(Double.MAX_VALUE);
            // the skin's search field takes the focus, not the combo box
            focusWithinProperty().addListener((_, _, focused) -> {
                if (focused) {
                    selectCell(cell);
                }
            });
            binding = new ChoiceBinding<>(this, flash, valueProperty());
        }

        @Override
        public void bind(Object key, Supplier<T> getter, Consumer<T> setter) {
            binding.bind(key, getter, setter);
        }

        @Override
        public void unbind() {
            binding.unbind();
        }
    }

    /**
     * An editor of one value of a cell among choices.
     */
    interface ChoiceEditor<T> extends CellEditor {
        void bind(Object key, Supplier<T> getter, Consumer<T> setter);
    }

    // A choice editor's value, bound to the row (or row side) its cell shows.
    private static final class ChoiceBinding<T> {
        private final Node editor;
        private final KeyedFlash flash;
        private final ObjectProperty<T> value;
        private Object key;
        private Supplier<T> getter;
        private Consumer<T> setter;
        private boolean showingValue;

        private ChoiceBinding(Node editor, KeyedFlash flash, ObjectProperty<T> value) {
            this.editor = editor;
            this.flash = flash;
            this.value = value;
            value.addListener((_, _, newValue) -> {
                if (!showingValue && getter != null && !Objects.equals(newValue, getter.get())) {
                    Object edited = key;
                    CellFields.apply(editor, flash, edited, "", () -> setter.accept(newValue));
                    // reverting a rejected value from within this change would leave the skin's popup checking it
                    Platform.runLater(() -> {
                        if (getter != null && Objects.equals(key, edited)) {
                            showValue();
                        }
                    });
                }
            });
        }

        private void bind(Object key, Supplier<T> getter, Consumer<T> setter) {
            this.key = key;
            this.getter = getter;
            this.setter = setter;
            showValue();
            flash.show(editor, key);
        }

        private void unbind() {
            key = null;
            getter = null;
            setter = null;
            flash.show(editor, null);
        }

        private void showValue() {
            showingValue = true;
            value.set(getter.get());
            showingValue = false;
        }
    }

    /**
     * An integer field between bounds, with plus/minus buttons, each hidden at its bound.
     */
    static final class IntStepper extends HBox implements CellEditor {
        private static final double FIELD_WIDTH = 44;

        private final CellTextField field;
        private final Button incrementButton = stepButton("mdi2p-plus");
        private final Button decrementButton = stepButton("mdi2m-minus");
        private IntSupplier getter;
        private int min;
        private int max;

        IntStepper(TableCell<?, ?> cell, KeyedFlash flash) {
            super(2);
            field = new CellTextField(cell, flash);
            field.setMinWidth(FIELD_WIDTH);
            field.setPrefWidth(FIELD_WIDTH);
            field.setMaxWidth(FIELD_WIDTH);
            field.setAlignment(Pos.CENTER_RIGHT);
            // after every display, the value being shown then
            field.textProperty().addListener(_ -> updateButtons());
            incrementButton.setOnAction(_ -> field.apply(String.valueOf(getter.getAsInt() + 1)));
            decrementButton.setOnAction(_ -> field.apply(String.valueOf(getter.getAsInt() - 1)));
            VBox buttons = new VBox(incrementButton, decrementButton);
            buttons.setAlignment(Pos.CENTER);
            getChildren().addAll(field, buttons);
            setAlignment(Pos.CENTER_LEFT);
        }

        void bind(Object key, int min, int max, IntSupplier getter, IntConsumer setter) {
            this.min = min;
            this.max = max;
            this.getter = getter;
            field.bind(key, () -> String.valueOf(getter.getAsInt()), text -> setter.accept(Math.clamp(Integer.parseInt(text.trim()), min, max)));
            updateButtons();
        }

        @Override
        public void unbind() {
            getter = null;
            field.unbind();
        }

        private void updateButtons() {
            int value = getter == null ? min : getter.getAsInt();
            incrementButton.setVisible(getter != null && value < max);
            decrementButton.setVisible(getter != null && value > min);
        }

        private static Button stepButton(String icon) {
            FontIcon fontIcon = new FontIcon(icon);
            fontIcon.setIconSize(8);
            Button button = new Button();
            button.setGraphic(fontIcon);
            button.getStyleClass().addAll("icon-button", "stepper-button");
            button.setFocusTraversable(false);
            return button;
        }
    }
}
