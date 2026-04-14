/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ColumnVisibilityToolbarControllerTest extends AbstractHeadlessApplicationTest {

    private ColumnVisibilityToolbarController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/column-visibility-toolbar.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    void configureAppliesEachGroupsDefaultVisibilityAndChecksItsBoxAccordingly() {
        TableColumn<Object, Object> onColumn = new TableColumn<>();
        TableColumn<Object, Object> offColumn = new TableColumn<>();

        interact(() -> controller.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("common.column.name", true, onColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("common.column.p", false, offColumn))));

        assertTrue(onColumn.isVisible());
        assertFalse(offColumn.isVisible());

        List<CheckBox> checkBoxes = controller.root.getItems().stream().map(CheckBox.class::cast).toList();
        assertEquals(2, checkBoxes.size());
        assertTrue(checkBoxes.get(0).isSelected());
        assertFalse(checkBoxes.get(1).isSelected());
    }

    @Test
    void togglingACheckBoxTogglesVisibilityOfEveryColumnInItsGroup() {
        TableColumn<Object, Object> columnA = new TableColumn<>();
        TableColumn<Object, Object> columnB = new TableColumn<>();

        interact(() -> controller.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("common.column.p", false, columnA, columnB))));
        CheckBox checkBox = (CheckBox) controller.root.getItems().get(0);

        interact(checkBox::fire);
        assertTrue(columnA.isVisible());
        assertTrue(columnB.isVisible());

        interact(checkBox::fire);
        assertFalse(columnA.isVisible());
        assertFalse(columnB.isVisible());
    }
}
