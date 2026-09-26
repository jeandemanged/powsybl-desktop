/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sld.SldParameters;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SldParametersControllerTest extends AbstractHeadlessApplicationTest {

    private SldParametersController controller;
    private ObjectProperty<SldParameters> params;
    private AtomicInteger changeCount;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/parameters/sld-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        params = new SimpleObjectProperty<>(new SldParameters());
        changeCount = new AtomicInteger();
        controller.setParametersProperty(params);
        controller.setOnChange(changeCount::incrementAndGet);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @SuppressWarnings("unchecked")
    private GridPane selectCategory(String title) {
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        StackPane detail = (StackPane) controller.splitPane.getItems().get(1);
        interact(() -> categories.getSelectionModel().select(title));
        ScrollPane scrollPane = (ScrollPane) detail.getChildren().get(0);
        return (GridPane) scrollPane.getContent();
    }

    private Control controlForLabel(GridPane grid, String labelText) {
        return (Control) nodeForLabel(grid, labelText);
    }

    private Node nodeForLabel(GridPane grid, String labelText) {
        for (Node node : grid.getChildren()) {
            if (node instanceof Label label && labelText.equals(label.getText())) {
                Integer rowIndex = GridPane.getRowIndex(node);
                int row = rowIndex == null ? 0 : rowIndex;
                return grid.getChildren().stream()
                        .filter(n -> !(n instanceof Label))
                        .filter(n -> {
                            Integer r = GridPane.getRowIndex(n);
                            return (r == null ? 0 : r) == row;
                        })
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No control next to label: " + labelText));
            }
        }
        throw new AssertionError("No label found: " + labelText);
    }

    @Test
    void categoryListShowsAllExpectedTitles() {
        @SuppressWarnings("unchecked")
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        assertEquals(13, categories.getItems().size());
    }

    @Test
    void toggleUseNameUpdatesModelAndNotifiesChange() {
        GridPane grid = selectCategory(Messages.get("parameters.sld.category.labels"));
        CheckBox checkBox = (CheckBox) controlForLabel(grid, Messages.get("parameters.sld.param.useName.label"));
        boolean before = params.get().getSvgParameters().isUseName();

        clickOn(checkBox);

        assertEquals(!before, params.get().getSvgParameters().isUseName());
        assertEquals(1, changeCount.get());
    }

    @Test
    void diagramPaddingFourFieldsCommitTogether() {
        GridPane grid = selectCategory(Messages.get("parameters.sld.category.padding"));
        // the padding row is a composite HBox of four TextFields, not a single Control
        HBox box = (HBox) nodeForLabel(grid, Messages.get("parameters.sld.param.diagramPadding.label"));
        TextField leftField = (TextField) box.getChildren().get(1);

        clickOn(leftField);
        interact(leftField::clear);
        write("99");
        push(KeyCode.TAB);

        assertEquals(99.0, params.get().getLayoutParameters().getDiagramPadding().left());
        assertTrue(changeCount.get() >= 1);
    }
}
