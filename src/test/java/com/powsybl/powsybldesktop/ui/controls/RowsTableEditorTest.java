/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.controls;

import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RowsTableEditorTest extends AbstractHeadlessApplicationTest {

    private RowsTableEditor editor;
    private final List<List<List<String>>> edits = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        editor = new RowsTableEditor(List.of("Name", "Value"));
        // a value must be a number
        editor.setOnEdit(rows -> {
            rows.forEach(row -> {
                if (!row.get(1).isEmpty()) {
                    Integer.parseInt(row.get(1));
                }
            });
            edits.add(rows);
        });
        editor.setRows(List.of(List.of("a", "1")));
        stage.setScene(new Scene(new StackPane(editor), 400, 300));
        stage.show();
    }

    private TableCell<?, ?> cell(int row, int column) {
        WaitForAsyncUtils.waitForFxEvents();
        return from(editor.getTable()).lookup(".table-cell").queryAll().stream()
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> cell.isVisible() && cell.getIndex() == row && cell.getTableColumn() == editor.getTable().getColumns().get(column))
                .findFirst().orElseThrow();
    }

    private void type(int row, int column, String text) {
        interact(() -> editor.getTable().edit(row, editor.getTable().getColumns().get(column)));
        TextField field = (TextField) cell(row, column).getGraphic();
        interact(() -> field.setText(text));
    }

    @Test
    void enterCommits() {
        type(0, 1, "2");
        interact(() -> ((TextField) cell(0, 1).getGraphic()).fireEvent(new ActionEvent()));

        assertEquals(List.of(List.of(List.of("a", "2"))), edits);
        assertEquals("2", cell(0, 1).getText());
    }

    // NumberFormatException is an IllegalArgumentException: the rows are kept as typed, with the error shown
    @Test
    void anInvalidEditShowsTheErrorUntilFixed() {
        type(0, 1, "x");
        interact(() -> ((TextField) cell(0, 1).getGraphic()).fireEvent(new ActionEvent()));

        assertEquals(List.of(), edits);
        assertEquals("For input string: \"x\"", editor.getError());
        assertEquals(List.of(List.of("a", "x")), editor.getRows());

        type(0, 1, "3");
        interact(() -> ((TextField) cell(0, 1).getGraphic()).fireEvent(new ActionEvent()));
        assertNull(editor.getError());
        assertEquals(List.of(List.of(List.of("a", "3"))), edits);
    }

    @Test
    void addsAndRemovesRows() {
        Button add = (Button) editor.lookupAll(".button").stream().findFirst().orElseThrow();
        interact(add::fire);
        // the new row's first cell is being edited: Escape removes the row, still blank, rather than leave it to fail
        // the next edit
        type(1, 0, "b");
        press(KeyCode.ESCAPE);
        release(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of(List.of("a", "1")), editor.getRows());
        assertEquals(List.of(), edits);

        interact(add::fire);
        type(1, 0, "b");
        interact(() -> ((TextField) cell(1, 0).getGraphic()).fireEvent(new ActionEvent()));
        assertEquals(List.of(List.of("a", "1"), List.of("b", "")), editor.getRows());

        interact(() -> editor.getTable().getSelectionModel().clearAndSelect(0));
        Button remove = (Button) editor.lookupAll(".button").stream().skip(1).findFirst().orElseThrow();
        interact(remove::fire);
        assertEquals(List.of(List.of("b", "")), editor.getRows());
        assertEquals(List.of(List.of(List.of("a", "1"), List.of("b", "")), List.of(List.of("b", ""))), edits);
    }

    @Test
    void setRowsReplacesThemAndClearsTheError() {
        type(0, 1, "x");
        interact(() -> ((TextField) cell(0, 1).getGraphic()).fireEvent(new ActionEvent()));

        interact(() -> editor.setRows(List.of(List.of("b"))));

        assertEquals(List.of(List.of("b", "")), editor.getRows());
        assertNull(editor.getError());
    }
}
