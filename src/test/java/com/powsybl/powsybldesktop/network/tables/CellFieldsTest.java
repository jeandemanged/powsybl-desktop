/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CellFieldsTest extends AbstractHeadlessApplicationTest {

    private static final class Row {
        private final String name;
        private double value;

        private Row(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    private static final StringConverter<Double> FORMAT = new StringConverter<>() {
        @Override
        public String toString(Double value) {
            return String.valueOf(value);
        }

        @Override
        public Double fromString(String text) {
            return Double.valueOf(text.trim());
        }
    };

    private final List<Row> rows = new ArrayList<>(IntStream.range(0, 50).mapToObj(i -> new Row("row" + i, i)).toList());
    private TableView<Row> table;
    private TableColumn<Row, Double> valueColumn;
    private int commits;

    @Override
    public void start(Stage stage) {
        table = new TableView<>();
        table.setEditable(true);
        TableColumn<Row, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().name));
        valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().value));
        CellFields.textColumn(valueColumn, FORMAT, row -> true, CellFields.RowKey.ITEM);
        valueColumn.setOnEditCommit(event -> {
            if (event.getNewValue() < 0) {
                throw new PowsyblException("negative");
            }
            event.getRowValue().value = event.getNewValue();
            commits++;
        });
        table.getColumns().setAll(List.of(nameColumn, valueColumn));
        table.getItems().setAll(rows);
        stage.setScene(new Scene(table, 400, 300));
        stage.show();
    }

    private TextField field(Row row) {
        return table.lookupAll(".cell-text-field").stream()
                .map(TextField.class::cast)
                .filter(field -> field.getParent() instanceof TableCell<?, ?> cell && cell.getIndex() == table.getItems().indexOf(row))
                .findFirst()
                .orElseThrow();
    }

    private TextField focusedField() {
        return table.getScene().getFocusOwner() instanceof TextField field ? field : null;
    }

    private void type(TextField field, String text) {
        interact(() -> {
            field.requestFocus();
            field.setText(text);
        });
    }

    private void press(TextField field, KeyCode code) {
        interact(() -> field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    // the first 10 rows reversed
    private List<Row> shuffled() {
        List<Row> shuffled = new ArrayList<>(rows);
        Collections.reverse(shuffled.subList(0, 10));
        return shuffled;
    }

    @Test
    void enterCommitsAndFlashesGreen() {
        Row row = rows.get(1);
        TextField field = field(row);
        type(field, "12.5");
        // The success flash is removed by a 200ms PauseTransition, which a slow CI runner can outlast between
        // interact() returning and the assertion - so check it in the same FX pulse as the commit.
        boolean[] flashed = new boolean[1];
        interact(() -> {
            field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            flashed[0] = field.getStyleClass().contains("edit-success");
        });

        assertEquals(12.5, row.value);
        assertEquals("12.5", field.getText());
        assertTrue(flashed[0]);
        assertSame(field, focusedField());
    }

    @Test
    void escapeRevertsWithoutCommitting() {
        Row row = rows.get(1);
        TextField field = field(row);
        type(field, "99");
        press(field, KeyCode.ESCAPE);
        assertEquals("1.0", field.getText());

        interact(table::requestFocus);
        assertEquals(1.0, row.value);
        assertEquals(0, commits);
    }

    @Test
    void focusLossCommits() {
        Row row = rows.get(2);
        TextField field = field(row);
        type(field, "7");
        interact(table::requestFocus);
        assertEquals(7.0, row.value);
    }

    @Test
    void unparsableOrRejectedValuesFlashRedAndRevert() {
        Row row = rows.get(3);
        TextField field = field(row);
        type(field, "1,5");
        press(field, KeyCode.ENTER);
        assertEquals(3.0, row.value);
        assertEquals("3.0", field.getText());
        assertTrue(field.getStyleClass().contains("edit-error"));

        type(field, "-4");
        press(field, KeyCode.ENTER);
        assertEquals(3.0, row.value);
        assertEquals("3.0", field.getText());
        assertTrue(field.getStyleClass().contains("edit-error"));
    }

    @Test
    void upAndDownCommitThenMoveToTheNeighborRows() {
        TextField field = field(rows.get(4));
        type(field, "40");
        press(field, KeyCode.DOWN);
        assertEquals(40.0, rows.get(4).value);
        assertSame(field(rows.get(5)), focusedField());
        assertEquals(5, table.getSelectionModel().getSelectedIndex());

        press(focusedField(), KeyCode.UP);
        assertSame(field(rows.get(4)), focusedField());
    }

    @Test
    void downScrollsToRowsBelowTheViewport() {
        int last = table.getItems().size() - 1;
        interact(() -> table.scrollTo(last - 5));
        interact(() -> field(rows.get(last - 1)).requestFocus());
        press(focusedField(), KeyCode.DOWN);
        TextField focused = focusedField();
        assertNotNull(focused);
        assertEquals(last, ((TableCell<?, ?>) focused.getParent()).getIndex());
    }

    @Test
    void focusAndTypedTextFollowTheirRowThroughATableRebuild() {
        Row row = rows.get(6);
        type(field(row), "66");
        interact(() -> {
            field(row).positionCaret(1);
            // a rebuild moving rows, as a refresh after a network update re-sorting the table
            table.getItems().setAll(shuffled());
            table.layout();
        });

        TextField focused = focusedField();
        assertSame(field(row), focused);
        assertEquals("66", focused.getText());
        assertEquals(6.0, row.value, "what is being typed is not committed by the rebuild");

        press(focused, KeyCode.ENTER);
        assertEquals(66.0, row.value);
    }

    @Test
    void focusSurvivesATableRefresh() {
        Row row = rows.get(7);
        type(field(row), "77");
        interact(() -> {
            table.refresh();
            table.layout();
        });

        assertSame(field(row), focusedField());
        assertEquals("77", focusedField().getText());
    }

    @Test
    void flashFollowsTheEditedRowThroughATableRebuild() {
        Row row = rows.get(8);
        TextField field = field(row);
        // rejected: the red flash outlasts the rebuild
        type(field, "-1");
        press(field, KeyCode.ENTER);
        boolean[] flashed = new boolean[1];
        List<Node> otherFlashed = new ArrayList<>();
        // checked in the same pulse as the rebuild, well within the flash duration
        interact(() -> {
            table.getItems().setAll(shuffled());
            table.layout();
            flashed[0] = field(row).getStyleClass().contains("edit-error");
            table.lookupAll(".cell-text-field").stream()
                    .filter(node -> node != field(row) && node.getStyleClass().contains("edit-error"))
                    .forEach(otherFlashed::add);
        });

        assertTrue(flashed[0]);
        assertEquals(List.of(), otherFlashed, "only the edited row flashes");
    }

    @Test
    void fieldsAreDisabledWhileTheTableIsNotEditable() {
        TextField field = field(rows.get(0));
        interact(() -> table.setEditable(false));
        assertTrue(field.isDisabled());
        interact(() -> table.setEditable(true));
        assertFalse(field.isDisabled());
    }
}
