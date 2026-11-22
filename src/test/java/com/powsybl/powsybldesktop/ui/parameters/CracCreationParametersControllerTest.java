/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.CracCreationParametersSupport;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracCreationParametersControllerTest extends AbstractHeadlessApplicationTest {

    private CracCreationParametersController controller;
    private ParametersModel parametersModel;
    private final List<Path> failedImports = new ArrayList<>();

    @TempDir
    Path tempDir;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/parameters/crac-creation-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        parametersModel = new ParametersModel();
        controller.setParametersModel(parametersModel);
        controller.setOnImportFailed((path, _) -> failedImports.add(path));
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private CracCreationParameters parameters() {
        return parametersModel.cracCreationParametersProperty().get();
    }

    @SuppressWarnings("unchecked")
    private <N extends Node> N control(String param) {
        String text = Messages.get("cracCreation.param." + param + ".label");
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
    void editsTheDefaultMonitoredLineSide() {
        ChoiceBox<CracCreationParameters.MonitoredLineSide> side = control("monitoredLineSide");
        assertEquals(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_BOTH_SIDES, side.getValue());

        interact(() -> side.setValue(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_ONE));

        assertEquals(Set.of(TwoSides.ONE), parameters().getDefaultMonitoredSides());
        assertEquals(1, parametersModel.parametersRevisionProperty().get());
    }

    // the two usage limits tables make one map
    @Test
    void editsTheUsageLimitsInBothTables() {
        edit("usageLimits", 0, 0, "curative");
        edit("usageLimits", 0, 1, "3");
        edit("usageLimitsPerTso", 0, 0, "preventive");
        edit("usageLimitsPerTso", 0, 1, "FR");
        edit("usageLimitsPerTso", 0, 3, "2");

        assertEquals(3, parameters().getRaUsageLimitsPerInstant().get("curative").getMaxRa());
        assertEquals(2, parameters().getRaUsageLimitsPerInstant().get("preventive").getMaxPstPerTso().get("FR"));
        assertNull(controller.table("usageLimitsPerTso").getError());
    }

    @Test
    void anInvalidRowShowsWhyAndLeavesTheParameters() {
        edit("usageLimits", 0, 0, "curative");
        edit("usageLimits", 0, 1, "3");

        edit("usageLimits", 0, 1, "three");

        assertEquals(Messages.get("cracCreation.row.invalid", 1, Messages.get("cracCreation.row.integer", 2, "three")),
                controller.table("usageLimits").getError());
        assertEquals(3, parameters().getRaUsageLimitsPerInstant().get("curative").getMaxRa());
    }

    // an extension is added once one of its values is set, not by reading it
    @Test
    void addsAFormatsExtensionOnceEdited() {
        assertNull(parameters().getExtension(NcCracCreationParameters.class));
        assertEquals(3, controller.table("curativeInstants").getRows().size());

        ChoiceBox<String> region = control("region");
        interact(() -> region.setValue(CapacityCalculationRegion.CORE.name()));

        assertEquals(CapacityCalculationRegion.CORE, parameters().getExtension(NcCracCreationParameters.class).getCapacityCalculationRegion());
        assertNull(parameters().getExtension(CimCracCreationParameters.class));
    }

    @Test
    void showsReplacedParametersAndExchangesOpenRaoJson() throws IOException {
        CracCreationParameters replacement = new CracCreationParameters();
        CracCreationParametersSupport.setTimeseriesMrids(CracCreationParametersSupport.cim(replacement), List.of(List.of("ts1")));
        interact(() -> parametersModel.cracCreationParametersProperty().set(replacement));
        assertEquals(List.of(List.of("ts1")), controller.table("timeseries").getRows());

        Path file = tempDir.resolve("crac-creation.json");
        interact(() -> controller.exportTo(file));
        interact(() -> parametersModel.cracCreationParametersProperty().set(new CracCreationParameters()));
        assertEquals(List.of(), controller.table("timeseries").getRows());
        interact(() -> controller.importFrom(file));
        assertEquals(List.of(List.of("ts1")), controller.table("timeseries").getRows());
        assertTrue(parameters().getExtension(CimCracCreationParameters.class).getTimeseriesMrids().contains("ts1"));

        Path invalid = tempDir.resolve("invalid.json");
        Files.writeString(invalid, "{\"crac-factory\": 42, \"nonsense\": true}");
        CracCreationParameters before = parameters();
        interact(() -> controller.importFrom(invalid));
        assertEquals(List.of(invalid), failedImports);
        assertSame(before, parameters());
    }
}
