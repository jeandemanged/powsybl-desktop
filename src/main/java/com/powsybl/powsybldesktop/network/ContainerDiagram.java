/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.google.common.io.ByteStreams;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Switch;
import com.powsybl.powsybldesktop.AbstractNetworkTask;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.diagram.DiagramPaneController;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.parameters.ApplicationParametersJson;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;
import com.powsybl.powsybldesktop.parameters.DesktopSldParameters;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sld.svg.GraphMetadata;
import javafx.animation.PauseTransition;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The single line or network area diagram of one container, shown in a {@link DiagramPaneController}: rendered off
 * the FX thread (latest wins), again on every network update, diagram parameters or decimals change - lazily, only
 * while showing. The single line diagram is interactive: a switch click toggles the switch, a feeder click navigates
 * to the feeder's equipment (see {@link SceneModel#navigate}). Used by {@link SubstationsController}'s two diagram
 * tabs and by separate diagram windows.
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
                    <style>
                        .sld-top-feeder,
                        .sld-bottom-feeder,
                        .sld-load-break-switch,
                        .sld-breaker,
                        .sld-disconnector {
                            cursor: pointer;
                        }
                    </style>
                    <body style='margin: 0'>
                        <div id="svgContainer"></div>
                    </body>
                </html>
            """;

    private record Render(String svg, GraphMetadata metadata) {
    }

    private final Kind kind;
    private final DiagramPaneController pane;
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
        renderService.setOnSucceeded(event -> {
            sldMetadata = renderService.getValue().metadata();
            pane.showDiagram(renderService.getValue().svg());
        });
        renderService.setOnFailed(event -> LOGGER.error(renderService.getException().getMessage(), renderService.getException()));

        var parametersModel = mainModel.getParametersModel();
        if (kind == Kind.SINGLE_LINE) {
            pane.setOnEngineLoaded(() -> ((JSObject) pane.getWebView().getEngine().executeScript("window")).setMember("controller", this));
            pane.loadShell(SLD_HTML_SHELL.replace("%__JS__%", readSldJs()));
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
        Network network = container.getNetwork();
        Identifiable<?> equipment = network.getIdentifiable(node.getEquipmentId());
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
            case Load _ -> ContainerNavigationState.ContainerTab.LOADS;
            case ShuntCompensator _ -> ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS;
            case StaticVarCompensator _ -> ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS;
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
