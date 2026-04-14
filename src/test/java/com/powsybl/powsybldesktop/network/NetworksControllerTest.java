/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworksControllerTest extends AbstractHeadlessApplicationTest {

    private final Network ieee14 = IeeeCdfNetworkFactory.create14();
    private final Network ieee118 = IeeeCdfNetworkFactory.create118();

    private NetworksController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/networks-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(ieee14);
        mainModel.addNetwork(ieee118);
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @SuppressWarnings("unchecked")
    private TreeView<Network> treeView() {
        return (TreeView<Network>) lookup(".tree-view").queryAs(TreeView.class);
    }

    @SuppressWarnings("unchecked")
    private TableView<Pair<String, String>> networkInfoTable() {
        return (TableView<Pair<String, String>>) lookup("#networkInfoTable").queryAs(TableView.class);
    }

    private Label idCaptionLabel() {
        return lookup("#idCaptionLabel").queryAs(Label.class);
    }

    private Label idLabel() {
        return lookup("#idLabel").queryAs(Label.class);
    }

    private TextField nameField() {
        return lookup("#nameField").queryAs(TextField.class);
    }

    private Label sourceFormatLabel() {
        return lookup("#sourceFormatLabel").queryAs(Label.class);
    }

    private Label countriesCaptionLabel() {
        return lookup("#countriesCaptionLabel").queryAs(Label.class);
    }

    private Label countriesLabel() {
        return lookup("#countriesLabel").queryAs(Label.class);
    }

    private BorderPane networkDetailsPane() {
        return lookup("#networkDetailsPane").queryAs(BorderPane.class);
    }

    private StackPane noNetworkSelectedPane() {
        return (StackPane) lookup("#noNetworkSelectedPane").queryAs(StackPane.class);
    }

    private void selectNetwork(Network network) {
        interact(() -> mainModel.setNetwork(network));
    }

    @Test
    void treeShowsAddedNetworks() {
        TreeView<Network> tree = treeView();
        assertEquals(2, tree.getRoot().getChildren().size());
        assertEquals(ieee14, tree.getRoot().getChildren().get(0).getValue());
        assertEquals(ieee118, tree.getRoot().getChildren().get(1).getValue());
    }

    @Test
    void clickingATreeCellSelectsThatNetwork() {
        clickOn(ieee118.getNameOrId());

        assertEquals(ieee118, mainModel.getNetwork());
    }

    @Test
    void navigatingWithArrowKeysSelectsThatNetwork() {
        clickOn(ieee14.getNameOrId());
        push(KeyCode.DOWN);

        assertEquals(ieee118, mainModel.getNetwork());
        assertEquals(ieee118.getId(), idLabel().getText());
    }

    @Test
    void navigateToSelectsMatchingTreeItem() {
        interact(() -> controller.navigateTo(ieee14));

        TreeItem<Network> selected = treeView().getSelectionModel().getSelectedItem();
        assertEquals(ieee14, selected.getValue());
    }

    @Test
    void navigateToNullClearsSelection() {
        interact(() -> controller.navigateTo(ieee14));
        interact(() -> controller.navigateTo(null));

        assertNull(treeView().getSelectionModel().getSelectedItem());
    }

    @Test
    void navigateToDoesNotAccumulateSelection() {
        interact(() -> controller.navigateTo(ieee14));
        interact(() -> controller.navigateTo(ieee118));

        assertEquals(List.of(ieee118), treeView().getSelectionModel().getSelectedItems().stream().map(TreeItem::getValue).toList());
    }

    @Test
    void selectingNetworkShowsIdNameSourceFormatAndCountries() {
        selectNetwork(ieee118);

        assertEquals(ieee118.getId(), idLabel().getText());
        assertEquals(ieee118.getOptionalName().orElse(""), nameField().getText());
        assertEquals(ieee118.getSourceFormat(), sourceFormatLabel().getText());
        String expectedCountries = ieee118.getCountries().stream()
                .map(Object::toString).sorted().collect(Collectors.joining(", "));
        assertEquals(expectedCountries, countriesLabel().getText());
    }

    @Test
    void captionLabelsStayAtFullWidthWithAVeryLongId() {
        double captionWidthBefore = idCaptionLabel().getWidth();

        Network longIdNetwork = NetworkFactory.findDefault().createNetwork("id-".repeat(100), "test");
        interact(() -> mainModel.addNetwork(longIdNetwork));
        selectNetwork(longIdNetwork);

        // the caption column is locked to its preferred width (GridPane would otherwise shrink it,
        // truncating "ID:" etc., to make room for the long value)
        assertEquals(captionWidthBefore, idCaptionLabel().getWidth());
    }

    @Test
    void selectingNetworkShowsDetailsPaneAndHidesNoSelectionPane() {
        selectNetwork(ieee14);

        assertTrue(networkDetailsPane().isVisible());
        assertFalse(noNetworkSelectedPane().isVisible());
    }

    @Test
    void countriesRowIsHiddenWhenNetworkHasNoCountries() {
        selectNetwork(ieee14);

        assertFalse(countriesCaptionLabel().isVisible());
        assertFalse(countriesLabel().isVisible());
    }

    @Test
    void countriesRowIsShownWhenNetworkHasCountries() {
        // ieee14 is auto-selected on add, so switch away first to make the reselect below
        // an actual property change that triggers a refresh.
        selectNetwork(ieee118);
        interact(() -> ieee14.getSubstationStream().findFirst().orElseThrow().setCountry(Country.FR));

        selectNetwork(ieee14);

        assertTrue(countriesCaptionLabel().isVisible());
        assertTrue(countriesLabel().isVisible());
        assertEquals("FR", countriesLabel().getText());
    }

    @Test
    void switchingNetworksUpdatesNameField() {
        selectNetwork(ieee14);
        interact(() -> ieee14.setName("Fourteen bus"));
        selectNetwork(ieee118);
        selectNetwork(ieee14);

        assertEquals("Fourteen bus", nameField().getText());

        selectNetwork(ieee118);

        assertEquals(ieee118.getOptionalName().orElse(""), nameField().getText());
    }

    @Test
    void tableOnlyContainsElementCountsNotIdentityFields() {
        selectNetwork(ieee14);

        List<String> keys = networkInfoTable().getItems().stream().map(Pair::getKey).toList();
        assertFalse(keys.contains("ID"));
        assertFalse(keys.contains("Name"));
        assertFalse(keys.contains("Source format"));
        assertTrue(keys.contains("Substations"));
        assertTrue(keys.contains("Generators"));
    }

    @Test
    void tableStartsWithBusCountsInBusBreakerAndBusView() {
        selectNetwork(ieee14);

        List<Pair<String, String>> rows = networkInfoTable().getItems();
        assertEquals("Buses (Bus/Breaker view)", rows.get(0).getKey());
        assertEquals("Buses (Bus/Branch view)", rows.get(1).getKey());

        long busBreakerTotal = ieee14.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusBreakerView().getBusStream()).count();
        long busBreakerMainCC = ieee14.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusBreakerView().getBusStream())
                .filter(Bus::isInMainConnectedComponent).count();
        assertEquals(busBreakerTotal + " (" + busBreakerMainCC + " in main connected component)", rows.get(0).getValue());

        long busViewTotal = ieee14.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusView().getBusStream()).count();
        long busViewMainCC = ieee14.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusView().getBusStream())
                .filter(Bus::isInMainConnectedComponent).count();
        assertEquals(busViewTotal + " (" + busViewMainCC + " in main connected component)", rows.get(1).getValue());
    }

    @Test
    void boundaryLineCountRowShowsPairedAndUnpairedBreakdown() {
        Network networkWithBoundaryLines = EurostagTutorialExample1Factory.createWithTieLine();
        interact(() -> mainModel.addNetwork(networkWithBoundaryLines));
        selectNetwork(networkWithBoundaryLines);

        List<Pair<String, String>> rows = networkInfoTable().getItems();
        String boundaryLinesValue = rows.stream()
                .filter(row -> row.getKey().equals("Boundary lines"))
                .findFirst().orElseThrow().getValue();
        assertEquals("4 (4 paired in 2 tie lines, 0 unpaired)", boundaryLinesValue);
    }

    private static Network networkWithOneBoundaryLine(String networkId, String pairingKey) {
        Network network = NetworkFactory.findDefault().createNetwork(networkId, "test");
        VoltageLevel voltageLevel = network.newSubstation().setId(networkId + "_S").add()
                .newVoltageLevel().setId(networkId + "_VL").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        voltageLevel.getBusBreakerView().newBus().setId(networkId + "_B").add();
        voltageLevel.newBoundaryLine()
                .setId(networkId + "_BL")
                .setP0(0).setQ0(0).setR(1).setX(1).setG(0).setB(0)
                .setPairingKey(pairingKey)
                .setBus(networkId + "_B")
                .add();
        return network;
    }

    @Test
    void tieLineCountRowCountsATieLineStraddlingTwoSubnetworksOnBothSides() {
        Network network1 = networkWithOneBoundaryLine("N1", "PAIR");
        Network network2 = networkWithOneBoundaryLine("N2", "PAIR");
        Network merged = Network.merge("MERGED", network1, network2);
        interact(() -> mainModel.addNetwork(merged));
        selectNetwork(merged.getSubnetwork("N1"));

        List<Pair<String, String>> rows = networkInfoTable().getItems();
        assertEquals("1", rows.stream().filter(row -> row.getKey().equals("Tie lines")).findFirst().orElseThrow().getValue());
    }

    @Test
    void pressingEnterInNameFieldRenamesNetwork() {
        selectNetwork(ieee14);

        clickOn(nameField());
        interact(nameField()::clear);
        write("My Fourteen Bus Network");
        push(KeyCode.ENTER);

        assertEquals("My Fourteen Bus Network", ieee14.getOptionalName().orElse(null));
    }

    @Test
    void losingFocusOnNameFieldRenamesNetwork() {
        selectNetwork(ieee14);

        clickOn(nameField());
        interact(nameField()::clear);
        write("Renamed on blur");
        interact(() -> networkInfoTable().requestFocus());

        assertEquals("Renamed on blur", ieee14.getOptionalName().orElse(null));
    }

    @Test
    void blankNameIsNotCommitted() {
        interact(() -> ieee14.setName("Original name"));
        selectNetwork(ieee14);

        interact(() -> nameField().setText(""));
        interact(() -> networkInfoTable().requestFocus());

        assertEquals("Original name", ieee14.getOptionalName().orElse(null));
    }
}
