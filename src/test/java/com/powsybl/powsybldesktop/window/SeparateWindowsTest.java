/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.window;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.logs.LogsViewController;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.tables.LinesController;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SeparateWindowsTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final Network otherNetwork = IeeeCdfNetworkFactory.create9();

    private MainModel mainModel;
    private SeparateWindows separateWindows;

    @Override
    public void start(Stage stage) {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.addNetwork(otherNetwork);
        mainModel.setNetwork(network);
        separateWindows = new SeparateWindows(mainModel, () -> stage);
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(separateWindows::closeAll);
    }

    private static List<Stage> windows(String title) {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.getTitle() != null && stage.getTitle().endsWith(title))
                .map(Stage.class::cast)
                .toList();
    }

    private void openLines() {
        interact(() -> separateWindows.open("Lines", scene -> SeparateWindows.<LinesController>load("network/tables/lines-view.fxml",
                controller -> controller.setSceneModel(scene))));
    }

    @Test
    void networkBoundWindowClosesWhenTheSelectionChanges() {
        openLines();
        assertEquals(1, windows("Lines").size());

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void networkBoundWindowClosesWhenItsNetworkIsClosed() {
        openLines();

        interact(() -> mainModel.removeNetwork(network));

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void noConfirmationIsNeededWithoutWindowsOnAnotherNetwork() {
        assertTrue(separateWindows.confirmNetworkChange(otherNetwork));
        openLines();
        assertTrue(separateWindows.confirmNetworkChange(network));
    }

    @Test
    void separateTableNavigatesTheMainWindowWithoutRecordingItsOwnState() {
        openLines();
        Line line = network.getLineStream().findFirst().orElseThrow();
        Stage window = windows("Lines").getFirst();
        Hyperlink link = from(window.getScene().getRoot()).lookup(".container-link")
                .match(node -> ((Hyperlink) node).getText().equals(line.getTerminal1().getVoltageLevel().getSubstation().orElseThrow().getNameOrId()))
                .query();

        interact(link::fire);

        var history = mainModel.getNavigationHistory();
        assertEquals(1, history.getPast().size());
        assertEquals(NavigationType.SUBSTATIONS, history.currentEventProperty().get().navigationType());
        assertInstanceOf(ContainerNavigationState.class, history.currentEventProperty().get().state());
    }

    @Test
    void singleInstanceWindowIsReusedAndSurvivesNetworkChanges() {
        Runnable openLogs = () -> separateWindows.openSingleInstance("logs", "Logs", scene -> SeparateWindows.<LogsViewController>load("logs/logs-view.fxml",
                controller -> controller.setModels(mainModel.getLogsModel(), mainModel.getParametersModel())));
        interact(openLogs);
        interact(openLogs);
        assertEquals(1, windows("Logs").size());

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertEquals(1, windows("Logs").size());
    }

    @Test
    void wholeNetworkViewIsOpenedOnceAndListed() {
        Runnable openLines = () -> separateWindows.open("lines", "Lines", scene -> SeparateWindows.<LinesController>load("network/tables/lines-view.fxml",
                controller -> controller.setSceneModel(scene)));
        interact(openLines);
        interact(openLines);

        assertEquals(1, windows("Lines").size());
        assertEquals(List.of("Lines"), separateWindows.getOpenWindows().stream().map(SeparateWindows.OpenWindow::title).toList());

        interact(() -> windows("Lines").getFirst().close());

        assertTrue(separateWindows.getOpenWindows().isEmpty());
    }

    @Test
    void windowsCanBeFocusedOrClosedByKey() throws Exception {
        assertFalse(WaitForAsyncUtils.asyncFx(() -> separateWindows.focusOpen("lines")).get());
        interact(() -> separateWindows.open("lines", "Lines", scene -> SeparateWindows.<LinesController>load("network/tables/lines-view.fxml",
                controller -> controller.setSceneModel(scene))));

        assertTrue(WaitForAsyncUtils.asyncFx(() -> separateWindows.focusOpen("lines")).get());

        interact(() -> separateWindows.close("lines"));

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void closeAllClosesEveryWindow() {
        openLines();

        interact(separateWindows::closeAll);

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void openButtonGoesInTheViewsTopToolbarOrANewOne() {
        ToolBar toolBar = new ToolBar();
        VBox withToolBar = new VBox(toolBar);
        VBox withoutToolBar = new VBox(new StackPane());

        SeparateWindows.addOpenButton(withToolBar, () -> { });
        SeparateWindows.addOpenButton(withoutToolBar, () -> { });

        assertInstanceOf(Button.class, toolBar.getItems().getLast());
        assertInstanceOf(ToolBar.class, withoutToolBar.getChildren().getFirst());
    }
}
