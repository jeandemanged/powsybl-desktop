/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.nad.NadParameters;
import com.powsybl.nad.svg.SvgParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
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
class NadParametersControllerTest extends AbstractHeadlessApplicationTest {

    private NadParametersController controller;
    private ObjectProperty<NadParameters> params;
    private AtomicInteger changeCount;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/parameters/nad-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        params = new SimpleObjectProperty<>(new NadParameters());
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

    private Control controlForLabel(GridPane grid, String labelText) {
        return (Control) nodeForLabel(grid, labelText);
    }

    @Test
    void categoryListShowsAllExpectedTitles() {
        @SuppressWarnings("unchecked")
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        assertEquals(12, categories.getItems().size());
    }

    @Test
    void toggleHighlightGraphUpdatesModelAndNotifiesChange() {
        GridPane grid = selectCategory(Messages.get("parameters.nad.category.debug"));
        CheckBox checkBox = (CheckBox) controlForLabel(grid, Messages.get("parameters.nad.param.highlightGraph.label"));
        boolean before = params.get().getSvgParameters().isHighlightGraph();

        clickOn(checkBox);

        assertEquals(!before, params.get().getSvgParameters().isHighlightGraph());
        assertEquals(1, changeCount.get());
    }

    // setFixedWidth flips sizeConstraint as a side effect (see SvgParameters); committing the width field
    // must both apply that value and re-sync the mode dropdown's displayed value.
    @Test
    void committingFixedWidthSwitchesSizeConstraintModeAndNotifiesChange() {
        GridPane grid = selectCategory(Messages.get("parameters.nad.category.sizing"));
        HBox box = (HBox) nodeForLabel(grid, Messages.get("parameters.nad.param.sizeConstraint.label"));
        @SuppressWarnings("unchecked")
        ChoiceBox<SvgParameters.SizeConstraint> modeChoiceBox = (ChoiceBox<SvgParameters.SizeConstraint>) box.getChildren().get(0);
        TextField widthField = (TextField) box.getChildren().get(2);

        assertEquals(SvgParameters.SizeConstraint.FIXED_SCALE, params.get().getSvgParameters().getSizeConstraint());

        clickOn(widthField);
        interact(widthField::clear);
        write("500");
        push(KeyCode.TAB);

        assertEquals(500, params.get().getSvgParameters().getFixedWidth());
        assertEquals(SvgParameters.SizeConstraint.FIXED_WIDTH, params.get().getSvgParameters().getSizeConstraint());
        assertEquals(SvgParameters.SizeConstraint.FIXED_WIDTH, modeChoiceBox.getValue());
        assertTrue(changeCount.get() >= 1);
    }
}
