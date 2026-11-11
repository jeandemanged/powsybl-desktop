/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.nad.svg.metadata.DiagramMetadata;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.diagram.NetworkAreaDiagramRenderer;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.powsybldesktop.ui.diagram.DiagramPaneController;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The network area diagram's right-click menus (nad.js).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContainerAreaDiagramTest extends AbstractHeadlessApplicationTest {

    // generous: the WebView loads and renders slowly while the suite runs a test JVM per core
    private static final long TIMEOUT_SECONDS = 60;

    private final Network network = FourSubstationsNodeBreakerFactory.create();
    private MainModel mainModel;
    private DiagramPaneController pane;
    private ContainerDiagram diagram;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/diagram/diagram-pane.fxml"), Messages.bundle());
        Parent root = loader.load();
        pane = loader.getController();
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        diagram = new ContainerDiagram(ContainerDiagram.Kind.AREA, pane, SceneModel.main(mainModel));
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

    @Test
    void rightClickOnALineOffersItsVoltageLevels() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");
        String line = metadata.getEdgesMetadata().stream().filter(edge -> edge.getEquipmentId().equals("LINE_S2S3")).findFirst().orElseThrow().getSvgId();

        List<String> items = rightClick("document.getElementById('" + line + "').querySelector('.nad-edge-path')");

        assertEquals(List.of(Messages.get("detail.open.tooltip"), Messages.get("substations.diagram.equipment.showInTable"),
                Messages.get("substations.diagram.equipment.goTo", "S2VL1"), Messages.get("substations.diagram.equipment.goTo", "S3VL1"),
                Messages.get("substations.diagram.area.center", "S3VL1")), items.subList(0, 5));
    }

    @Test
    void centeringOnAVoltageLevelNavigatesToItsAreaDiagram() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");
        String voltageLevel = metadata.getNodesMetadata().stream().filter(node -> node.getEquipmentId().equals("S3VL1")).findFirst().orElseThrow().getSvgId();

        List<String> items = rightClick("document.getElementById('" + voltageLevel + "')");
        assertEquals(List.of(Messages.get("detail.open.tooltip"), Messages.get("substations.diagram.area.singleLine"),
                Messages.get("substations.diagram.area.center", "S3VL1")), items.subList(0, 3));

        interact(() -> shownMenu().getItems().get(2).fire());
        ContainerNavigationState state = (ContainerNavigationState) mainModel.getNavigationHistory().currentEventProperty().get().state();
        assertSame(network.getVoltageLevel("S3VL1"), state.getContainer());
        assertEquals(ContainerNavigationState.ContainerTab.AREA, state.getTab());
        // Back returns to the diagram centered where the user was
        List<NavigationEvent> past = mainModel.getNavigationHistory().getPast();
        ContainerNavigationState previous = (ContainerNavigationState) past.get(past.size() - 2).state();
        assertSame(network.getVoltageLevel("S2VL1"), previous.getContainer());
        assertEquals(ContainerNavigationState.ContainerTab.AREA, previous.getTab());
    }

    // the metadata of the shown diagram: the ids an area diagram gives its elements depend on what it draws, not on its layout
    private DiagramMetadata showAreaDiagramOf(String voltageLevelId) throws Exception {
        // the page (nad.js) loaded first: a diagram injected meanwhile would land in the page it replaces
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                pane.getWebView().getEngine().executeScript("typeof equipmentContextMenuAt === 'function'")).get().equals(Boolean.TRUE));
        interact(() -> {
            diagram.setShowing(true);
            diagram.setContainer(network.getVoltageLevel(voltageLevelId));
        });
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() ->
                pane.getWebView().getEngine().executeScript("document.querySelector('.nad-vl-nodes') != null")).get().equals(Boolean.TRUE));
        return NetworkAreaDiagramRenderer.render(network.getVoltageLevel(voltageLevelId), mainModel.getDiagramAreaDepth(),
                ApplicationParametersJson.copy(mainModel.getParametersModel().nadParametersProperty().getValue())).metadata();
    }

    // the WebView, its own context menu disabled, doesn't dispatch contextmenu events to the page: ContainerDiagram
    // handles the JavaFX event and asks nad.js which equipment is under the pointer
    // on the classpath, JSObject resolves to JDK 25's jdk.jsobject, deprecated for removal, rather than JavaFX's own copy
    @SuppressWarnings("removal")
    private List<String> rightClick(String element) {
        interact(() -> {
            pane.getWebView().getEngine().executeScript(element + ".scrollIntoView({block: 'center', inline: 'center'})");
            JSObject rect = (JSObject) pane.getWebView().getEngine().executeScript(element + ".getBoundingClientRect()");
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
        return shownMenu().getItems().stream().map(MenuItem::getText).toList();
    }

    private static ContextMenu shownMenu() {
        return Window.getWindows().stream()
                .filter(window -> window instanceof ContextMenu && window.isShowing())
                .map(ContextMenu.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("No context menu"));
    }

    @SuppressWarnings("removal")
    private static double number(JSObject object, String member) {
        return ((Number) object.getMember(member)).doubleValue();
    }
}
