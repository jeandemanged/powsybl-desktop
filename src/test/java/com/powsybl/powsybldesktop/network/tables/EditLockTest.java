/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.DisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every editor in every table view - TextField-backed cells, check boxes, choice boxes, steppers, info buttons
 * opening edit dialogs - is disabled while the network is busy with a background job (see MainModel.markBusy), and
 * enabled again once it isn't.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class EditLockTest extends AbstractHeadlessApplicationTest {

    private static final List<String> VIEWS = List.of(
            "boundary-lines-view.fxml", "busbar-sections-view.fxml", "buses-bus-breaker-view.fxml", "buses-bus-view.fxml",
            "components-view.fxml", "generators-view.fxml", "lines-view.fxml", "loads-view.fxml",
            "security-analysis-results-view.fxml", "shunt-compensators-view.fxml", "static-var-compensators-view.fxml",
            "substations-view.fxml", "switches-view.fxml", "tie-lines-view.fxml", "transformers-view.fxml",
            "voltage-levels-view.fxml");

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Scene scene = new Scene(new Pane(), 1200, 700);
        scene.getStylesheets().add(getClass().getResource("/com/powsybl/powsybldesktop/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void everyEditorIsDisabledOnlyWhileTheNetworkIsBusy() throws Exception {
        // BE has node/breaker voltage levels, switches, shunts, SVCs, tap changers, boundary lines; merged with NL,
        // tie lines too
        Network network = Network.merge(
                Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource()),
                Network.read(CgmesConformity1Catalog.microGridBaseCaseNL().dataSource()));
        List<String> violations = new ArrayList<>();
        int editorCount = 0;
        for (String view : VIEWS) {
            MainModel mainModel = new MainModel();
            mainModel.addNetwork(network);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/network/tables/" + view), Messages.bundle());
            Parent root = loader.load();
            Object controller = loader.getController();
            interact(() -> {
                stage.getScene().setRoot(root);
                controller.getClass().getMethod("setMainModel", MainModel.class).invoke(controller, mainModel);
                if (controller instanceof SwitchesController switches) {
                    switches.setContainer(network.getVoltageLevelStream()
                            .filter(vl -> vl.getSwitchCount() > 0).findFirst().map(VoltageLevel.class::cast).orElseThrow());
                }
                // every column shown, narrow enough for all of them to fit (and so be rendered) in the window
                root.lookupAll(".table-view").forEach(node -> allColumns(((TableView<?>) node).getColumns()).forEach(c -> {
                    c.setVisible(true);
                    c.setMinWidth(10);
                    c.setPrefWidth(c.getColumns().isEmpty() ? 18 : c.getColumns().size() * 18);
                }));
                return null;
            });
            Runnable[] release = new Runnable[1];
            interact(() -> release[0] = mainModel.markBusy(network));
            WaitForAsyncUtils.waitForFxEvents();
            List<TableCell<?, ?>> editors = editorCells(root);
            editorCount += editors.size();
            editors.stream().filter(cell -> !cell.isDisabled())
                    .forEach(cell -> violations.add(view + " / " + cell.getTableColumn().getText() + ": enabled while busy"));

            interact(release[0]);
            WaitForAsyncUtils.waitForFxEvents();
            editorCells(root).stream().filter(Node::isDisabled)
                    .forEach(cell -> violations.add(view + " / " + cell.getTableColumn().getText() + ": disabled while idle"));
            interact(() -> ((DisposableController) controller).dispose());
        }

        assertEquals(List.of(), violations.stream().distinct().toList());
        // guards against this test silently checking nothing (e.g. no rows rendered)
        assertTrue(editorCount > 100, "only " + editorCount + " editor cells checked");
    }

    private static Stream<TableColumn<?, ?>> allColumns(List<? extends TableColumn<?, ?>> columns) {
        return columns.stream().flatMap(column -> Stream.concat(Stream.of(column), allColumns(column.getColumns())));
    }

    private static List<TableCell<?, ?>> editorCells(Parent root) {
        return root.lookupAll(".table-cell").stream()
                .filter(node -> node instanceof TableCell<?, ?> cell && !cell.isEmpty() && cell.isVisible() && cell.getTableColumn() != null)
                .<TableCell<?, ?>>map(node -> (TableCell<?, ?>) node)
                .filter(EditLockTest::isEditor)
                .toList();
    }

    // shown as editable (the editable-cell box), or holding an input control (buttons included: they open edit
    // dialogs) - hyperlinks only navigate
    private static boolean isEditor(TableCell<?, ?> cell) {
        if (cell.getStyleClass().contains("editable-cell")) {
            return true;
        }
        return cell.lookupAll("*").stream().anyMatch(node -> node.getStyleClass().contains("editable-cell")
                || node instanceof ButtonBase && !(node instanceof Hyperlink)
                || node instanceof ChoiceBox<?> || node instanceof ComboBoxBase<?> || node instanceof TextInputControl
                || node instanceof Spinner<?>);
    }
}
