/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.diagram.DiagramPaneController;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContainerDiagramTest extends AbstractHeadlessApplicationTest {

    // generous: the WebView loads and renders slowly while the suite runs a test JVM per core
    private static final long TIMEOUT_SECONDS = 60;

    private final Network network = IeeeCdfNetworkFactory.create14();
    private DiagramPaneController pane;
    private ContainerDiagram diagram;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/diagram/diagram-pane.fxml"), Messages.bundle());
        Parent root = loader.load();
        pane = loader.getController();
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        diagram = new ContainerDiagram(ContainerDiagram.Kind.SINGLE_LINE, pane, SceneModel.main(mainModel));
        stage.setScene(new Scene(root, 900, 700));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(() -> {
            Window.getWindows().stream().filter(ContextMenu.class::isInstance).toList().forEach(Window::hide);
            diagram.dispose();
        });
    }

    // the WebView, its own context menu disabled, doesn't dispatch contextmenu events to the page: ContainerDiagram
    // handles the JavaFX event and asks sld.js which equipment is under the pointer
    @Test
    void rightClickOnAGeneratorOffersItsDetails() throws TimeoutException {
        // the single line diagram's page (sld.js) loaded first: a diagram injected meanwhile would land in the page it replaces
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                pane.getWebView().getEngine().executeScript("typeof equipmentContextMenuAt === 'function'")).get().equals(Boolean.TRUE));
        interact(() -> {
            diagram.setShowing(true);
            diagram.setContainer(network.getVoltageLevel("VL1"));
        });
        String generatorShape = "document.querySelector('.sld-generator path, .sld-generator circle')";
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                pane.getWebView().getEngine().executeScript(generatorShape + " != null")).get().equals(Boolean.TRUE));

        interact(() -> {
            JSObject rect = (JSObject) pane.getWebView().getEngine().executeScript(generatorShape + ".getBoundingClientRect()");
            double zoom = pane.getWebView().getZoom();
            double x = (number(rect, "left") + number(rect, "width") / 2) * zoom;
            double y = (number(rect, "top") + number(rect, "height") / 2) * zoom;
            // the event's coordinates are the scene's
            Point2D scene = pane.getWebView().localToScene(x, y);
            Point2D screen = pane.getWebView().localToScreen(x, y);
            Event.fireEvent(pane.getWebView(), new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, scene.getX(), scene.getY(),
                    screen.getX(), screen.getY(), false, null));
        });
        WaitForAsyncUtils.waitForFxEvents();

        ContextMenu menu = Window.getWindows().stream()
                .filter(window -> window instanceof ContextMenu && window.isShowing())
                .map(ContextMenu.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("No context menu"));
        // then, after a separator, its topology edits and its removal
        assertEquals(Arrays.asList(Messages.get("detail.open.tooltip"), Messages.get("substations.diagram.equipment.showInTable"), null,
                        Messages.get("edit.menu.topology"), null, Messages.get("edit.menu.remove", "Generator \"B1-G\"")),
                menu.getItems().stream().map(MenuItem::getText).toList());
    }

    @Test
    void toolbarDetailsButtonIsEnabledForAShownVoltageLevel() {
        Button details = (Button) pane.getToolBar().getItems().stream()
                .filter(item -> item instanceof Button button && button.getTooltip() != null
                        && Messages.get("substations.diagram.details").equals(button.getTooltip().getText()))
                .findFirst().orElseThrow();
        assertTrue(details.isDisabled());
        interact(() -> diagram.setContainer(network.getVoltageLevel("VL1")));
        assertFalse(details.isDisabled());
    }

    private static double number(JSObject object, String member) {
        return ((Number) object.getMember(member)).doubleValue();
    }
}
