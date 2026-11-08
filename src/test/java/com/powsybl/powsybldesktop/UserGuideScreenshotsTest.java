/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.parameters.MapViolationLayer;
import com.powsybl.powsybldesktop.ui.form.TableColumnSupport;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.powsybldesktop.violations.VoltageRange;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
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
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not a regression test: walks through the user's guide quick start tutorials in the main window, then through the
 * views, and writes the screenshots the guide shows: of the main view, of a separate window or of a single control.
 * Excluded from the default build, run with {@code mvn test -Pug-screenshots}.
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
    private final List<String> writtenScreenshots = new ArrayList<>();

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
        views(network);
        assertEquals(List.of("quickstart-start", "quickstart-sample-menu", "quickstart-network-loaded", "quickstart-network-map",
                "quickstart-loadflow-run", "quickstart-loadflow-results", "quickstart-loadflow-components", "quickstart-loadflow-sld",
                "quickstart-loadflow-violations", "quickstart-loadflow-map", "quickstart-sa-add-contingencies", "quickstart-sa-contingencies",
                "quickstart-sa-done", "general-notifications", "quickstart-sa-results", "quickstart-sa-flows-by-contingency",
                "quickstart-sa-map", "quickstart-sa-map-no-contingency-voltage", "map-violations-box", "map-voltage-ranges-box",
                "general-main-toolbar", "networks-import-menu", "substations-nad", "substations-lines-tab", "search-box-match",
                "search-box-approximate", "search-box-not-found", "tables-lines", "tables-column-groups", "violations-dialog",
                "details-generator", "details-line-limits", "general-separate-window", "sa-results-voltages",
                "parameters-user-interface", "parameters-load-flow", "reports", "logs"), writtenScreenshots);
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
        interact(controller.notificationsButton::fire);
        Node notificationsPanel = lookup("#notificationsListView").query().getParent().getParent();
        screenshotPart("general-notifications", notificationsPanel, Integer.MAX_VALUE, 140);
        interact(controller.notificationsButton::fire);
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
        interact(() -> contingencyVoltage.setSelected(true));
        waitForWebViews();
        screenshotNode("map-violations-box", lookup("#violationsBox").query());
        screenshotNode("map-voltage-ranges-box", lookup("#baseVoltagesBox").query());
    }

    private void views(Network network) throws TimeoutException {
        screenshotNode("general-main-toolbar", controller.borderPane.getTop().lookup(".tool-bar"));

        interact(controller::onNetworks);
        clickOn("#importMenuButton");
        moveTo(shown(Messages.get("networks.toolbar.import.sampleNetworks")), Motion.HORIZONTAL_FIRST);
        screenshot("networks-import-menu");
        type(KeyCode.ESCAPE, 2);

        VoltageLevel voltageLevel = network.getVoltageLevelStream()
                .max(Comparator.comparingInt(VoltageLevel::getConnectableCount))
                .orElseThrow();
        navigateToContainer(voltageLevel, ContainerNavigationState.ContainerTab.AREA);
        Slider depth = lookup(".slider").match(NodeQueryUtils.isVisible()).query();
        interact(() -> depth.setValue(2));
        waitForWebViews();
        clickOn(lookup("#fitToScreenToggleButton").match(NodeQueryUtils.isVisible()).<Node>query());
        waitForWebViews();
        screenshot("substations-nad");
        navigateToContainer(voltageLevel, ContainerNavigationState.ContainerTab.LINES);
        screenshot("substations-lines-tab");
        searchScreenshots(voltageLevel);

        interact(controller::onLines);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("tables-lines");
        Node solvedValues = lookup((Node node) -> node instanceof CheckBox checkBox && checkBox.isVisible()
                && Messages.get("desktop.common.columnGroup.solvedValues").equals(checkBox.getText())).query();
        screenshotPart("tables-column-groups", solvedValues.getParent(),
                (int) solvedValues.localToScene(solvedValues.getLayoutBounds()).getMaxX() + 8, 60);

        violationsDialog();

        Line overloaded = network.getLine(mainModel.getStudy(network).getViolations().getFlowViolations().keySet().stream()
                .filter(id -> network.getLine(id) != null).sorted().findFirst().orElseThrow());
        Generator generator = network.getGeneratorStream()
                .max(Comparator.comparingDouble(Generator::getMaxP))
                .orElseThrow();
        interact(controller::onGenerators);
        openDetails(generator);
        Stage detailWindow = separateWindow();
        screenshotScene("details-generator", detailWindow.getScene());
        interact(controller::onLines);
        openDetails(overloaded);
        selectTab(detailWindow.getScene().getRoot(), Messages.get("detail.tab.limits"));
        screenshotScene("details-line-limits", detailWindow.getScene());
        selectTab(detailWindow.getScene().getRoot(), Messages.get("detail.tab.main"));
        screenshotScene("general-separate-window", detailWindow.getScene());
        interact(detailWindow::close);

        interact(controller::onSecurityAnalysisResults);
        WaitForAsyncUtils.waitForFxEvents();
        selectTab(root, Messages.get("securityAnalysisResults.tab.voltage", "").replace("()", ""));
        screenshot("sa-results-voltages");

        interact(controller::onParameters);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("parameters-user-interface");
        selectTab(root, Messages.get("desktop.common.loadFlow"));
        screenshot("parameters-load-flow");

        interact(controller::onReports);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("reports");

        interact(controller::onLogs);
        WaitForAsyncUtils.waitForFxEvents();
        screenshot("logs");
    }

    private void navigateToContainer(VoltageLevel voltageLevel, ContainerNavigationState.ContainerTab tab) {
        interact(() -> mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                ContainerNavigationState.create(voltageLevel, tab))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void searchScreenshots(VoltageLevel voltageLevel) throws TimeoutException {
        TextField field = lookup("#searchField").match(NodeQueryUtils.isVisible()).query();
        // the search box root: the field and its buttons, then the status line
        Node searchBox = field.getParent().getParent();
        waitUntil(() -> !field.isDisabled());
        String name = voltageLevel.getNameOrId();
        search(field, name.substring(0, 4));
        screenshotNode("search-box-match", searchBox);
        // a letter replaced: no exact match left, only approximate ones
        search(field, name.substring(0, 2) + "x" + name.substring(3));
        screenshotNode("search-box-approximate", searchBox);
        search(field, "zzqqxxww");
        screenshotNode("search-box-not-found", searchBox);
        search(field, "");
    }

    private void search(TextField field, String text) {
        interact(() -> field.setText(text));
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void violationsDialog() {
        interact(controller::onViolations);
        WaitForAsyncUtils.waitForFxEvents();
        Button info = (Button) lookup((Node node) -> node instanceof Button button && button.getTooltip() != null
                && Messages.get("violations.details").equals(button.getTooltip().getText()) && node.isVisible()).query();
        // the dialog is shown and waited for: fired from interact(), it would never return
        Platform.runLater(info::fire);
        WaitForAsyncUtils.waitForFxEvents();
        Stage dialog = separateWindow();
        screenshotScene("violations-dialog", dialog.getScene());
        interact(dialog::close);
    }

    // Selects the equipment's row in the shown table, then clicks its info button
    private void openDetails(Identifiable<?> equipment) {
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        TableView<Object> table = (TableView<Object>) lookup((Node node) -> node instanceof TableView<?> tableView
                && tableView.getItems().contains(equipment) && node.isVisible()).query();
        interact(() -> {
            table.getSelectionModel().select(equipment);
            table.scrollTo(equipment);
        });
        WaitForAsyncUtils.waitForFxEvents();
        TableRow<?> row = lookup((Node node) -> node instanceof TableRow<?> tableRow && tableRow.getItem() == equipment
                && tableRow.getTableView() == table).query();
        Button info = (Button) row.lookupAll(".icon-button").stream()
                .filter(node -> node instanceof Button button && button.getTooltip() != null
                        && Messages.get("detail.open.tooltip").equals(button.getTooltip().getText()))
                .findFirst().orElseThrow();
        interact(info::fire);
        WaitForAsyncUtils.sleep(1, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Stage separateWindow() {
        return (Stage) Window.getWindows().stream()
                .filter(window -> window != stage && window instanceof Stage && window.isShowing())
                .findFirst().orElseThrow();
    }

    private void selectTab(Node scope, String textStart) {
        interact(() -> {
            Tab tab = scope.lookupAll(".tab-pane").stream()
                    .map(TabPane.class::cast)
                    .flatMap(tabPane -> tabPane.getTabs().stream())
                    .filter(t -> t.getText() != null && t.getText().startsWith(textStart))
                    .findFirst().orElseThrow();
            tab.getTabPane().getSelectionModel().select(tab);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private record BarCase(String situation, Loading loading, VoltageRange range) {
    }

    @Test
    void violationBars() throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        screenshotBars("tables-loading-bars", List.of(
                new BarCase("No violation", new Loading(72, null), null),
                new BarCase("Permanent limit exceeded", loading("permanent", Integer.MAX_VALUE, 1000, 1120), null),
                new BarCase("Temporary limit exceeded (10 min)", loading("10'", 600, 1200, 1350), null),
                new BarCase("Beyond 200 %", loading("1'", 60, 1500, 2300), null)),
                Messages.get("desktop.common.column.loading"), BarCase::loading, null);
        screenshotBars("tables-voltage-bars", List.of(
                new BarCase("Within the limits", null, new VoltageRange(405, 400, 380, 420, null)),
                new BarCase("Below the low limit", null, voltageRange(372, 380, LimitViolationType.LOW_VOLTAGE)),
                new BarCase("Above the high limit", null, voltageRange(428, 420, LimitViolationType.HIGH_VOLTAGE)),
                new BarCase("Below 80 % of nominal", null, voltageRange(310, 380, LimitViolationType.LOW_VOLTAGE)),
                new BarCase("No high limit", null, new VoltageRange(430, 400, 380, Double.NaN, null))),
                Messages.get("buses.column.voltageRange"), null, BarCase::range);
        assertEquals(List.of("tables-loading-bars", "tables-voltage-bars"), writtenScreenshots);
    }

    // A current violation on a line with a 1000 A permanent limit
    private static Loading loading(String limitName, int acceptableDuration, double limit, double value) {
        return Loading.of(new FlowViolation(new LimitViolation("L", null, null, LimitViolationType.CURRENT, limitName,
                acceptableDuration, limit, 1, value, ThreeSides.ONE, null), 1000));
    }

    // A 400 kV bus, limits 380 and 420 kV
    private static VoltageRange voltageRange(double v, double limit, LimitViolationType type) {
        LimitViolation violation = LimitViolation.builder().subject("VL").type(type).limit(limit).value(v).build();
        return new VoltageRange(v, 400, 380, 420, new VoltageViolation(violation, "VL_0", 400));
    }

    // A table of its own with one row per situation, laid out by a scene that's never shown
    private void screenshotBars(String name, List<BarCase> cases, String barTitle,
                                Function<BarCase, Loading> loading, Function<BarCase, VoltageRange> range) {
        interact(() -> {
            TableView<BarCase> table = barsTable(cases, barTitle, loading, range);
            double width = table.getColumns().stream().mapToDouble(TableColumn::getPrefWidth).sum() + 4;
            Scene scene = new Scene(table, width, 28 * (cases.size() + 1) + 4);
            scene.getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
            write(name, SwingFXUtils.fromFXImage(scene.snapshot(null), null));
        });
    }

    private static TableView<BarCase> barsTable(List<BarCase> cases, String barTitle,
                                                Function<BarCase, Loading> loading, Function<BarCase, VoltageRange> range) {
        TableView<BarCase> table = new TableView<>();
        TableColumn<BarCase, String> situation = new TableColumn<>("Situation");
        situation.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().situation()));
        situation.setPrefWidth(280);
        table.getColumns().add(situation);
        GuiParameters guiParameters = new GuiParameters();
        if (loading != null) {
            TableColumn<BarCase, Loading> column = new TableColumn<>(barTitle);
            TableColumnSupport.configureLoadingColumn(column, loading, () -> guiParameters, (_, _) -> { });
            column.setPrefWidth(210);
            table.getColumns().add(column);
        } else {
            TableColumn<BarCase, VoltageRange> column = new TableColumn<>(barTitle);
            TableColumnSupport.configureVoltageRangeColumn(column, range, () -> guiParameters);
            column.setPrefWidth(190);
            table.getColumns().add(column);
        }
        table.getItems().setAll(cases);
        table.setFixedCellSize(28);
        return table;
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
        interact(() -> write(name, composite()));
    }

    /**
     * A single control, e.g. a toolbar or a box of checkboxes, as laid out in its view.
     */
    private void screenshotNode(String name, Node node) {
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            write(name, SwingFXUtils.fromFXImage(node.snapshot(parameters, null), null));
        });
    }

    /**
     * The top left part of a node of the main view, up to the given scene x and height, with the popups showing over it.
     */
    private void screenshotPart(String name, Node node, int maxX, int maxHeight) {
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> {
            Bounds bounds = node.localToScene(node.getLayoutBounds());
            write(name, composite().getSubimage((int) bounds.getMinX(), (int) bounds.getMinY(),
                    Math.min(maxX, (int) bounds.getMaxX()) - (int) bounds.getMinX(), Math.min(maxHeight, (int) bounds.getHeight())));
        });
    }

    /**
     * A separate window or a dialog, whatever part of it the headless screen holds.
     */
    private void screenshotScene(String name, Scene scene) {
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> write(name, SwingFXUtils.fromFXImage(scene.snapshot(null), null)));
    }

    // Read back, so that a file left by an earlier run can't pass for this one's
    private void write(String name, BufferedImage image) {
        try {
            File file = OUTPUT_DIRECTORY.resolve(name + ".png").toFile();
            Files.deleteIfExists(file.toPath());
            assertTrue(ImageIO.write(image, "png", file), name);
            BufferedImage written = ImageIO.read(file);
            assertEquals(image.getWidth(), written.getWidth(), name);
            assertEquals(image.getHeight(), written.getHeight(), name);
            writtenScreenshots.add(name);
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
