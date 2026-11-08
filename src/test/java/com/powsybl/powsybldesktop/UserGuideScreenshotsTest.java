/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.parameters.MapViolationLayer;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.robot.Motion;
import org.testfx.util.NodeQueryUtils;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Not a regression test: walks through the user's guide quick start tutorials in the main window and writes the
 * screenshots the guide shows. Excluded from the default build, run with {@code mvn test -Pug-screenshots}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
@Tag("user-guide")
class UserGuideScreenshotsTest extends AbstractHeadlessApplicationTest {

    private static final Path OUTPUT_DIRECTORY = Path.of(System.getProperty("ug.screenshots.dir", "docs/ug/_generated/images"));

    private static final int WIDTH = 1680;
    private static final int HEIGHT = 1050;

    private MainModel mainModel;
    private MainController controller;
    private Stage stage;
    private Region root;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        mainModel = new MainModel();
        controller = new MainController(mainModel);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> controller);
        root = loader.load();
        root.setPrefSize(WIDTH, HEIGHT);

        // The headless screen is 1000 x 1000 and keeps popups within it: the window must fit in it, at its origin, for
        // menus to open under their button as on a real screen. So the window shows the top left part of a larger main
        // view, laid out at its preferred size by the Pane, and the screenshots are taken from that view.
        Scene scene = new Scene(new Pane(root), 1000, 720);
        scene.getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        stage.setX(0);
        stage.setY(0);
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void quickStartTutorials() throws TimeoutException, IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        Network network = loadSmallGrid();
        runLoadFlow(network);
        runSecurityAnalysis(network);
    }

    private Network loadSmallGrid() throws TimeoutException {
        screenshot("quickstart-start");

        clickOn("#importMenuButton");
        moveTo(shown(Messages.get("networks.toolbar.import.sampleNetworks")), Motion.HORIZONTAL_FIRST);
        String smallNodeBreaker = "smallNodeBreakerWithVoltageLimits (" + Messages.get("networks.toolbar.import.sampleNetworks.geographicalLayout") + ")";
        moveTo(shown(smallNodeBreaker), Motion.HORIZONTAL_FIRST);
        screenshot("quickstart-sample-menu");
        clickOn(shown(smallNodeBreaker), Motion.HORIZONTAL_FIRST);

        waitUntil(() -> mainModel.getNetwork() != null && !mainModel.isBusy(mainModel.getNetwork()));
        screenshot("quickstart-network-loaded");

        openMap();
        screenshot("quickstart-network-map");
        return mainModel.getNetwork();
    }

    private void runLoadFlow(Network network) throws TimeoutException {
        clickOn(shown(Messages.get("main.toolbar.run")), Motion.HORIZONTAL_FIRST);
        moveTo(shown(Messages.get("desktop.common.loadFlow")), Motion.HORIZONTAL_FIRST);
        screenshot("quickstart-loadflow-run");
        clickOn(shown(Messages.get("desktop.common.loadFlow")), Motion.HORIZONTAL_FIRST);
        waitUntil(() -> mainModel.getStudy(network).getLoadFlowResult() != null && !mainModel.isBusy(network));
        screenshot("quickstart-loadflow-results");
        interact(() -> mainModel.getNotificationsModel().clear());

        openTable("main.toolbar.loadflowresult");
        screenshot("quickstart-loadflow-components");

        VoltageLevel voltageLevel = network.getVoltageLevelStream()
                .max(Comparator.comparingInt(VoltageLevel::getConnectableCount))
                .orElseThrow();
        interact(() -> mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                ContainerNavigationState.create(voltageLevel, ContainerNavigationState.ContainerTab.SINGLE_LINE))));
        waitForWebViews();
        Node fitToScreen = lookup("#fitToScreenToggleButton").match(NodeQueryUtils.isVisible()).query();
        clickOn(fitToScreen);
        waitForWebViews();
        screenshot("quickstart-loadflow-sld");

        openTable("main.toolbar.violations");
        screenshot("quickstart-loadflow-violations");

        openMap();
        screenshot("quickstart-loadflow-map");
    }

    private void runSecurityAnalysis(Network network) throws TimeoutException {
        clickOn(shown(Messages.get("main.toolbar.contingencies")), Motion.HORIZONTAL_FIRST);
        clickOn(shown(Messages.get("desktop.common.action.add")), Motion.HORIZONTAL_FIRST);
        moveTo(shown(Messages.get("contingencies.kind.lineCriterion")), Motion.HORIZONTAL_FIRST);
        screenshot("quickstart-sa-add-contingencies");
        clickOn(shown(Messages.get("contingencies.kind.lineCriterion")), Motion.HORIZONTAL_FIRST);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("quickstart-sa-contingencies");

        clickOn(shown(Messages.get("main.toolbar.run")), Motion.HORIZONTAL_FIRST);
        clickOn(shown(Messages.get("desktop.common.securityAnalysis")), Motion.HORIZONTAL_FIRST);
        waitUntil(() -> mainModel.getStudy(network).getSecurityAnalysisResult() != null && !mainModel.isBusy(network));
        screenshot("quickstart-sa-done");
        interact(() -> mainModel.getNotificationsModel().clear());

        openTable("main.toolbar.securityAnalysisResults");
        screenshot("quickstart-sa-results");

        // the tab title ends with its row count
        String flowsByContingency = Messages.get("securityAnalysisResults.tab.flowByContingency", "").replace("()", "");
        Node flowsByContingencyTab = lookup((Node node) -> node instanceof Labeled labeled && labeled.getText() != null
                && labeled.getText().startsWith(flowsByContingency)).query();
        clickOn(flowsByContingencyTab);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("quickstart-sa-flows-by-contingency");

        openMap();
        screenshot("quickstart-sa-map");
        // the Violations box is outside the headless window, which only shows the main view's top left part
        CheckBox contingencyVoltage = (CheckBox) shown(MapViolationLayer.CONTINGENCY_VOLTAGE.getLabel());
        interact(() -> contingencyVoltage.setSelected(false));
        waitForWebViews();
        screenshot("quickstart-sa-map-no-contingency-voltage");
    }

    // Menu labels also exist in menus that aren't showing: only a node of a showing window can be pointed at
    private Node shown(String text) {
        return lookup((Node node) -> node instanceof Labeled labeled && text.equals(labeled.getText())
                && node.isVisible() && node.getScene() != null && node.getScene().getWindow().isShowing()).query();
    }

    private void openTable(String key) {
        clickOn(shown(Messages.get("main.toolbar.tables")), Motion.HORIZONTAL_FIRST);
        clickOn(shown(Messages.get(key)), Motion.HORIZONTAL_FIRST);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openMap() {
        clickOn(shown(Messages.get("main.toolbar.map")), Motion.HORIZONTAL_FIRST);
        waitForWebViews();
    }

    private static void waitUntil(Callable<Boolean> condition) throws TimeoutException {
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, condition);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // Diagrams are rendered off the FX thread then laid out by WebKit, with nothing to observe from here
    private static void waitForWebViews() {
        WaitForAsyncUtils.sleep(3, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void screenshot(String name) {
        WaitForAsyncUtils.waitForFxEvents();
        BufferedImage[] image = new BufferedImage[1];
        interact(() -> image[0] = composite());
        try {
            ImageIO.write(image[0], "png", OUTPUT_DIRECTORY.resolve(name + ".png").toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The main view with the popups (menus, notifications) showing over it, as a user would see it.
     */
    private BufferedImage composite() {
        Scene mainScene = stage.getScene();
        double originX = stage.getX() + mainScene.getX();
        double originY = stage.getY() + mainScene.getY();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), 0, 0, null);
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (window != stage && window.isShowing() && scene != null) {
                // the notifications popup sits in the window's bottom right corner, to be moved to the view's
                double x = window.getX() + scene.getX() - originX;
                double y = window.getY() + scene.getY() - originY;
                if (window.getClass() == Popup.class) {
                    x += WIDTH - mainScene.getWidth();
                    y += HEIGHT - mainScene.getHeight();
                }
                graphics.drawImage(SwingFXUtils.fromFXImage(scene.snapshot(null), null),
                        (int) Math.round(x), (int) Math.round(y), null);
            }
        }
        graphics.dispose();
        return image;
    }
}
