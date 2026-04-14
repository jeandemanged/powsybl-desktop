/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.PhaseTapChangerStep;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Modal popup listing every step of a ratio or phase tap changer (rho/r/x/g/b, plus alpha for a phase tap
 * changer), opened from the info button next to that tap changer's spinner in the Transformers table. Every
 * value is editable and writes straight through to the underlying {@link TapChanger#getStep(int)}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class TapChangerStepsDialog {

    private static final StringConverter<Double> DEFAULT_FORMAT = valueFormat("%.2f");
    private static final StringConverter<Double> RHO_FORMAT = valueFormat("%.6f");

    // Duplicated from TableColumnSupport's private constant rather than exposing it, since it's just the CSS
    // class name, not shared behavior.
    private static final String EDITABLE_CELL_STYLE_CLASS = "editable-cell";

    private TapChangerStepsDialog() {
    }

    static void show(Window owner, String transformerName, Integer side, TapChanger<?, ?, ?, ?> tapChanger) {
        boolean phase = tapChanger instanceof PhaseTapChanger;

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get(phase ? "transformers.tapChanger.phaseDialogTitle" : "transformers.tapChanger.ratioDialogTitle"));

        VBox header = new VBox(2);
        header.getChildren().add(new Label(Messages.get("transformers.tapChanger.header.transformer", transformerName)));
        if (side != null) {
            header.getChildren().add(new Label(Messages.get("transformers.tapChanger.header.side", side)));
        }
        header.getChildren().add(loadTapChangingCapabilitiesCheckBox(tapChanger));

        TableView<Integer> stepsTableView = new TableView<>();
        stepsTableView.setEditable(true);
        List<Integer> positions = new ArrayList<>();
        for (int position = tapChanger.getLowTapPosition(); position <= tapChanger.getHighTapPosition(); position++) {
            positions.add(position);
        }
        stepsTableView.getItems().setAll(positions);

        stepsTableView.getColumns().add(readOnlyColumn("transformers.tapChanger.column.tapPosition", String::valueOf));
        if (phase) {
            stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.alpha", DEFAULT_FORMAT,
                    position -> alphaStep(tapChanger, position).getAlpha(),
                    (position, value) -> alphaStep(tapChanger, position).setAlpha(value), stepsTableView));
        }
        stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.rho", RHO_FORMAT,
                position -> tapChanger.getStep(position).getRho(),
                (position, value) -> tapChanger.getStep(position).setRho(value), stepsTableView));
        stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.r", DEFAULT_FORMAT,
                position -> tapChanger.getStep(position).getR(),
                (position, value) -> tapChanger.getStep(position).setR(value), stepsTableView));
        stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.x", DEFAULT_FORMAT,
                position -> tapChanger.getStep(position).getX(),
                (position, value) -> tapChanger.getStep(position).setX(value), stepsTableView));
        stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.g", DEFAULT_FORMAT,
                position -> tapChanger.getStep(position).getG(),
                (position, value) -> tapChanger.getStep(position).setG(value), stepsTableView));
        stepsTableView.getColumns().add(editableColumn("transformers.tapChanger.column.b", DEFAULT_FORMAT,
                position -> tapChanger.getStep(position).getB(),
                (position, value) -> tapChanger.getStep(position).setB(value), stepsTableView));
        stepsTableView.setPrefSize(500, 300);
        stepsTableView.setMaxWidth(Double.MAX_VALUE);
        stepsTableView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(stepsTableView, Priority.ALWAYS);

        VBox content = new VBox(10, header, stepsTableView);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    private static CheckBox loadTapChangingCapabilitiesCheckBox(TapChanger<?, ?, ?, ?> tapChanger) {
        CheckBox checkBox = new CheckBox(Messages.get("transformers.tapChanger.header.loadTapChangingCapabilities"));
        checkBox.setSelected(tapChanger.hasLoadTapChangingCapabilities());
        checkBox.setOnAction(event -> {
            boolean newValue = checkBox.isSelected();
            try {
                tapChanger.setLoadTapChangingCapabilities(newValue);
                TableColumnSupport.flashEditSuccess(checkBox);
            } catch (PowsyblException e) {
                checkBox.setSelected(!newValue);
                TableColumnSupport.notifyEditError(checkBox, e);
            }
        });
        return checkBox;
    }

    private static PhaseTapChangerStep alphaStep(TapChanger<?, ?, ?, ?> tapChanger, int position) {
        return (PhaseTapChangerStep) tapChanger.getStep(position);
    }

    private static TableColumn<Integer, String> readOnlyColumn(String titleKey, Function<Integer, String> valueGetter) {
        TableColumn<Integer, String> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setEditable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        return column;
    }

    private static TableColumn<Integer, Double> editableColumn(String titleKey, StringConverter<Double> format,
                                                                 Function<Integer, Double> valueGetter, BiConsumer<Integer, Double> setter,
                                                                 TableView<Integer> tableView) {
        TableColumn<Integer, Double> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        // Keyed by tap position rather than held on the TableCell instance - see EditableStepCell.commitEdit's
        // comment for why.
        Map<Integer, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new EditableStepCell(format, flashStyles));
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            tableView.refresh();
        });
        return column;
    }

    private static StringConverter<Double> valueFormat(String pattern) {
        return new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return String.format(Locale.ROOT, pattern, value);
            }

            @Override
            public Double fromString(String text) {
                return Double.valueOf(text.trim());
            }
        };
    }

    // The column's onEditCommit handler (fired synchronously by super.commitEdit) refreshes the whole table so
    // every step's dependent display picks up the new value, which can recycle this exact TableCell to a
    // different row before the success flash fades - so, like ShuntCompensatorSectionsDialog's EditableSectionCell,
    // the flash is keyed by tap position (re-derived in updateItem) rather than applied directly to "this".
    private static final class EditableStepCell extends TextFieldTableCell<Integer, Double> {
        private final Map<Integer, String> flashStyles;

        EditableStepCell(StringConverter<Double> format, Map<Integer, String> flashStyles) {
            super(format);
            this.flashStyles = flashStyles;
        }

        @Override
        public void commitEdit(Double newValue) {
            Integer position = getTableRow() == null ? null : getTableRow().getItem();
            try {
                super.commitEdit(newValue);
                if (position != null) {
                    TableColumnSupport.flashKeyed(this, position, flashStyles, TableColumnSupport.EDIT_SUCCESS_STYLE_CLASS);
                }
            } catch (PowsyblException e) {
                TableColumnSupport.notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }

        // The flash clearing runs unconditionally, even when empty (position == null): a cell recycled to an
        // empty filler row otherwise keeps whatever flash class a previous row left on it forever, since nothing
        // else ever revisits that cell to clear it.
        @Override
        public void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            Integer position = getTableRow() == null ? null : getTableRow().getItem();
            getStyleClass().remove(EDITABLE_CELL_STYLE_CLASS);
            if (!empty && position != null) {
                getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            }
            TableColumnSupport.applyKeyedFlash(this, empty ? null : position, flashStyles);
        }
    }
}
