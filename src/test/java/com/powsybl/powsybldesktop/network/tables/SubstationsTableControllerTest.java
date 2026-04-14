/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SubstationsTableControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private SubstationsTableController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/substations-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private Substation substation(String id) {
        return network.getSubstation(id);
    }

    private int rowOf(Substation substation) {
        return controller.currentSubstations.indexOf(substation);
    }

    private Object cellValue(TableColumn<Substation, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node cellGraphic(TableColumn<Substation, Substation> column, int row) {
        TableCell<Substation, Substation> cell = (TableCell<Substation, Substation>) column.getCellFactory().call(column);
        cell.updateTableView(controller.substationsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllSubstationsSortedByName() {
        List<Substation> expected = network.getSubstationStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentSubstations);
        assertEquals(expected.size(), controller.substationsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Substation substation = substation("S1");
        int row = rowOf(substation);

        Hyperlink nameLink = (Hyperlink) cellGraphic(controller.nameColumn, row);
        assertEquals(substation.getNameOrId(), nameLink.getText());
        assertEquals(substation.getNullableCountry(), cellValue(controller.countryColumn, row));
        assertEquals(substation.getTso(), cellValue(controller.tsoColumn, row));
    }

    @Test
    void countryEditCommitSetsAndUnsetsCountry() {
        Substation substation = substation("S1");
        int row = rowOf(substation);

        interact(() -> fireEditCommit(controller.substationsTableView, controller.countryColumn, row, Country.FR));
        assertEquals(Country.FR, substation.getNullableCountry());

        interact(() -> fireEditCommit(controller.substationsTableView, controller.countryColumn, row, null));
        assertNull(substation.getNullableCountry());
    }

    @Test
    void tsoEditCommitUpdatesSubstation() {
        Substation substation = substation("S1");
        int row = rowOf(substation);

        interact(() -> fireEditCommit(controller.substationsTableView, controller.tsoColumn, row, "RTE"));

        assertEquals("RTE", substation.getTso());
    }

    @Test
    void geographicalTagsCellShowsTagsConcatenatedWithSemicolon() {
        Substation substation = substation("S1");
        substation.addGeographicalTag("A");
        substation.addGeographicalTag("B");

        HBox graphic = (HBox) cellGraphic(controller.geographicalTagsColumn, rowOf(substation));
        Label tagsLabel = (Label) graphic.getChildren().get(1);

        assertEquals("A;B", tagsLabel.getText());
    }

    @Test
    void goToSubstationSelectsAndClearsSelection() {
        Substation substation = substation("S2");
        int row = rowOf(substation);

        interact(() -> controller.goToSubstation(substation));
        List<TablePosition> selectedCells = controller.substationsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToSubstation(null));
        assertTrue(controller.substationsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingNameLinkNavigatesToSubstationsView() {
        Substation substation = substation("S3");
        Hyperlink link = (Hyperlink) cellGraphic(controller.nameColumn, rowOf(substation));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
