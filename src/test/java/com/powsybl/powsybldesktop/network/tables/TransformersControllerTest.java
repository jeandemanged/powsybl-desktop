/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class TransformersControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final Network threeWindingsNetwork = ThreeWindingsTransformerNetworkFactory.create();

    private TransformersController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/transformers-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.addNetwork(threeWindingsNetwork);
        mainModel.setNetwork(network);
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private int rowOf(Identifiable<?> transformer) {
        return controller.currentItems.indexOf(transformer);
    }

    private Object cellValue(TableColumn<Identifiable<?>, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<Identifiable<?>, T> column, int row) {
        TableCell<Identifiable<?>, T> cell = (TableCell<Identifiable<?>, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.transformersTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private VBox cellBox(TableColumn<Identifiable<?>, Identifiable<?>> column, int row) {
        return (VBox) cellGraphic(column, row);
    }

    // Each side of an editable multi-sided double column (RatedU/RatedS) is a StackPane holding a Label
    // (shown at rest) and a TextField (shown while editing) - see TableColumnSupport.editableDoubleField.
    private static Label labelOf(VBox box, int index) {
        return (Label) ((StackPane) box.getChildren().get(index)).getChildren().get(0);
    }

    private static TextField fieldOf(VBox box, int index) {
        return (TextField) ((StackPane) box.getChildren().get(index)).getChildren().get(1);
    }

    // Each side of a tap changer column is a StackPane holding a blank Label (shown at rest, when this side has
    // no such tap changer) and an HBox with the info button (see TapChangerStepsDialog) then the IntStepperField
    // (shown otherwise) - see TableColumnSupport.TapChangerSlot.
    private static boolean isBlankTapChangerSide(VBox box, int index) {
        return ((StackPane) box.getChildren().get(index)).getChildren().get(0).isVisible();
    }

    private static HBox presentTapChangerSideOf(VBox box, int index) {
        return (HBox) ((StackPane) box.getChildren().get(index)).getChildren().get(1);
    }

    private static Button infoButtonOf(VBox box, int index) {
        return (Button) presentTapChangerSideOf(box, index).getChildren().get(0);
    }

    // An IntStepperField (TableColumnSupport) is itself an HBox: a StackPane holding the double-click-to-edit
    // Label/TextField pair (same as editableDoubleField's ratedU/ratedS cells) followed by a VBox holding the
    // increment (top) and decrement (bottom) buttons.
    private static HBox stepperOf(VBox box, int index) {
        return (HBox) presentTapChangerSideOf(box, index).getChildren().get(1);
    }

    private static Label stepperLabelOf(VBox box, int index) {
        return (Label) ((StackPane) stepperOf(box, index).getChildren().get(0)).getChildren().get(0);
    }

    private static TextField stepperFieldOf(VBox box, int index) {
        return (TextField) ((StackPane) stepperOf(box, index).getChildren().get(0)).getChildren().get(1);
    }

    private static Button stepperIncrementButtonOf(VBox box, int index) {
        return (Button) ((VBox) stepperOf(box, index).getChildren().get(1)).getChildren().get(0);
    }

    private static Button stepperDecrementButtonOf(VBox box, int index) {
        return (Button) ((VBox) stepperOf(box, index).getChildren().get(1)).getChildren().get(1);
    }

    private static void doubleClick(Label label) {
        label.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    private static String doubleText(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }

    @Test
    void tableContainsAllTransformersSortedByName() {
        List<Identifiable<?>> expected = new ArrayList<>(network.getTwoWindingsTransformerStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList());
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.transformersTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFieldsForATwoWindingsTransformer() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        int row = rowOf(transformer);

        assertEquals(transformer.getNameOrId(), cellValue(controller.nameColumn, row));

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(transformer.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());

        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, row);
        assertEquals(2, voltageLevelBox.getChildren().size());
        assertEquals(transformer.getTerminal1().getVoltageLevel().getNameOrId(),
                ((Hyperlink) voltageLevelBox.getChildren().get(0)).getText());
        assertEquals(transformer.getTerminal2().getVoltageLevel().getNameOrId(),
                ((Hyperlink) voltageLevelBox.getChildren().get(1)).getText());

        VBox ratedUBox = cellBox(controller.ratedUColumn, row);
        assertEquals(doubleText(transformer.getRatedU1()), labelOf(ratedUBox, 0).getText());
        assertEquals(doubleText(transformer.getRatedU2()), labelOf(ratedUBox, 1).getText());

        VBox ratedSBox = cellBox(controller.ratedSColumn, row);
        assertEquals(1, ratedSBox.getChildren().size());
        assertEquals(doubleText(transformer.getRatedS()), labelOf(ratedSBox, 0).getText());
    }

    @Test
    void connectedColumnShowsBothTerminals() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(transformer));

        assertEquals(2, box.getChildren().size());
        assertEquals(transformer.getTerminal1().isConnected(), ((CheckBox) box.getChildren().get(0)).isSelected());
        assertEquals(transformer.getTerminal2().isConnected(), ((CheckBox) box.getChildren().get(1)).isSelected());
    }

    @Test
    void togglingConnectedCheckBoxesDisconnectsAndReconnectsEachTerminal() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(transformer));
        CheckBox terminal1CheckBox = (CheckBox) box.getChildren().get(0);
        CheckBox terminal2CheckBox = (CheckBox) box.getChildren().get(1);
        assertTrue(transformer.getTerminal1().isConnected());
        assertTrue(transformer.getTerminal2().isConnected());

        interact(terminal1CheckBox::fire);
        assertFalse(transformer.getTerminal1().isConnected());
        assertTrue(transformer.getTerminal2().isConnected());

        interact(terminal2CheckBox::fire);
        assertFalse(transformer.getTerminal1().isConnected());
        assertFalse(transformer.getTerminal2().isConnected());
    }

    @Test
    void doubleClickingRatedUEntersEditModeAndCommitOnEnterUpdatesTheTransformer() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.ratedUColumn, rowOf(transformer));
        Label ratedU1Label = labelOf(box, 0);
        TextField ratedU1Field = fieldOf(box, 0);
        assertTrue(ratedU1Label.isVisible());
        assertFalse(ratedU1Field.isVisible());

        interact(() -> doubleClick(ratedU1Label));
        assertFalse(ratedU1Label.isVisible());
        assertTrue(ratedU1Field.isVisible());
        assertEquals(doubleText(transformer.getRatedU1()), ratedU1Field.getText());

        interact(() -> {
            ratedU1Field.setText("123.45");
            ratedU1Field.getOnAction().handle(new ActionEvent());
        });

        assertEquals(123.45, transformer.getRatedU1());
        assertTrue(ratedU1Label.isVisible());
        assertFalse(ratedU1Field.isVisible());
        assertEquals("123.45", ratedU1Label.getText());
        assertTrue(ratedU1Label.getStyleClass().contains("edit-success"));
    }

    // ratedS may be unset (NaN, displayed as "-"), unlike ratedU - "NaN" is how the user clears it back.
    @Test
    void editingRatedSFieldAcceptsAValueAndNaN() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.ratedSColumn, rowOf(transformer));
        Label ratedSLabel = labelOf(box, 0);
        TextField ratedSField = fieldOf(box, 0);

        interact(() -> doubleClick(ratedSLabel));
        interact(() -> {
            ratedSField.setText("50");
            ratedSField.getOnAction().handle(new ActionEvent());
        });
        assertEquals(50.0, transformer.getRatedS());
        assertEquals("50.00", ratedSLabel.getText());

        interact(() -> doubleClick(ratedSLabel));
        interact(() -> {
            ratedSField.setText("NaN");
            ratedSField.getOnAction().handle(new ActionEvent());
        });
        assertTrue(Double.isNaN(transformer.getRatedS()));
        assertEquals("-", ratedSLabel.getText());
    }

    @Test
    void escapeCancelsRatedUEditWithoutCommitting() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.ratedUColumn, rowOf(transformer));
        Label ratedU1Label = labelOf(box, 0);
        TextField ratedU1Field = fieldOf(box, 0);
        double originalRatedU = transformer.getRatedU1();

        interact(() -> doubleClick(ratedU1Label));
        interact(() -> {
            ratedU1Field.setText("999");
            ratedU1Field.getOnKeyPressed().handle(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        });

        assertEquals(originalRatedU, transformer.getRatedU1());
        assertTrue(ratedU1Label.isVisible());
        assertFalse(ratedU1Field.isVisible());
    }

    @Test
    void componentAndPowerColumnsShowBothTerminals() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        int row = rowOf(transformer);

        VBox ccBox = cellBox(controller.connectedComponentColumn, row);
        assertEquals(String.valueOf(transformer.getTerminal1().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(transformer.getTerminal2().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(1)).getText());

        VBox scBox = cellBox(controller.synchronousComponentColumn, row);
        assertEquals(String.valueOf(transformer.getTerminal1().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(transformer.getTerminal2().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(1)).getText());

        VBox pBox = cellBox(controller.pColumn, row);
        assertEquals(doubleText(transformer.getTerminal1().getP()), ((Label) pBox.getChildren().get(0)).getText());
        assertEquals(doubleText(transformer.getTerminal2().getP()), ((Label) pBox.getChildren().get(1)).getText());

        VBox qBox = cellBox(controller.qColumn, row);
        assertEquals(doubleText(transformer.getTerminal1().getQ()), ((Label) qBox.getChildren().get(0)).getText());
        assertEquals(doubleText(transformer.getTerminal2().getQ()), ((Label) qBox.getChildren().get(1)).getText());
    }

    @Test
    void patlIViolationColumnIsEmptyWhenNotOverloaded() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        assertNull(cellGraphic(controller.patlIViolationColumn, rowOf(transformer)));
    }

    @Test
    void patlIViolationColumnShowsOverloadedIconForATwoWindingsTransformer() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        transformer.getTerminal1().getBusView().getBus().setV(100);
        transformer.getTerminal1().setP(1000).setQ(1000);
        transformer.newCurrentLimits1().setPermanentLimit(0.001).add();
        interact(() -> mainModel.setUpdate());

        Label label = (Label) cellGraphic(controller.patlIViolationColumn, rowOf(transformer));
        assertEquals(Messages.get("common.patlIViolation.overloaded"), label.getText());
    }

    @Test
    void patlIViolationColumnShowsOverloadedIconForAThreeWindingsTransformer() {
        interact(() -> mainModel.setNetwork(threeWindingsNetwork));
        ThreeWindingsTransformer transformer = threeWindingsNetwork.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        ThreeWindingsTransformer.Leg leg1 = transformer.getLeg1();
        leg1.getTerminal().getBusView().getBus().setV(100);
        leg1.getTerminal().setP(1000).setQ(1000);
        leg1.newCurrentLimits().setPermanentLimit(0.001).add();
        interact(() -> mainModel.setUpdate());

        Label label = (Label) cellGraphic(controller.patlIViolationColumn, rowOf(transformer));
        assertEquals(Messages.get("common.patlIViolation.overloaded"), label.getText());
    }

    @Test
    void multiSidedColumnsAreNotSortable() {
        assertFalse(controller.voltageLevelColumn.isSortable());
        assertFalse(controller.ratedUColumn.isSortable());
        assertFalse(controller.ratedSColumn.isSortable());
        assertFalse(controller.connectedColumn.isSortable());
        assertFalse(controller.connectedComponentColumn.isSortable());
        assertFalse(controller.synchronousComponentColumn.isSortable());
        assertFalse(controller.pColumn.isSortable());
        assertFalse(controller.qColumn.isSortable());
        assertFalse(controller.ratioTapChangerColumn.isSortable());
        assertFalse(controller.phaseTapChangerColumn.isSortable());
    }

    @Test
    void tapChangerColumnsAreBlankForATwoWindingsTransformerWithNoTapChanger() {
        interact(() -> {
            controller.ratioTapChangerColumn.setVisible(true);
            controller.phaseTapChangerColumn.setVisible(true);
        });
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        int row = rowOf(transformer);

        VBox ratioBox = cellBox(controller.ratioTapChangerColumn, row);
        assertEquals(1, ratioBox.getChildren().size());
        assertTrue(isBlankTapChangerSide(ratioBox, 0));

        VBox phaseBox = cellBox(controller.phaseTapChangerColumn, row);
        assertEquals(1, phaseBox.getChildren().size());
        assertTrue(isBlankTapChangerSide(phaseBox, 0));
    }

    // Leg1 has no ratio tap changer, leg2 and leg3 do - checks each field sits in the slot matching its own
    // leg rather than all being packed at the top of the cell.
    @Test
    void ratioTapChangerColumnAlignsEachFieldWithItsOwnLegForAThreeWindingsTransformer() {
        interact(() -> {
            mainModel.setNetwork(threeWindingsNetwork);
            controller.ratioTapChangerColumn.setVisible(true);
            controller.phaseTapChangerColumn.setVisible(true);
        });
        ThreeWindingsTransformer transformer = threeWindingsNetwork.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        VBox ratioBox = cellBox(controller.ratioTapChangerColumn, rowOf(transformer));

        assertEquals(3, ratioBox.getChildren().size());
        assertTrue(isBlankTapChangerSide(ratioBox, 0));

        var leg2TapChanger = transformer.getLeg2().getRatioTapChanger();
        assertEquals(String.valueOf(leg2TapChanger.getTapPosition()), stepperLabelOf(ratioBox, 1).getText());
        assertEquals(leg2TapChanger.getTapPosition() < leg2TapChanger.getHighTapPosition(), stepperIncrementButtonOf(ratioBox, 1).isVisible());
        assertEquals(leg2TapChanger.getTapPosition() > leg2TapChanger.getLowTapPosition(), stepperDecrementButtonOf(ratioBox, 1).isVisible());

        var leg3TapChanger = transformer.getLeg3().getRatioTapChanger();
        assertEquals(String.valueOf(leg3TapChanger.getTapPosition()), stepperLabelOf(ratioBox, 2).getText());

        // no leg has a phase tap changer
        VBox phaseBox = cellBox(controller.phaseTapChangerColumn, rowOf(transformer));
        assertEquals(3, phaseBox.getChildren().size());
        for (int side = 0; side < 3; side++) {
            assertTrue(isBlankTapChangerSide(phaseBox, side));
        }
    }

    @Test
    void editingRatioTapChangerFieldUpdatesTheCorrespondingLeg() {
        interact(() -> {
            mainModel.setNetwork(threeWindingsNetwork);
            controller.ratioTapChangerColumn.setVisible(true);
        });
        ThreeWindingsTransformer transformer = threeWindingsNetwork.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        VBox ratioBox = cellBox(controller.ratioTapChangerColumn, rowOf(transformer));
        Label leg2Label = stepperLabelOf(ratioBox, 1);
        TextField leg2Field = stepperFieldOf(ratioBox, 1);

        interact(() -> doubleClick(leg2Label));
        interact(() -> {
            leg2Field.setText("1");
            leg2Field.getOnAction().handle(new ActionEvent());
        });

        assertEquals(1, transformer.getLeg2().getRatioTapChanger().getTapPosition());
    }

    @Test
    void ratioTapChangerFieldHasAnInfoButtonToItsLeft() {
        interact(() -> {
            mainModel.setNetwork(threeWindingsNetwork);
            controller.ratioTapChangerColumn.setVisible(true);
        });
        ThreeWindingsTransformer transformer = threeWindingsNetwork.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        VBox ratioBox = cellBox(controller.ratioTapChangerColumn, rowOf(transformer));
        HBox leg2Cell = presentTapChangerSideOf(ratioBox, 1);

        assertEquals(2, leg2Cell.getChildren().size());
        assertInstanceOf(Button.class, leg2Cell.getChildren().get(0));
        assertInstanceOf(HBox.class, leg2Cell.getChildren().get(1));
        assertEquals(infoButtonOf(ratioBox, 1).getTooltip().getText(), Messages.get("transformers.tapChanger.stepsTooltip"));
    }

    @Test
    void threeWindingsTransformerShowsThreeStackedValuesPerSide() {
        interact(() -> mainModel.setNetwork(threeWindingsNetwork));
        ThreeWindingsTransformer transformer = threeWindingsNetwork.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        int row = rowOf(transformer);

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(transformer.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());

        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, row);
        assertEquals(3, voltageLevelBox.getChildren().size());
        assertEquals(transformer.getLeg1().getTerminal().getVoltageLevel().getNameOrId(),
                ((Hyperlink) voltageLevelBox.getChildren().get(0)).getText());
        assertEquals(transformer.getLeg2().getTerminal().getVoltageLevel().getNameOrId(),
                ((Hyperlink) voltageLevelBox.getChildren().get(1)).getText());
        assertEquals(transformer.getLeg3().getTerminal().getVoltageLevel().getNameOrId(),
                ((Hyperlink) voltageLevelBox.getChildren().get(2)).getText());

        VBox ratedUBox = cellBox(controller.ratedUColumn, row);
        assertEquals(3, ratedUBox.getChildren().size());
        assertEquals(doubleText(transformer.getLeg1().getRatedU()), labelOf(ratedUBox, 0).getText());
        assertEquals(doubleText(transformer.getLeg2().getRatedU()), labelOf(ratedUBox, 1).getText());
        assertEquals(doubleText(transformer.getLeg3().getRatedU()), labelOf(ratedUBox, 2).getText());

        VBox ratedSBox = cellBox(controller.ratedSColumn, row);
        assertEquals(3, ratedSBox.getChildren().size());
        assertEquals(doubleText(transformer.getLeg1().getRatedS()), labelOf(ratedSBox, 0).getText());
        assertEquals(doubleText(transformer.getLeg2().getRatedS()), labelOf(ratedSBox, 1).getText());
        assertEquals(doubleText(transformer.getLeg3().getRatedS()), labelOf(ratedSBox, 2).getText());

        VBox connectedBox = cellBox(controller.connectedColumn, row);
        assertEquals(3, connectedBox.getChildren().size());
    }

    @Test
    void goToTransformerSelectsAndClearsSelection() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().skip(1).findFirst().orElseThrow();
        int row = rowOf(transformer);

        interact(() -> controller.goToTransformer(transformer));
        List<TablePosition> selectedCells = controller.transformersTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToTransformer(null));
        assertTrue(controller.transformersTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToTransformerSelectsCorrectRowWhenTableIsSorted() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.transformersTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.transformersTableView.getItems().indexOf(transformer);
        assertTrue(sortedRow != rowOf(transformer), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToTransformer(transformer));

        List<TablePosition> selectedCells = controller.transformersTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel1 = transformer.getTerminal1().getVoltageLevel();
        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, rowOf(transformer));
        Hyperlink link = (Hyperlink) voltageLevelBox.getChildren().get(0);

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel1, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();
        Substation substation = transformer.getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(transformer));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
