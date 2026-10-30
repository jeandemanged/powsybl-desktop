/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.google.common.io.ByteStreams;
import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Ground;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.powsybldesktop.AbstractNetworkTask;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.diagram.DiagramPaneController;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;
import com.powsybl.powsybldesktop.parameters.DesktopSldParameters;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import com.powsybl.sld.svg.GraphMetadata;
import javafx.animation.PauseTransition;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The single line or network area diagram of one container, shown in a {@link DiagramPaneController}: rendered off
 * the FX thread (latest wins), again on every network update, diagram parameters or decimals change - lazily, only
 * while showing. The single line diagram is interactive: a switch click toggles the switch, a feeder click navigates
 * to the feeder's equipment (see {@link SceneModel#navigate}). Used by {@link SubstationsController}'s two diagram
 * tabs and by separate diagram windows. A feeder click on an equipment with a detail view (see {@link EquipmentDetails})
 * opens it instead, unless the feeder leads to another voltage level (lines, transformers...); a click on a transformer
 * drawn inside the diagram opens its detail view; a right-click on any equipment offers its detail view and where the
 * click leads.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ContainerDiagram extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerDiagram.class);

    public enum Kind {
        SINGLE_LINE,
        AREA
    }

    // click-to-navigate JS bridge (window.controller); the area diagram has no interactivity yet, so its pane keeps
    // DiagramPaneController's default (non-interactive) shell
    private static final String SLD_HTML_SHELL = """
                <html>
                    <script type="text/javascript">%__JS__%</script>
                    <body style='margin: 0'>
                        <div id="svgContainer"></div>
                    </body>
                </html>
            """;

    private record Render(String svg, GraphMetadata metadata) {
    }

    private final Kind kind;
    private final DiagramPaneController pane;
    private final ContextMenu contextMenu = new ContextMenu();
    // opens the shown substation's or voltage level's detail view
    private final Button detailsButton = new Button();
    // screen location of the last context menu request, where the equipment context menu shows
    private double contextMenuX;
    private double contextMenuY;
    private final SceneModel sceneModel;
    private final MainModel mainModel;
    private Container<?> container;
    private boolean showing;
    // set whenever what the diagram shows changes, cleared once its render is requested
    private boolean stale = true;
    // of the displayed single line diagram, to map an SVG node id back to equipment (see the sld.js callbacks)
    private GraphMetadata sldMetadata;
    // last area diagram depth actually rendered with; the slider fires continuously while dragging
    private int depth;
    private Map<GuiParameters.Quantity, Integer> decimals;

    // Everything a render reads is captured on the FX thread in createTask.
    private final Service<Render> renderService = new Service<>() {
        @Override
        protected Task<Render> createTask() {
            Container<?> rendered = container;
            return kind == Kind.SINGLE_LINE ? singleLineTask(rendered) : areaTask(rendered, depth);
        }
    };

    public ContainerDiagram(Kind kind, DiagramPaneController pane, SceneModel sceneModel) {
        this.kind = Objects.requireNonNull(kind);
        this.pane = Objects.requireNonNull(pane);
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        pane.setDiagramFileNameSupplier(() -> container == null ? null : container.getNameOrId());
        detailsButton.setGraphic(new FontIcon("mdi2i-information-outline"));
        detailsButton.setTooltip(new Tooltip(Messages.get("substations.diagram.details")));
        detailsButton.setDisable(true);
        detailsButton.setOnAction(event -> EquipmentDetails.open(sceneModel, container));
        pane.getToolBar().getItems().addAll(new Separator(Orientation.VERTICAL), detailsButton);
        renderService.setOnSucceeded(event -> {
            sldMetadata = renderService.getValue().metadata();
            pane.showDiagram(renderService.getValue().svg());
        });
        renderService.setOnFailed(event -> LOGGER.error(renderService.getException().getMessage(), renderService.getException()));

        var parametersModel = mainModel.getParametersModel();
        if (kind == Kind.SINGLE_LINE) {
            pane.setOnEngineLoaded(() -> ((JSObject) pane.getWebView().getEngine().executeScript("window")).setMember("controller", this));
            pane.loadShell(SLD_HTML_SHELL.replace("%__JS__%", readSldJs()));
            pane.getWebView().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> contextMenu.hide());
            pane.getWebView().addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);
            pane.getToolBar().getItems().add(editMenuButton());
            // remembered across navigation/views (see SceneModel)
            listenerManager.listen(pane.zoomProperty(), (observable, oldValue, newValue) -> sceneModel.diagramZoomProperty().set(newValue.doubleValue()));
            listenerManager.listen(pane.fitToScreenProperty(), (observable, oldValue, newValue) -> sceneModel.diagramFitToScreenProperty().set(newValue));
            pane.restoreZoom(sceneModel.diagramZoomProperty().get(), sceneModel.diagramFitToScreenProperty().get());
            listenerManager.listen(parametersModel.sldParametersRevisionProperty(), (observable, oldValue, newValue) -> refresh());
        } else {
            depth = sceneModel.diagramAreaDepthProperty().get();
            addDepthControl();
            listenerManager.listen(parametersModel.nadParametersRevisionProperty(), (observable, oldValue, newValue) -> refresh());
        }
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
        // GUI parameters also hold e.g. the map basemap: only a decimals change is worth re-rendering
        decimals = parametersModel.getGuiParameters().getDecimals();
        listenerManager.listen(parametersModel.guiParametersRevisionProperty(), (observable, oldValue, newValue) -> {
            Map<GuiParameters.Quantity, Integer> newDecimals = parametersModel.getGuiParameters().getDecimals();
            if (!newDecimals.equals(decimals)) {
                decimals = newDecimals;
                refresh();
            }
        });
    }

    /**
     * Opens {@code container}'s diagram in a separate window: as a tab of {@code group}'s window, or in a new window if
     * {@code group} is null.
     */
    public static void open(SeparateWindows separateWindows, SeparateWindows.Group group, Kind kind, Container<?> container) {
        String titleKey = kind == Kind.SINGLE_LINE ? "desktop.common.singleLineDiagram" : "desktop.common.networkAreaDiagram";
        // the diagram navigates within its window (see showEquipment): its title, and its rebuild on a language
        // change, follow what it shows
        AtomicReference<ContainerDiagram> shown = new AtomicReference<>();
        Supplier<Container<?>> current = () -> shown.get() == null ? container : shown.get().getContainer();
        Supplier<String> title = () -> Messages.get(titleKey) + " - " + current.get().getNameOrId();
        Function<SceneModel, SeparateWindows.View> viewFactory = scene -> {
            FXMLLoader loader = new FXMLLoader(DiagramPaneController.class.getResource("diagram-pane.fxml"), Messages.bundle());
            try {
                Parent root = loader.load();
                ContainerDiagram diagram = new ContainerDiagram(kind, loader.getController(), scene);
                diagram.setContainer(current.get());
                shown.set(diagram);
                diagram.setShowing(true);
                return new SeparateWindows.View(root, diagram);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        String key = kind + "/" + container.getId();
        if (group == null) {
            separateWindows.open(key, title, viewFactory);
        } else {
            separateWindows.open(group, key, title, viewFactory);
        }
    }

    // what can be created in the shown container, as from a right-click outside any equipment
    private MenuButton editMenuButton() {
        MenuButton button = new MenuButton(null, new FontIcon("mdi2p-plus"));
        button.setTooltip(new Tooltip(Messages.get("edit.menu.tooltip")));
        button.setOnShowing(event -> button.getItems().setAll(container == null ? List.of()
                : EditMenus.items(sceneModel, () -> pane.getWebView().getScene().getWindow(), container.getParentNetwork(), container)));
        return button;
    }

    private String readSldJs() {
        try (var stream = getClass().getResourceAsStream("sld.js")) {
            return new String(ByteStreams.toByteArray(Objects.requireNonNull(stream)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Task<Render> singleLineTask(Container<?> rendered) {
        DesktopSldParameters parameters = ApplicationParametersJson.copy(mainModel.getParametersModel().sldParametersProperty().getValue());
        applyDecimals(parameters.getSvgParameters(), mainModel.getParametersModel().getGuiParameters());
        return new AbstractNetworkTask<>(mainModel, rendered.getNetwork()) {
            @Override
            protected Render compute() throws IOException {
                SubstationDiagramRenderer.DiagramRender render = SubstationDiagramRenderer.render(rendered, parameters);
                return new Render(render.svg(), render.metadata());
            }
        };
    }

    private Task<Render> areaTask(Container<?> rendered, int renderedDepth) {
        DesktopNadParameters parameters = ApplicationParametersJson.copy(mainModel.getParametersModel().nadParametersProperty().getValue());
        applyDecimals(parameters.getSvgParameters(), mainModel.getParametersModel().getGuiParameters());
        return new AbstractNetworkTask<>(mainModel, rendered.getNetwork()) {
            @Override
            protected Render compute() throws IOException {
                return new Render(NetworkAreaDiagramRenderer.render(rendered, renderedDepth, parameters), null);
            }
        };
    }

    private static void applyDecimals(com.powsybl.sld.svg.SvgParameters svgParameters, GuiParameters gui) {
        svgParameters.setVoltageValuePrecision(gui.getDecimals(GuiParameters.Quantity.VOLTAGE));
        svgParameters.setPowerValuePrecision(gui.getDecimals(GuiParameters.Quantity.POWER));
        svgParameters.setAngleValuePrecision(gui.getDecimals(GuiParameters.Quantity.ANGLE));
        svgParameters.setCurrentValuePrecision(gui.getDecimals(GuiParameters.Quantity.CURRENT));
        svgParameters.setPercentageValuePrecision(gui.getDecimals(GuiParameters.Quantity.PERCENTAGE));
    }

    private static void applyDecimals(com.powsybl.nad.svg.SvgParameters svgParameters, GuiParameters gui) {
        svgParameters.setVoltageValuePrecision(gui.getDecimals(GuiParameters.Quantity.VOLTAGE));
        svgParameters.setPowerValuePrecision(gui.getDecimals(GuiParameters.Quantity.POWER));
        svgParameters.setAngleValuePrecision(gui.getDecimals(GuiParameters.Quantity.ANGLE));
        svgParameters.setCurrentValuePrecision(gui.getDecimals(GuiParameters.Quantity.CURRENT));
        svgParameters.setPercentageValuePrecision(gui.getDecimals(GuiParameters.Quantity.PERCENTAGE));
    }

    // diagram-pane.fxml only declares the controls common to both diagrams
    private void addDepthControl() {
        Slider depthSlider = new Slider(1, 6, depth);
        depthSlider.setBlockIncrement(1);
        depthSlider.setMajorTickUnit(1);
        depthSlider.setMinorTickCount(0);
        depthSlider.setSnapToTicks(true);
        depthSlider.setShowTickLabels(true);
        depthSlider.setPrefWidth(120);
        depthSlider.setTooltip(new Tooltip(Messages.get("substations.diagram.depth")));
        // debounced: dragging the slider across several depths would otherwise start a render per depth, each
        // running to completion in the background even once superseded (see AbstractNetworkTask)
        PauseTransition depthDebounce = new PauseTransition(Duration.millis(300));
        depthDebounce.setOnFinished(event -> refresh());
        depthSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int newDepth = newValue.intValue();
            if (newDepth != depth) {
                depth = newDepth;
                sceneModel.diagramAreaDepthProperty().set(newDepth);
                depthDebounce.playFromStart();
            }
        });
        pane.getToolBar().getItems().addAll(new Separator(Orientation.VERTICAL), new Label(Messages.get("substations.diagram.depth")), depthSlider);
    }

    public Container<?> getContainer() {
        return container;
    }

    public void setContainer(Container<?> container) {
        if (container != this.container) {
            this.container = container;
            detailsButton.setDisable(!EquipmentDetails.supports(container));
            refresh();
        }
    }

    /**
     * Whether the diagram is visible: a hidden one is only re-rendered once shown again.
     */
    public void setShowing(boolean showing) {
        this.showing = showing;
        if (showing && stale) {
            render();
        }
    }

    private void refresh() {
        stale = true;
        if (showing) {
            render();
        }
    }

    private void render() {
        stale = false;
        if (container != null) {
            renderService.restart();
        } else {
            renderService.cancel();
            sldMetadata = null;
            pane.showNoSelection();
        }
    }

    @SuppressWarnings("unused") // used by JS
    public void onFeederTopBottomClick(String id) {
        GraphMetadata.NodeMetadata node = sldMetadata == null ? null : sldMetadata.getNodeMetadata(id);
        if (node == null) {
            return;
        }
        Identifiable<?> equipment = container.getNetwork().getIdentifiable(node.getEquipmentId());
        if (node.getNextVId() == null && EquipmentDetails.supports(equipment)) {
            EquipmentDetails.open(sceneModel, equipment);
        } else {
            showEquipment(node, equipment);
        }
    }

    /**
     * Whether {@code id} is the node of a busbar section, or of a transformer drawn inside the diagram (not as a
     * feeder, see onFeederTopBottomClick), JS otherwise trying the element's parents.
     */
    @SuppressWarnings("unused") // used by JS
    public boolean isClickableEquipment(String id) {
        Identifiable<?> equipment = equipment(id);
        return equipment instanceof BusbarSection || equipment instanceof TwoWindingsTransformer || equipment instanceof ThreeWindingsTransformer;
    }

    /**
     * Opens the detail view of a {@link #isClickableEquipment} node's equipment.
     */
    @SuppressWarnings("unused") // used by JS
    public void onEquipmentClick(String id) {
        EquipmentDetails.open(sceneModel, equipment(id));
    }

    /**
     * Offers the equipment's detail view (and its HVDC line's, for a converter station), where the equipment leads (a
     * feeder's other end voltage level, or its row in the equipment tables), then what can be created on it (on a
     * busbar section or bus) and its removal, at the right-click location.
     *
     * @return whether {@code id} is an equipment's node, JS otherwise trying the clicked element's parents
     */
    @SuppressWarnings("unused") // used by JS
    public boolean onEquipmentContextMenu(String id) {
        GraphMetadata.NodeMetadata node = sldMetadata == null ? null : sldMetadata.getNodeMetadata(id);
        Identifiable<?> equipment = equipment(id);
        if (equipment == null) {
            return false;
        }
        List<MenuItem> items = new ArrayList<>();
        if (EquipmentDetails.supports(equipment)) {
            MenuItem detailsItem = new MenuItem(Messages.get("detail.open.tooltip"));
            detailsItem.setOnAction(event -> EquipmentDetails.open(sceneModel, equipment));
            items.add(detailsItem);
            if (equipment instanceof HvdcConverterStation<?> station && station.getHvdcLine() != null) {
                MenuItem hvdcItem = new MenuItem(Messages.get("substations.diagram.equipment.hvdcLineDetails"));
                hvdcItem.setOnAction(event -> EquipmentDetails.open(sceneModel, station.getHvdcLine()));
                items.add(hvdcItem);
            }
            MenuItem showItem = new MenuItem(node.getNextVId() != null
                    ? Messages.get("substations.diagram.equipment.goTo", container.getNetwork().getVoltageLevel(node.getNextVId()).getNameOrId())
                    : Messages.get("substations.diagram.equipment.showInTable"));
            showItem.setOnAction(event -> showEquipment(node, equipment));
            items.add(showItem);
        }
        List<MenuItem> edits = EditMenus.items(sceneModel, () -> pane.getWebView().getScene().getWindow(), equipment.getParentNetwork(), equipment);
        if (!items.isEmpty() && !edits.isEmpty()) {
            items.add(new SeparatorMenuItem());
        }
        items.addAll(edits);
        if (items.isEmpty()) {
            return false;
        }
        contextMenu.getItems().setAll(items);
        contextMenu.show(pane.getWebView(), contextMenuX, contextMenuY);
        return true;
    }

    /**
     * Offers what can be created in the shown voltage level or substation, on a right-click outside any equipment.
     */
    @SuppressWarnings("unused") // used by JS
    public void onBackgroundContextMenu() {
        if (container == null) {
            return;
        }
        List<MenuItem> items = EditMenus.items(sceneModel, () -> pane.getWebView().getScene().getWindow(), container.getParentNetwork(), container);
        if (!items.isEmpty()) {
            contextMenu.getItems().setAll(items);
            contextMenu.show(pane.getWebView(), contextMenuX, contextMenuY);
        }
    }

    // the WebView, its own context menu disabled, doesn't dispatch contextmenu events to the page: sld.js finds the
    // equipment under the pointer, given in CSS pixels of the viewport (the WebView's zoom scales them)
    private void onContextMenuRequested(ContextMenuEvent event) {
        contextMenuX = event.getScreenX();
        contextMenuY = event.getScreenY();
        double zoom = pane.getWebView().getZoom();
        pane.getWebView().getEngine().executeScript("equipmentContextMenuAt(" + event.getX() / zoom + ", " + event.getY() / zoom + ")");
        event.consume();
    }

    private Identifiable<?> equipment(String id) {
        GraphMetadata.NodeMetadata node = sldMetadata == null || id == null || id.isEmpty() ? null : sldMetadata.getNodeMetadata(id);
        return node == null || node.getEquipmentId() == null ? null : container.getNetwork().getIdentifiable(node.getEquipmentId());
    }

    private void showEquipment(GraphMetadata.NodeMetadata node, Identifiable<?> equipment) {
        Network network = container.getNetwork();
        // a diagram leading to another stays in its own scene: in a separate window, this one shows it instead
        if (node.getNextVId() != null && sceneModel.isSeparate()) {
            VoltageLevel target = network.getVoltageLevel(node.getNextVId());
            setContainer(target);
            sceneModel.getDetailWindows().ifPresent(windows -> windows.retarget(this, kind + "/" + target.getId()));
            return;
        }
        Container<?> target = node.getNextVId() != null ? network.getVoltageLevel(node.getNextVId()) : container;
        ContainerNavigationState.ContainerTab tab = node.getNextVId() != null ? ContainerNavigationState.ContainerTab.SINGLE_LINE : equipmentTab(equipment);
        // this state isn't guaranteed to already be in history (e.g. reached via search, which doesn't record one):
        // record it so navigating back from the equipment lands back on this diagram
        sceneModel.record(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(container)));
        sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(target, tab,
                tab == ContainerNavigationState.ContainerTab.SINGLE_LINE ? null : equipment)));
    }

    private static ContainerNavigationState.ContainerTab equipmentTab(Identifiable<?> equipment) {
        return switch (equipment) {
            case Generator _ -> ContainerNavigationState.ContainerTab.GENERATORS;
            case Battery _ -> ContainerNavigationState.ContainerTab.BATTERIES;
            case Load _ -> ContainerNavigationState.ContainerTab.LOADS;
            case ShuntCompensator _ -> ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS;
            case StaticVarCompensator _ -> ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS;
            case VscConverterStation _ -> ContainerNavigationState.ContainerTab.VSC_CONVERTER_STATIONS;
            case LccConverterStation _ -> ContainerNavigationState.ContainerTab.LCC_CONVERTER_STATIONS;
            case TwoWindingsTransformer _, ThreeWindingsTransformer _ -> ContainerNavigationState.ContainerTab.TRANSFORMERS;
            case BusbarSection _ -> ContainerNavigationState.ContainerTab.BUSBAR_SECTIONS;
            case Switch _ -> ContainerNavigationState.ContainerTab.SWITCHES;
            case Line _ -> ContainerNavigationState.ContainerTab.LINES;
            case BoundaryLine _ -> ContainerNavigationState.ContainerTab.BOUNDARY_LINES;
            case Ground _ -> ContainerNavigationState.ContainerTab.GROUNDS;
            case null, default -> ContainerNavigationState.ContainerTab.SINGLE_LINE;
        };
    }

    @SuppressWarnings("unused") // used by JS
    public void onSwitchClick(String id) {
        Objects.requireNonNull(id);
        // not while a background job uses the network (see MainModel.markBusy), e.g. this diagram's re-render
        // after a previous click
        if (mainModel.isBusy(container.getNetwork())) {
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "main.networkBusy"));
            return;
        }
        GraphMetadata.NodeMetadata nodeMetadata = Objects.requireNonNull(sldMetadata.getNodeMetadata(id));
        Switch aSwitch = Objects.requireNonNull(container.getNetwork().getSwitch(nodeMetadata.getEquipmentId()));
        aSwitch.setOpen(!aSwitch.isOpen());
        // re-renders through the updateProperty listener
        mainModel.setUpdate(aSwitch.getVoltageLevel());
    }

    @Override
    public void dispose() {
        renderService.cancel();
        super.dispose();
    }
}
