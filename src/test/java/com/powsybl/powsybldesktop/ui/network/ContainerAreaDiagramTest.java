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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The network area diagram's clicks, right-click menus and center ring (nad.js).
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
        String line = edgeId(metadata, "LINE_S2S3");

        List<String> items = rightClick("document.getElementById('" + line + "').querySelector('.nad-edge-path')");

        assertEquals(List.of(Messages.get("detail.open.tooltip"), Messages.get("substations.diagram.equipment.showInTable"),
                Messages.get("substations.diagram.equipment.goTo", "S2VL1"), Messages.get("substations.diagram.equipment.goTo", "S3VL1"),
                Messages.get("substations.diagram.area.center", "S3VL1")), items.subList(0, 5));
    }

    @Test
    void centeringOnAVoltageLevelNavigatesToItsAreaDiagram() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");
        String voltageLevel = nodeId(metadata, "S3VL1");

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

    @Test
    void clickingAVoltageLevelCentersTheDiagramOnIt() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");

        click(nodeId(metadata, "S3VL1"));

        assertCenteredOn("S3VL1");
        // the shown center isn't clickable
        assertFalse(diagram.isClickableEquipment(nodeId(metadata, "S2VL1")));
    }

    // S4VL1 is one step beyond the depth: its line from S3VL1 is drawn as a half line
    @Test
    void clickingAHalfLineCentersTheDiagramOnTheVoltageLevelItLeadsTo() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");

        click(edgeId(metadata, "LINE_S3S4"));

        assertCenteredOn("S4VL1");
    }

    @Test
    void thePointerShowsAFingerOverWhatCanBeClicked() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");

        assertEquals("pointer", cursorOver(nodeId(metadata, "S3VL1")));
        assertEquals("pointer", cursorOver(edgeId(metadata, "LINE_S2S3")));
        assertEquals("pointer", cursorOver(edgeId(metadata, "LINE_S3S4")));
        // the shown center
        assertEquals("", cursorOver(nodeId(metadata, "S2VL1")));
    }

    private String cursorOver(String id) throws Exception {
        return (String) WaitForAsyncUtils.asyncFx(() -> pane.getWebView().getEngine().executeScript(
                "document.getElementById('" + id + "').dispatchEvent(new MouseEvent('mouseover', {bubbles: true})); document.body.style.cursor")).get();
    }

    // its details, which open in a separate window, not available here
    @Test
    void aLineIsClickable() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S2VL1");

        assertTrue(diagram.isClickableEquipment(edgeId(metadata, "LINE_S2S3")));
    }

    @Test
    void aRingMovesFromThePreviousCenterToTheNewOne() throws Exception {
        showAreaDiagramOf("S2VL1");
        // kept long enough to be seen here, whatever the load
        WaitForAsyncUtils.asyncFx(() -> pane.getWebView().getEngine().executeScript("HIGHLIGHT_MS = 600000")).get();

        interact(() -> diagram.setContainer(network.getVoltageLevel("S3VL1")));

        String newCenter = "document.getElementById('" + nodeId(metadataOf("S3VL1"), "S3VL1") + "')";
        WaitForAsyncUtils.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(() -> pane.getWebView().getEngine().executeScript("""
                (function () {
                    var rings = document.querySelectorAll('.nad-center-ring');
                    var node = %s;
                    if (rings.length !== 1 || !node) {
                        return false;
                    }
                    var ring = rings[0].getBoundingClientRect();
                    var circle = node.getBoundingClientRect();
                    return Math.abs(ring.left + ring.width / 2 - circle.left - circle.width / 2) < 1
                        && Math.abs(ring.top + ring.height / 2 - circle.top - circle.height / 2) < 1;
                })()""".formatted(newCenter))).get().equals(Boolean.TRUE));
    }

    // inside a winding, drawn unfilled, away from the other one and the phase shifter's arrow: only its stroke would
    // catch the pointer
    @Test
    void aTransformerIsClickableInsideItsWindings() throws Exception {
        DiagramMetadata metadata = showAreaDiagramOf("S1VL2");
        String transformer = edgeId(metadata, "TWT");

        assertEquals(transformer, WaitForAsyncUtils.asyncFx(() -> pane.getWebView().getEngine().executeScript("""
                (function () {
                    var windings = document.getElementById('%s').querySelectorAll('.nad-winding');
                    windings[0].scrollIntoView({block: 'center', inline: 'center'});
                    var first = windings[0].getBoundingClientRect();
                    var second = windings[1].getBoundingClientRect();
                    var x = (first.left + first.right) / 2;
                    var y = (first.top + first.bottom) / 2;
                    return clickableId(document.elementFromPoint(x + (x - (second.left + second.right) / 2) / 2,
                        y + (y - (second.top + second.bottom) / 2) / 2));
                })()""".formatted(transformer))).get());
    }

    private void click(String id) {
        interact(() -> pane.getWebView().getEngine().executeScript(
                "document.getElementById('" + id + "').dispatchEvent(new MouseEvent('click', {bubbles: true}))"));
    }

    private void assertCenteredOn(String voltageLevelId) {
        ContainerNavigationState state = (ContainerNavigationState) mainModel.getNavigationHistory().currentEventProperty().get().state();
        assertSame(network.getVoltageLevel(voltageLevelId), state.getContainer());
        assertEquals(ContainerNavigationState.ContainerTab.AREA, state.getTab());
    }

    private static String nodeId(DiagramMetadata metadata, String equipmentId) {
        return metadata.getNodesMetadata().stream().filter(node -> node.getEquipmentId().equals(equipmentId)).findFirst().orElseThrow().getSvgId();
    }

    private static String edgeId(DiagramMetadata metadata, String equipmentId) {
        return metadata.getEdgesMetadata().stream().filter(edge -> edge.getEquipmentId().equals(equipmentId)).findFirst().orElseThrow().getSvgId();
    }

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
        return metadataOf(voltageLevelId);
    }

    // of the diagram centered on voltageLevelId: the ids an area diagram gives its elements depend on what it draws, not
    // on its layout
    private DiagramMetadata metadataOf(String voltageLevelId) throws IOException {
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
