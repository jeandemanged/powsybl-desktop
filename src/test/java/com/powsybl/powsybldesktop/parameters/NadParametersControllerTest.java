/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.nad.svg.EdgeInfoEnum;
import com.powsybl.nad.svg.SvgParameters;
import com.powsybl.nad.svg.iidm.DefaultLabelProviderFactory;
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
    private ObjectProperty<DesktopNadParameters> params;
    private AtomicInteger changeCount;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/parameters/nad-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        params = new SimpleObjectProperty<>(new DesktopNadParameters());
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
        assertEquals(14, categories.getItems().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void choosingLayoutAndEdgeInfoUpdatesModel() {
        GridPane layoutGrid = selectCategory(Messages.get("parameters.nad.category.layout"));
        ChoiceBox<Object> layoutChoice = (ChoiceBox<Object>) controlForLabel(layoutGrid, Messages.get("parameters.nad.param.layoutAlgorithm.label"));
        interact(() -> layoutChoice.setValue(DesktopNadParameters.LayoutAlgorithm.GEOGRAPHICAL));
        assertEquals(DesktopNadParameters.LayoutAlgorithm.GEOGRAPHICAL, params.get().getLayoutAlgorithm());

        GridPane labelsGrid = selectCategory(Messages.get("parameters.nad.category.styleLabels"));
        ChoiceBox<Object> middleChoice = (ChoiceBox<Object>) controlForLabel(labelsGrid, Messages.get("parameters.nad.param.infoMiddleSide1.label"));
        interact(() -> middleChoice.setValue(EdgeInfoEnum.CURRENT));
        var edgeInfo = ((DefaultLabelProviderFactory) params.get().getLabelProviderFactory()).getParameters().getEdgeInfoParameters();
        assertEquals(EdgeInfoEnum.CURRENT, edgeInfo.infoMiddleSide1());
        assertEquals(EdgeInfoEnum.ACTIVE_POWER, edgeInfo.infoSideExternal());
        assertEquals(2, changeCount.get());
    }

    @Test
    void editingAtlas2ValueKeepsOthersAndRejectsInvalidOnes() {
        GridPane grid = selectCategory(Messages.get("parameters.nad.category.forceLayout"));
        TextField repulsion = (TextField) controlForLabel(grid, Messages.get("parameters.nad.param.atlas2RepulsionIntensity.label"));
        TextField swing = (TextField) controlForLabel(grid, Messages.get("parameters.nad.param.atlas2SwingTolerance.label"));
        int maxSteps = params.get().getAtlas2Parameters().getMaxSteps();

        interact(() -> {
            repulsion.setText("7.5");
            repulsion.getOnAction().handle(null);
            swing.setText("-1");
            swing.getOnAction().handle(null);
        });

        assertEquals(7.5, params.get().getAtlas2Parameters().getRepulsionIntensity());
        assertEquals(maxSteps, params.get().getAtlas2Parameters().getMaxSteps());
        assertEquals(1.0, params.get().getAtlas2Parameters().getSwingTolerance());
        assertEquals("1.0", swing.getText());
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
