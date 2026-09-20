/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.powsybldesktop.contingency.ContingenciesController;
import com.powsybl.powsybldesktop.loadflow.LoadFlowResultAndReport;
import com.powsybl.powsybldesktop.logs.LogsViewController;
import com.powsybl.powsybldesktop.memory.MemoryController;
import com.powsybl.powsybldesktop.navigation.*;
import com.powsybl.powsybldesktop.network.NetworksController;
import com.powsybl.powsybldesktop.network.SubstationsController;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.network.tables.BoundaryLinesController;
import com.powsybl.powsybldesktop.network.tables.BusbarSectionsController;
import com.powsybl.powsybldesktop.network.tables.BusesBusBreakerViewController;
import com.powsybl.powsybldesktop.network.tables.BusesBusViewController;
import com.powsybl.powsybldesktop.network.tables.ComponentsController;
import com.powsybl.powsybldesktop.network.tables.GeneratorsController;
import com.powsybl.powsybldesktop.network.tables.LinesController;
import com.powsybl.powsybldesktop.network.tables.LoadsController;
import com.powsybl.powsybldesktop.network.tables.ShuntCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.StaticVarCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.SubstationsTableController;
import com.powsybl.powsybldesktop.network.tables.TieLinesController;
import com.powsybl.powsybldesktop.network.tables.TransformersController;
import com.powsybl.powsybldesktop.network.tables.VoltageLevelsController;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import com.powsybl.powsybldesktop.notification.NotificationOverlay;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.notification.NotificationsController;
import com.powsybl.powsybldesktop.parameters.ParametersController;
import com.powsybl.powsybldesktop.report.ReportsController;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.DisposableController;
import com.powsybl.powsybldesktop.utils.LanguagePreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns the shell built from {@code main-view.fxml} (menu bar, toolbar, center view). Rebuilt in
 * place by {@link #reloadShell()} on a language change, since {@code %key} FXML text is resolved
 * once at load time rather than bound live - the {@link MainModel} instance is carried over to the
 * new controller so no application state is lost.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MainController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    @FXML
    public BorderPane borderPane;
    @FXML
    public Button backwardButton;
    @FXML
    public Button forwardButton;
    @FXML
    public RadioMenuItem languageEnglishItem;
    @FXML
    public RadioMenuItem languageFrenchItem;
    @FXML
    public ToggleButton notificationsButton;

    private final MainModel mainModel;

    private final Map<Network, Service<LoadFlowResultAndReport>> loadFlowServices = new HashMap<>();

    private final Map<Network, Service<NetworkSearchIndex>> searchIndexServices = new HashMap<>();

    private DisposableController currentController;

    private Parent notificationsView;

    private NotificationsController notificationsController;

    private NotificationOverlay notificationOverlay;

    private Stage memoryStage;

    public MainController(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
    }

    @FXML
    protected void onExit() {
        Platform.exit();
    }

    @FXML
    protected void onMemory() {
        if (memoryStage != null) {
            memoryStage.requestFocus();
            memoryStage.toFront();
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("memory/memory-view.fxml"), Messages.bundle());
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        root.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/com/powsybl/powsybldesktop/styles.css")).toExternalForm());
        MemoryController controller = loader.getController();

        memoryStage = new Stage();
        memoryStage.initOwner(borderPane.getScene().getWindow());
        memoryStage.setTitle(Messages.get("main.menu.help.memory"));
        memoryStage.getIcons().add(new Image(
                Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
        memoryStage.setScene(new Scene(root));
        memoryStage.setResizable(false);
        memoryStage.setOnHidden(event -> {
            controller.dispose();
            memoryStage = null;
        });
        memoryStage.show();
    }

    @FXML
    protected void onAbout() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("about/about-view.fxml"), Messages.bundle());
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(borderPane.getScene().getWindow());
        dialog.setTitle(Messages.get("main.about.title"));
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(
                Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))
        ));

        dialog.showAndWait();
    }

    @FXML
    protected void onLanguageEnglish() {
        onLanguageChange(Locale.ENGLISH);
    }

    @FXML
    protected void onLanguageFrench() {
        onLanguageChange(Locale.FRENCH);
    }

    private void onLanguageChange(Locale locale) {
        LanguagePreferences.save(locale);
        Locale.setDefault(locale);
        reloadShell();
    }

    /**
     * Rebuilds the shell FXML under the now-current {@link Locale#getDefault()} and swaps it into
     * the existing {@link Stage}'s scene, carrying {@link #mainModel} over to the new controller so
     * loaded networks, notifications, reports and navigation history survive the reload. The current
     * center view is re-resolved too (see the end of {@link #initialize()}), since it was loaded from
     * FXML under the old bundle as well.
     */
    private void reloadShell() {
        Stage stage = (Stage) borderPane.getScene().getWindow();
        dispose();
        MainController newController = new MainController(mainModel);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(type -> newController);
        try {
            stage.getScene().setRoot(loader.load());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void dispose() {
        disposeCurrentController();
        if (notificationsController != null) {
            notificationsController.dispose();
        }
        if (memoryStage != null) {
            memoryStage.close();
        }
        super.dispose();
    }

    @FXML
    protected void onLoadFlow() {
        Network selectedNetwork = mainModel.getNetwork();
        if (selectedNetwork == null) {
            return;
        }
        // never run a load flow on a subnetwork, only on its root - a subnetwork-scoped run would ignore
        // whatever ties it to the rest of the merged network (e.g. a tie line straddling two subnetworks)
        Network network = selectedNetwork.getNetwork();

        if (loadFlowServices.containsKey(network)) {
            return;
        }
        Service<LoadFlowResultAndReport> loadFlowService = new Service<>() {
            @Override
            protected Task<LoadFlowResultAndReport> createTask() {
                return new Task<>() {
                    @Override
                    protected LoadFlowResultAndReport call() {
                        ReportNode reportNode = ReportNode.newRootReportNode()
                                .withAllResourceBundlesFromClasspath()
                                .withMessageTemplate("powsybl.desktop.loadflow")
                                .withTimestamp()
                                .build();

                        LoadFlowRunParameters runParameters = LoadFlowRunParameters.getDefault()
                                .setParameters(mainModel.loadFlowParametersProperty().getValue())
                                .setReportNode(reportNode);
                        LoadFlowResult loadFlowResult = LoadFlow.run(network, runParameters);
                        return new LoadFlowResultAndReport(loadFlowResult, reportNode);
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.loadFlow.running", loadFlowService::cancel);
        mainModel.addNotification(runningNotification);

        loadFlowService.setOnSucceeded(event -> {
            loadFlowServices.remove(network);
            LoadFlowResultAndReport loadFlowResultAndReport = (LoadFlowResultAndReport) event.getSource().getValue();
            mainModel.setLoadFlowResult(network, loadFlowResultAndReport.loadFlowResult());
            mainModel.setUpdate();
            mainModel.addReport(loadFlowResultAndReport.reportNode());

            NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.REPORTS,
                            ReportNavigationState.create(loadFlowResultAndReport.reportNode()))));

            mainModel.replaceNotification(runningNotification,
                    Notification.createSuccess(runningNotification.startTimestamp(), "main.loadFlow.completed", viewReportAction));
        });

        loadFlowService.setOnFailed(event -> {
            loadFlowServices.remove(network);
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.replaceNotification(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.loadFlow.failed", viewLogsAction));
        });
        loadFlowService.setOnCancelled(event -> {
            loadFlowServices.remove(network);
            mainModel.replaceNotification(runningNotification,
                    Notification.createCancelled(runningNotification.startTimestamp(), "main.loadFlow.cancelled"));
        });
        loadFlowServices.put(network, loadFlowService);
        loadFlowService.start();
    }

    // Builds the search index for a newly-selected network once, in the background - a network already cached
    // (or already building) is left alone, and MainModel.setNetwork already reflects the new network's cached
    // status before this runs, so SearchBoxController sees the right "Indexing..." state immediately.
    private void ensureSearchIndex(Network network) {
        if (network == null || mainModel.getSearchIndex(network) != null || searchIndexServices.containsKey(network)) {
            return;
        }
        Instant startTimestamp = Instant.now();
        mainModel.searchIndexStateProperty().setValue(NetworkSearchIndex.State.BUILDING);
        Service<NetworkSearchIndex> searchIndexService = new Service<>() {
            @Override
            protected Task<NetworkSearchIndex> createTask() {
                return new Task<>() {
                    @Override
                    protected NetworkSearchIndex call() {
                        return NetworkSearchIndex.build(network);
                    }
                };
            }
        };
        searchIndexService.setOnSucceeded(event -> {
            searchIndexServices.remove(network);
            NetworkSearchIndex index = (NetworkSearchIndex) event.getSource().getValue();
            if (mainModel.getNetworks().contains(network)) {
                mainModel.setSearchIndex(network, index);
            } else {
                // the network was removed while its index was still building - don't resurrect a cache entry for it
                index.close();
            }
        });
        searchIndexService.setOnFailed(event -> {
            searchIndexServices.remove(network);
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);
            if (network.equals(mainModel.getNetwork())) {
                mainModel.searchIndexStateProperty().setValue(NetworkSearchIndex.State.FAILED);
            }
            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS)));
            mainModel.addNotification(Notification.createError(startTimestamp, "main.search.indexFailed", viewLogsAction));
        });
        searchIndexServices.put(network, searchIndexService);
        searchIndexService.start();
    }

    // Bus-view merged buses are recalculated on every topology change (switch open/close, terminal
    // connect/disconnect) - both of which route through mainModel.setUpdate() - so the cached index's BUS entries
    // need refreshing too, unlike every other equipment kind (see NetworkSearchIndex's class javadoc). Cheap
    // enough (proportional to bus count, not the whole network) to run synchronously, unlike the initial build;
    // a no-op if the current network has no cached index yet (still building, or none requested). Scoped to just
    // the voltage level the change was confined to when known, cheaper still than the whole-network fallback
    // used when it isn't (e.g. after a load flow run).
    private void refreshSearchIndexBuses() {
        Network network = mainModel.getNetwork();
        if (network == null) {
            return;
        }
        NetworkSearchIndex index = mainModel.getSearchIndex(network);
        if (index == null) {
            return;
        }
        VoltageLevel voltageLevel = mainModel.getUpdatedVoltageLevel();
        if (voltageLevel != null) {
            index.refreshBuses(voltageLevel);
        } else {
            index.refreshBuses(network);
        }
    }

    private <T extends DisposableController> Pair<Parent, T> loadView(String fxml) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml), Messages.bundle());
        try {
            Parent view = loader.load();
            T controller = loader.getController();
            return new Pair<>(view, controller);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Swaps the border pane's center to fxml's view/controller only when it isn't already showing, so repeated
    // navigation events for the same NavigationType (e.g. selecting a different row) don't reload the view.
    private <T extends DisposableController> T ensureController(Class<T> controllerClass, String fxml, Consumer<T> setup) {
        if (!controllerClass.isInstance(currentController)) {
            disposeCurrentController();
            Pair<Parent, T> viewAndController = loadView(fxml);
            T controller = viewAndController.getValue();
            currentController = controller;
            setup.accept(controller);
            borderPane.setCenter(viewAndController.getKey());
        }
        return controllerClass.cast(currentController);
    }

    @FXML
    private void initialize() {
        // MainModel briefly sets this property to null before every real dispatch, to force the listener
        // to fire even when navigating to a content-equal NavigationEvent - ignore that transient value
        listenerManager.listen(mainModel.navigationEventProperty(), (observable, oldValue, newValue) -> {
            if (newValue != null) {
                onNavigationEvent(newValue);
            }
        });
        listenerManager.listen(mainModel.networkProperty(), (observable, oldValue, newValue) -> ensureSearchIndex(newValue));
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refreshSearchIndexBuses());

        FXMLLoader notificationsLoader = new FXMLLoader(getClass().getResource("notification/notifications-view.fxml"), Messages.bundle());
        try {
            notificationsView = notificationsLoader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        notificationsController = notificationsLoader.getController();
        notificationsController.setMainModel(mainModel);

        notificationsButton.setSelected(mainModel.isNotificationsPanelOpen());
        borderPane.setRight(mainModel.isNotificationsPanelOpen() ? notificationsView : null);

        listenerManager.listen(mainModel.getNotifications(), (ListChangeListener<Notification>) change -> {
            while (change.next()) {
                if (change.wasReplaced()) {
                    for (int i = 0; i < change.getRemovedSize(); i++) {
                        Notification previous = change.getRemoved().get(i);
                        Notification current = mainModel.getNotifications().get(change.getFrom() + i);
                        notificationOverlay().replace(previous, current);
                    }
                } else if (change.wasAdded()) {
                    change.getAddedSubList().stream()
                            .filter(notification -> notification.status() == NotificationStatus.RUNNING)
                            .forEach(notification -> notificationOverlay().show(notification));
                } else if (change.wasRemoved() && notificationOverlay != null) {
                    change.getRemoved().forEach(notificationOverlay::remove);
                }
            }
        });

        borderPane.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.BACK) {
                onNavigateBackward();
            } else if (event.getButton() == MouseButton.FORWARD) {
                onNavigateForward();
            }
        });

        // navigationPast changes on every move through history - including a tree selection (e.g. NetworksController,
        // SubstationsController) that records history with notify=false to avoid re-triggering the view swap below -
        // so, unlike onNavigationEvent, this is the one place that catches every case the title needs refreshing.
        listenerManager.listen(mainModel.getNavigationPast(), (ListChangeListener<NavigationEvent>) change -> {
            while (change.next()) {
                // consumed only to satisfy the Change cursor contract - the title is derived from the list's
                // current tail below, regardless of what kind of change (add/remove/permutation) occurred
            }
            if (!mainModel.getNavigationPast().isEmpty()) {
                updateStageTitle(mainModel.getNavigationPast().getLast());
            }
        });

        // at initialize() time the scene/stage don't exist yet (see MainApplication / reloadShell), so the
        // title for the very first navigation event is applied once the scene is attached
        borderPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                NavigationEvent current = mainModel.navigationEventProperty().getValue();
                if (current != null) {
                    updateStageTitle(current);
                }
            }
        });

        backwardButton.setOnContextMenuRequested(event -> showNavigationHistoryMenu(backwardButton, event, true));
        forwardButton.setOnContextMenuRequested(event -> showNavigationHistoryMenu(forwardButton, event, false));
        backwardButton.disableProperty().bind(Bindings.size(mainModel.getNavigationPast()).lessThanOrEqualTo(1));
        forwardButton.disableProperty().bind(Bindings.isEmpty(mainModel.getNavigationFuture()));

        if ("fr".equals(Locale.getDefault().getLanguage())) {
            languageFrenchItem.setSelected(true);
        } else {
            languageEnglishItem.setSelected(true);
        }

        NavigationEvent current = mainModel.navigationEventProperty().getValue();
        if (current == null) {
            // first launch: no navigation history yet, default initial view
            onNetworks();
        } else {
            // reload after a language change: re-resolve the current view's FXML under the new bundle
            onNavigationEvent(current);
        }
    }

    private void onNavigationEvent(NavigationEvent newValue) {
        Objects.requireNonNull(newValue);
        if (newValue.state() != null && newValue.state().getSelectedNetwork() != null) {
            this.mainModel.setNetwork(newValue.state().getSelectedNetwork());
        }
        if (newValue.navigationType() == NavigationType.LOGS) {
            ensureController(LogsViewController.class, "logs/logs-view.fxml", c -> c.setLogsModel(mainModel.getLogsModel()));
        } else if (newValue.navigationType() == NavigationType.PARAMETERS) {
            ensureController(ParametersController.class, "parameters/parameters-view.fxml", c -> c.setMainModel(mainModel));
        } else if (newValue.navigationType() == NavigationType.NETWORKS) {
            NetworksController controller = ensureController(NetworksController.class, "network/networks-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof NetworkNavigationState state) {
                controller.navigateTo(state.getSelectedNetwork());
            } else if (newValue.state() == null) {
                controller.navigateTo(null);
            }
        } else if (newValue.navigationType() == NavigationType.SUBSTATIONS) {
            SubstationsController controller = ensureController(SubstationsController.class, "network/substations-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof ContainerNavigationState state) {
                controller.navigateTo(state.getContainer(), state.getTab());
            } else if (newValue.state() == null) {
                controller.navigateTo(null);
            }
        } else if (newValue.navigationType() == NavigationType.CONTINGENCIES) {
            ensureController(ContingenciesController.class, "contingency/contingencies-view.fxml", c -> c.setMainModel(mainModel));
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SUBSTATIONS) {
            SubstationsTableController controller = ensureController(SubstationsTableController.class,
                    "network/tables/substations-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof SubstationNavigationState state) {
                controller.goToSubstation(state.getSubstation());
            } else if (newValue.state() == null) {
                controller.goToSubstation(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS) {
            VoltageLevelsController controller = ensureController(VoltageLevelsController.class,
                    "network/tables/voltage-levels-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof VoltageLevelNavigationState state) {
                controller.goToVoltageLevel(state.getVoltageLevel());
            } else if (newValue.state() == null) {
                controller.goToVoltageLevel(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS) {
            BusbarSectionsController controller = ensureController(BusbarSectionsController.class,
                    "network/tables/busbar-sections-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof BusbarSectionNavigationState state) {
                controller.goToBusbarSection(state.getBusbarSection());
            } else if (newValue.state() == null) {
                controller.goToBusbarSection(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW) {
            BusesBusViewController controller = ensureController(BusesBusViewController.class,
                    "network/tables/buses-bus-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof BusNavigationState state) {
                controller.goToBus(state.getBus());
            } else if (newValue.state() == null) {
                controller.goToBus(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW) {
            BusesBusBreakerViewController controller = ensureController(BusesBusBreakerViewController.class,
                    "network/tables/buses-bus-breaker-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof BusNavigationState state) {
                controller.goToBus(state.getBus());
            } else if (newValue.state() == null) {
                controller.goToBus(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_GENERATORS) {
            GeneratorsController controller = ensureController(GeneratorsController.class,
                    "network/tables/generators-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof GeneratorNavigationState state) {
                controller.goToGenerator(state.getGenerator());
            } else if (newValue.state() == null) {
                controller.goToGenerator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS) {
            ShuntCompensatorsController controller = ensureController(ShuntCompensatorsController.class,
                    "network/tables/shunt-compensators-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof ShuntCompensatorNavigationState state) {
                controller.goToShuntCompensator(state.getShuntCompensator());
            } else if (newValue.state() == null) {
                controller.goToShuntCompensator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS) {
            StaticVarCompensatorsController controller = ensureController(StaticVarCompensatorsController.class,
                    "network/tables/static-var-compensators-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof StaticVarCompensatorNavigationState state) {
                controller.goToStaticVarCompensator(state.getStaticVarCompensator());
            } else if (newValue.state() == null) {
                controller.goToStaticVarCompensator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_LOADS) {
            LoadsController controller = ensureController(LoadsController.class, "network/tables/loads-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof LoadNavigationState state) {
                controller.goToLoad(state.getLoad());
            } else if (newValue.state() == null) {
                controller.goToLoad(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_LINES) {
            LinesController controller = ensureController(LinesController.class, "network/tables/lines-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof LineNavigationState state) {
                controller.goToLine(state.getLine());
            } else if (newValue.state() == null) {
                controller.goToLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_TRANSFORMERS) {
            TransformersController controller = ensureController(TransformersController.class,
                    "network/tables/transformers-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof TransformerNavigationState state) {
                controller.goToTransformer(state.getTransformer());
            } else if (newValue.state() == null) {
                controller.goToTransformer(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_TIE_LINES) {
            TieLinesController controller = ensureController(TieLinesController.class, "network/tables/tie-lines-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof TieLineNavigationState state) {
                controller.goToTieLine(state.getTieLine());
            } else if (newValue.state() == null) {
                controller.goToTieLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BOUNDARY_LINES) {
            BoundaryLinesController controller = ensureController(BoundaryLinesController.class,
                    "network/tables/boundary-lines-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof BoundaryLineNavigationState state) {
                controller.goToBoundaryLine(state.getBoundaryLine());
            } else if (newValue.state() == null) {
                controller.goToBoundaryLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_COMPONENTS) {
            ensureController(ComponentsController.class, "network/tables/components-view.fxml", c -> c.setMainModel(mainModel));
        } else if (newValue.navigationType() == NavigationType.REPORTS) {
            ReportsController controller = ensureController(ReportsController.class, "report/reports-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof ReportNavigationState state) {
                controller.selectReport(state.getReportNode());
            }
        }
    }

    private void updateStageTitle(NavigationEvent event) {
        Scene scene = borderPane.getScene();
        if (scene == null) {
            return;
        }
        Stage stage = (Stage) scene.getWindow();
        if (stage != null) {
            stage.setTitle(MainApplication.APP_TITLE + " - " + event.describe());
        }
    }

    private NotificationOverlay notificationOverlay() {
        // Created lazily: at initialize() time the scene/stage don't exist yet (see MainApplication).
        if (notificationOverlay == null) {
            notificationOverlay = new NotificationOverlay(borderPane.getScene().getWindow(), mainModel::removeNotification);
        }
        return notificationOverlay;
    }

    private void disposeCurrentController() {
        if (Objects.nonNull(currentController)) {
            currentController.dispose();
        }
    }

    public void onNetworks() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(mainModel.getNetwork())));
    }

    public void onSubstations() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.createNoContainer(mainModel.getNetwork())));
    }

    public void onContingencies() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.CONTINGENCIES, NetworkNavigationState.create(mainModel.getNetwork())));
    }

    public void onParameters() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.PARAMETERS));
    }

    public void onLogs() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));
    }

    public void onSubstationsTable() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_SUBSTATIONS, SubstationNavigationState.createNoSubstation(mainModel.getNetwork())));
    }

    public void onVoltageLevelsTable() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS, VoltageLevelNavigationState.createNoVoltageLevel(mainModel.getNetwork())));
    }

    public void onBusbarSectionsTable() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS,
                BusbarSectionNavigationState.createNoBusbarSection(mainModel.getNetwork())));
    }

    public void onBusesBusView() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW, BusNavigationState.createNoBus(mainModel.getNetwork())));
    }

    public void onBusesBusBreakerView() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW));
    }

    public void onGenerators() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_GENERATORS, GeneratorNavigationState.createNoGenerator(mainModel.getNetwork())));
    }

    public void onShuntCompensators() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS,
                ShuntCompensatorNavigationState.createNoShuntCompensator(mainModel.getNetwork())));
    }

    public void onStaticVarCompensators() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS,
                StaticVarCompensatorNavigationState.createNoStaticVarCompensator(mainModel.getNetwork())));
    }

    public void onLoads() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_LOADS, LoadNavigationState.createNoLoad(mainModel.getNetwork())));
    }

    public void onLines() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_LINES, LineNavigationState.createNoLine(mainModel.getNetwork())));
    }

    public void onTransformers() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_TRANSFORMERS, TransformerNavigationState.createNoTransformer(mainModel.getNetwork())));
    }

    public void onTieLines() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.createNoTieLine(mainModel.getNetwork())));
    }

    public void onBoundaryLines() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, BoundaryLineNavigationState.createNoBoundaryLine(mainModel.getNetwork())));
    }

    public void onComponents() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_COMPONENTS));
    }

    public void onReports() {
        mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.REPORTS));
    }

    public void onNotifications() {
        mainModel.setNotificationsPanelOpen(notificationsButton.isSelected());
        borderPane.setRight(notificationsButton.isSelected() ? notificationsView : null);
    }

    public void onNavigateBackward() {
        mainModel.navigateBackward();
    }

    public void onNavigateForward() {
        mainModel.navigateForward();
    }

    private void showNavigationHistoryMenu(Button anchor, ContextMenuEvent event, boolean backward) {
        var events = backward ? mainModel.getNavigationPast() : mainModel.getNavigationFuture();
        int lastIndex = backward ? events.size() - 2 : events.size() - 1;
        if (lastIndex < 0) {
            return;
        }
        ContextMenu contextMenu = new ContextMenu();
        for (int i = lastIndex; i >= 0; i--) {
            int index = i;
            MenuItem item = new MenuItem(events.get(i).describe());
            item.setOnAction(e -> {
                if (backward) {
                    mainModel.navigateBackwardToIndex(index);
                } else {
                    mainModel.navigateForwardToIndex(index);
                }
            });
            contextMenu.getItems().add(item);
        }
        contextMenu.show(anchor, event.getScreenX(), event.getScreenY());
    }
}
