/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.iidm.network.Country;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.CracGeneratorSettings;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.ui.controls.SearchableCheckComboBox;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracGeneratorParametersControllerTest extends AbstractHeadlessApplicationTest {

    private CracGeneratorParametersController controller;
    private ParametersModel parametersModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/parameters/crac-generator-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        parametersModel = new ParametersModel();
        controller.setParametersModel(parametersModel);
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private CracGeneratorSettings settings() {
        return parametersModel.cracGeneratorSettingsProperty().get();
    }

    @SuppressWarnings("unchecked")
    private <N extends Node> N control(String param) {
        String text = Messages.get("cracGenerator.param." + param + ".label");
        for (GridPane grid : controller.categoryGrids.values()) {
            for (Node node : grid.getChildren()) {
                if (node instanceof Label label && text.equals(label.getText())) {
                    Integer row = GridPane.getRowIndex(label);
                    return (N) grid.getChildren().stream()
                            .filter(other -> Objects.equals(GridPane.getRowIndex(other), row) && Objects.equals(GridPane.getColumnIndex(other), 1))
                            .findFirst().orElseThrow();
                }
            }
        }
        throw new AssertionError("No field " + param);
    }

    // as a cell's edit commits
    @SuppressWarnings("unchecked")
    private void edit(String param, int row, int column, String value) {
        RowsTableEditor editor = controller.table(param);
        TableView<ObservableList<String>> table = editor.getTable();
        TableColumn<ObservableList<String>, String> tableColumn = (TableColumn<ObservableList<String>, String>) table.getColumns().get(column);
        interact(() -> {
            while (table.getItems().size() <= row) {
                table.getItems().add(FXCollections.observableArrayList(new ArrayList<>(Collections.nCopies(table.getColumns().size(), ""))));
            }
            tableColumn.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(table, new TablePosition<>(table, row, tableColumn),
                    TableColumn.editCommitEvent(), value));
        });
    }

    @Test
    void showsTheDefaults() {
        assertEquals(List.of(List.of("curative")), controller.table("curativeInstants").getRows());
        assertEquals(List.of(List.of("outage", "60"), List.of("curative", "")), controller.table("limitDurations").getRows());
        assertFalse(controller.problemsLabel.isVisible());
    }

    // a curative instant without acceptable duration
    @Test
    void tellsTheProblemsAsTheyAppear() {
        edit("curativeInstants", 1, 0, "curative 2");

        assertEquals(List.of("curative", "curative 2"), settings().getCurativeInstants());
        assertTrue(controller.problemsLabel.isVisible());
        assertEquals(Messages.get("cracGenerator.problem.durationsMissing", "curative 2"), controller.problemsLabel.getText());

        edit("limitDurations", 2, 0, "curative 2");
        assertFalse(controller.problemsLabel.isVisible());
        assertEquals(List.of("curative 2", ""), settings().getLimitDurations().get(2));
    }

    @Test
    void anInvalidRowLeavesTheSettings() {
        edit("pstTapRanges", 0, 0, "preventive");
        edit("pstTapRanges", 0, 1, "-5");
        edit("pstTapRanges", 0, 2, "5");
        edit("pstTapRanges", 0, 3, "ABSOLUTE");
        assertEquals(List.of(List.of("preventive", "-5", "5", "ABSOLUTE")), settings().getPstTapRanges());

        edit("pstTapRanges", 0, 3, "SOMETIMES");

        assertTrue(controller.table("pstTapRanges").getError().contains("SOMETIMES"), controller.table("pstTapRanges").getError());
        assertEquals(List.of(List.of("preventive", "-5", "5", "ABSOLUTE")), settings().getPstTapRanges());
    }

    @Test
    void editsOptionalNumbersAndCountries() {
        TextField minV = control("optimizedMinV");
        interact(() -> {
            minV.setText("225");
            minV.fireEvent(new ActionEvent());
        });
        assertEquals(225., settings().getOptimizedMinV());
        interact(() -> {
            minV.setText(" ");
            minV.fireEvent(new ActionEvent());
        });
        assertNull(settings().getOptimizedMinV());

        SearchableCheckComboBox<Country> countries = control("cnecCountries");
        interact(() -> countries.setChecked(List.of(Country.FR, Country.BE)));
        assertEquals(Set.of(Country.FR, Country.BE), Set.copyOf(settings().getCnecCountries()));
    }

    @Test
    void showsReplacedSettings() {
        CracGeneratorSettings replacement = new CracGeneratorSettings();
        replacement.setAutoInstants(List.of("auto"));
        replacement.setRedispatching(true);

        interact(() -> parametersModel.cracGeneratorSettingsProperty().set(replacement));

        assertEquals(List.of(List.of("auto")), controller.table("autoInstants").getRows());
        assertEquals(Messages.get("cracGenerator.problem.durationsMissing", "auto"), controller.problemsLabel.getText());
    }
}
