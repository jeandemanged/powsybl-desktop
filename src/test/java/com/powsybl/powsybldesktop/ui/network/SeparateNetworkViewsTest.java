/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.ui.network.tables.LinesController;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SeparateWindows;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Network views shown in a {@link SeparateWindows} window.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SeparateNetworkViewsTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private MainModel mainModel;
    private SeparateWindows separateWindows;

    @Override
    public void start(Stage stage) {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
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

    @Test
    void separateTableNavigatesTheMainWindowWithoutRecordingItsOwnState() {
        interact(() -> separateWindows.open(() -> "Lines", scene -> SeparateWindows.<LinesController>load("ui/network/tables/lines-view.fxml",
                controller -> controller.setSceneModel(scene))));
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

    // the WebView runs the page: the line feeder is clicked through window.controller, as sld.js does
    @Test
    void separateSingleLineDiagramNavigatesWithinItsTab() throws TimeoutException {
        VoltageLevel vl1 = network.getVoltageLevel("VL1");
        String title = Messages.get("desktop.common.singleLineDiagram") + " - ";
        interact(() -> ContainerDiagram.open(separateWindows, false, ContainerDiagram.Kind.SINGLE_LINE, vl1));
        WebView webView = (WebView) windows(title + vl1.getNameOrId()).getFirst().getScene().getRoot().lookup(".web-view");
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> webView.getEngine().executeScript(
                "document.querySelector('[id^=\"idL\"].sld-top-feeder, [id^=\"idL\"].sld-bottom-feeder') != null")).get().equals(Boolean.TRUE));
        int pastEvents = mainModel.getNavigationHistory().getPast().size();

        interact(() -> webView.getEngine().executeScript("var feeder = document.querySelector('[id^=\"idL\"].sld-top-feeder, "
                + "[id^=\"idL\"].sld-bottom-feeder'); window.controller.onFeederTopBottomClick(feeder.id)"));

        assertEquals(1, separateWindows.getOpenWindows().size());
        assertTrue(windows(title + vl1.getNameOrId()).isEmpty());
        assertEquals(1, Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.getTitle() != null && stage.getTitle().contains(title)).count());
        assertEquals(pastEvents, mainModel.getNavigationHistory().getPast().size());
    }
}
