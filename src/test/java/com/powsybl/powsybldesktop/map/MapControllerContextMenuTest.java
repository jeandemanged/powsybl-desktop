/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapControllerContextMenuTest extends AbstractHeadlessApplicationTest {

    // generous: the WebView loads slowly while the suite runs a test JVM per core
    private static final long TIMEOUT_SECONDS = 60;

    private MapController controller;
    private WebView webView;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/map/map-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        webView = (WebView) root.lookup(".web-view");

        MainModel mainModel = new MainModel();
        Network network = IeeeCdfNetworkFactory.create14();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(() -> {
            Window.getWindows().stream().filter(ContextMenu.class::isInstance).toList().forEach(Window::hide);
            controller.dispose();
        });
    }

    // the WebView, its own context menu disabled, doesn't dispatch contextmenu events to the page: MapController
    // handles the JavaFX event and asks map.js where the pointer is
    @Test
    void rightClickOffersANewSubstationHere() throws TimeoutException {
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                webView.getEngine().executeScript("typeof window.controller !== 'undefined'")).get().equals(Boolean.TRUE));

        interact(() -> {
            double x = webView.getWidth() / 2;
            double y = webView.getHeight() / 2;
            // the event's coordinates are the scene's
            Point2D scene = webView.localToScene(x, y);
            Point2D screen = webView.localToScreen(x, y);
            Event.fireEvent(webView, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, scene.getX(), scene.getY(),
                    screen.getX(), screen.getY(), false, null));
        });
        WaitForAsyncUtils.waitForFxEvents();

        ContextMenu menu = Window.getWindows().stream()
                .filter(window -> window instanceof ContextMenu && window.isShowing())
                .map(ContextMenu.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("No context menu"));
        assertEquals(List.of(Messages.get("edit.menu.newSubstationHere")), menu.getItems().stream().map(MenuItem::getText).toList());
    }
}
