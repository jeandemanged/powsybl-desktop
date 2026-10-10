/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.ListOfContingencyLists;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.powsybldesktop.contingency.ContingenciesController;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.loadflow.LoadFlowConvergence;
import com.powsybl.powsybldesktop.loadflow.LoadFlowResultAndReport;
import com.powsybl.powsybldesktop.logs.LogsViewController;
import com.powsybl.powsybldesktop.map.MapController;
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
import com.powsybl.powsybldesktop.network.tables.SecurityAnalysisResultsController;
import com.powsybl.powsybldesktop.network.tables.ShuntCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.StaticVarCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.SubstationsTableController;
import com.powsybl.powsybldesktop.network.tables.TieLinesController;
import com.powsybl.powsybldesktop.network.tables.TransformersController;
import com.powsybl.powsybldesktop.network.tables.VoltageLevelsController;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import com.powsybl.powsybldesktop.notification.NotificationOverlay;
import com.powsybl.powsybldesktop.notification.NotificationsController;
import com.powsybl.powsybldesktop.parameters.ParametersController;
import com.powsybl.powsybldesktop.report.ReportsController;
import com.powsybl.powsybldesktop.security.SecurityAnalysisResultAndReport;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.DisposableController;
import com.powsybl.powsybldesktop.utils.LanguagePreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisReport;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.json.JsonSecurityAnalysisParameters;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    public Button mapButton;
    @FXML
    public RadioMenuItem languageEnglishItem;
    @FXML
    public RadioMenuItem languageFrenchItem;
    @FXML
    public ToggleButton notificationsButton;

    private final MainModel mainModel;

    private DisposableController currentController;

    private Parent notificationsView;

    private NotificationsController notificationsController;

    private NotificationOverlay notificationOverlay;

    private Stage memoryStage;

    private Stage parametersStage;

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
        // this overlay stops listening to notifications, so its cards would otherwise stay on screen forever
        if (notificationOverlay != null) {
            notificationOverlay.dispose();
        }
        if (memoryStage != null) {
            memoryStage.close();
        }
        if (parametersStage != null) {
            parametersStage.close();
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

        if (refuseIfBusy(network)) {
            return;
        }
        LoadFlowParameters parameters = copyParameters(mainModel.getParametersModel().loadFlowParametersProperty().getValue());
        Service<LoadFlowResultAndReport> loadFlowService = new Service<>() {
            @Override
            protected Task<LoadFlowResultAndReport> createTask() {
                return new AbstractNetworkTask<>(mainModel, network) {
                    @Override
                    protected LoadFlowResultAndReport compute() throws Exception {
                        ReportNode reportNode = ReportNode.newRootReportNode()
                                .withAllResourceBundlesFromClasspath()
                                .withMessageTemplate("powsybl.desktop.loadflow")
                                .withTimestamp()
                                .build();

                        LoadFlowRunParameters runParameters = LoadFlowRunParameters.getDefault()
                                .setParameters(parameters)
                                .setReportNode(reportNode);
                        // LoadFlow.run() blocks on CompletableFuture.join(), which ignores thread interruption,
                        // so cancelling this task wouldn't stop the underlying computation - runAsync()+get() honors it
                        CompletableFuture<LoadFlowResult> future = LoadFlow.runAsync(network, runParameters);
                        try {
                            LoadFlowResult loadFlowResult = future.get();
                            return new LoadFlowResultAndReport(loadFlowResult, reportNode);
                        } catch (InterruptedException e) {
                            future.cancel(true);
                            Thread.currentThread().interrupt();
                            throw e;
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof Exception cause) {
                                throw cause;
                            }
                            throw e;
                        }
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.loadFlow.running", loadFlowService::cancel);
        mainModel.getNotificationsModel().add(runningNotification);

        loadFlowService.setOnSucceeded(event -> {
            LoadFlowResultAndReport loadFlowResultAndReport = (LoadFlowResultAndReport) event.getSource().getValue();
            mainModel.getStudy(network).setLoadFlowResult(loadFlowResultAndReport.loadFlowResult());
            mainModel.setUpdate();
            mainModel.addReport(loadFlowResultAndReport.reportNode());
            mainModel.getNotificationsModel().replace(runningNotification,
                    loadFlowOutcome(runningNotification.startTimestamp(), loadFlowResultAndReport));
        });

        loadFlowService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.getNotificationsModel().replace(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.loadFlow.failed", viewLogsAction));
        });
        loadFlowService.setOnCancelled(event -> mainModel.getNotificationsModel().replace(runningNotification,
                Notification.createCancelled(runningNotification.startTimestamp(), "main.loadFlow.cancelled")));
        mainModel.getStudy(network).trackComputation(loadFlowService);
        loadFlowService.start();
    }

    // A load flow writes the network, which every other background job reads (including a cancelled computation
    // still winding down): a computation only starts on a network no other job uses
    private boolean refuseIfBusy(Network rootNetwork) {
        if (mainModel.isBusy(rootNetwork)) {
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "main.networkBusy"));
            return true;
        }
        return false;
    }

    // Snapshots taken on the FX thread: a run must not see the parameters window's in-place edits made meanwhile
    private static LoadFlowParameters copyParameters(LoadFlowParameters parameters) {
        return parameters.copy();
    }

    private static SecurityAnalysisParameters copyParameters(SecurityAnalysisParameters parameters) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonSecurityAnalysisParameters.write(parameters, out);
        SecurityAnalysisParameters copy = JsonSecurityAnalysisParameters.read(new ByteArrayInputStream(out.toByteArray()));
        copy.setLoadFlowParameters(parameters.getLoadFlowParameters().copy());
        return copy;
    }

    // completing without exception doesn't mean converged - the outcome reflects the components' convergence
    private Notification loadFlowOutcome(Instant start, LoadFlowResultAndReport loadFlowResultAndReport) {
        NotificationAction viewResultsAction = new NotificationAction("main.loadFlow.viewResults", e ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_COMPONENTS)));
        NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", e ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.REPORTS,
                        ReportNavigationState.create(loadFlowResultAndReport.reportNode()))));
        return switch (LoadFlowConvergence.of(loadFlowResultAndReport.loadFlowResult())) {
            case CONVERGED -> Notification.createSuccess(start, "main.loadFlow.completed", viewResultsAction, viewReportAction);
            case PARTIALLY_CONVERGED -> Notification.createPartialSuccess(start, "main.loadFlow.partiallyConverged", viewResultsAction, viewReportAction);
            case NOT_CONVERGED -> Notification.createError(start, "main.loadFlow.notConverged", viewResultsAction, viewReportAction);
        };
    }

    @FXML
    protected void onSecurityAnalysis() {
        Network selectedNetwork = mainModel.getNetwork();
        if (selectedNetwork == null) {
            return;
        }
        // same rationale as onLoadFlow: never run on a subnetwork, only on its root
        Network network = selectedNetwork.getNetwork();

        if (refuseIfBusy(network)) {
            return;
        }
        SecurityAnalysisParameters parameters = copyParameters(mainModel.getParametersModel().securityAnalysisParametersProperty().getValue());
        List<ContingencyList> enabledContingencyLists = mainModel.getStudy(network).getContingencyLists().stream()
                .filter(list -> mainModel.getStudy(network).contingencyListEnabledProperty(list).get())
                .collect(Collectors.toList());
        ContingencyList contingencyList = new ListOfContingencyLists(network.getNameOrId(), enabledContingencyLists);

        Service<SecurityAnalysisResultAndReport> securityAnalysisService = new Service<>() {
            @Override
            protected Task<SecurityAnalysisResultAndReport> createTask() {
                return new AbstractNetworkTask<>(mainModel, network) {
                    @Override
                    protected SecurityAnalysisResultAndReport compute() throws Exception {
                        ReportNode reportNode = ReportNode.newRootReportNode()
                                .withAllResourceBundlesFromClasspath()
                                .withMessageTemplate("powsybl.desktop.securityanalysis")
                                .withTimestamp()
                                .build();

                        List<Contingency> contingencies = ContingencyNames.deduplicate(contingencyList.getContingencies(network));
                        SecurityAnalysisRunParameters runParameters = SecurityAnalysisRunParameters.getDefault()
                                .setSecurityAnalysisParameters(parameters)
                                .setReportNode(reportNode);
                        // SecurityAnalysis.run() blocks on CompletableFuture.join(), which ignores thread interruption,
                        // so cancelling this task wouldn't stop the underlying computation - runAsync()+get() honors it
                        CompletableFuture<SecurityAnalysisReport> future = SecurityAnalysis.runAsync(network, contingencies, runParameters);
                        try {
                            SecurityAnalysisReport securityAnalysisReport = future.get();
                            return new SecurityAnalysisResultAndReport(securityAnalysisReport.getResult(), reportNode);
                        } catch (InterruptedException e) {
                            future.cancel(true);
                            Thread.currentThread().interrupt();
                            throw e;
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof Exception cause) {
                                throw cause;
                            }
                            throw e;
                        }
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.securityAnalysis.running", securityAnalysisService::cancel);
        mainModel.getNotificationsModel().add(runningNotification);

        securityAnalysisService.setOnSucceeded(event -> {
            SecurityAnalysisResultAndReport securityAnalysisResultAndReport = (SecurityAnalysisResultAndReport) event.getSource().getValue();
            mainModel.getStudy(network).setSecurityAnalysisResult(securityAnalysisResultAndReport.securityAnalysisResult());
            mainModel.setUpdate();
            mainModel.addReport(securityAnalysisResultAndReport.reportNode());
            mainModel.getNotificationsModel().replace(runningNotification,
                    securityAnalysisOutcome(runningNotification.startTimestamp(), securityAnalysisResultAndReport));
        });

        securityAnalysisService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.getNotificationsModel().replace(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.securityAnalysis.failed", viewLogsAction));
        });
        securityAnalysisService.setOnCancelled(event -> mainModel.getNotificationsModel().replace(runningNotification,
                Notification.createCancelled(runningNotification.startTimestamp(), "main.securityAnalysis.cancelled")));
        mainModel.getStudy(network).trackComputation(securityAnalysisService);
        securityAnalysisService.start();
    }

    // completing without exception doesn't mean the base case converged - without it, no contingency was simulated
    private Notification securityAnalysisOutcome(Instant start, SecurityAnalysisResultAndReport securityAnalysisResultAndReport) {
        NotificationAction viewResultsAction = new NotificationAction("main.securityAnalysis.viewResults", e ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS)));
        NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", e ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.REPORTS,
                        ReportNavigationState.create(securityAnalysisResultAndReport.reportNode()))));
        if (securityAnalysisResultAndReport.securityAnalysisResult().getPreContingencyResult().getStatus()
                != LoadFlowResult.ComponentResult.Status.CONVERGED) {
            return Notification.createError(start, "main.securityAnalysis.preContingencyNotConverged", viewResultsAction, viewReportAction);
        }
        return Notification.createSuccess(start, "main.securityAnalysis.completed", viewResultsAction, viewReportAction);
    }

    // Builds the search index for a newly-selected network once, in the background - a network already cached
    // (or already building) is left alone. MainModel.setNetwork sets the cached status before notifying this
    // listener, so the BUILDING state set here is not overwritten and SearchBoxController shows "Indexing...".
    private void ensureSearchIndex(Network network) {
        if (network == null) {
            return;
        }
        NetworkStudy study = mainModel.getStudy(network);
        if (study.getSearchIndex(network) != null || study.isSearchIndexBuilding(network)) {
            return;
        }
        Instant startTimestamp = Instant.now();
        mainModel.searchIndexStateProperty().setValue(NetworkSearchIndex.State.BUILDING);
        Service<NetworkSearchIndex> searchIndexService = new Service<>() {
            @Override
            protected Task<NetworkSearchIndex> createTask() {
                return new AbstractNetworkTask<>(mainModel, network) {
                    @Override
                    protected NetworkSearchIndex compute() {
                        return NetworkSearchIndex.build(network);
                    }
                };
            }
        };
        searchIndexService.setOnSucceeded(event -> {
            NetworkSearchIndex index = (NetworkSearchIndex) event.getSource().getValue();
            // the network can be a subnetwork, while getNetworks() only holds root networks
            if (mainModel.getNetworks().contains(network.getNetwork())) {
                mainModel.setSearchIndex(network, index);
            } else {
                // the network was removed while its index was still building - don't resurrect a cache entry for it
                index.close();
            }
        });
        searchIndexService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);
            if (network.equals(mainModel.getNetwork())) {
                mainModel.searchIndexStateProperty().setValue(NetworkSearchIndex.State.FAILED);
            }
            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));
            mainModel.getNotificationsModel().add(Notification.createError(startTimestamp, "main.search.indexFailed", viewLogsAction));
        });
        study.trackSearchIndexBuild(network, searchIndexService);
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
        NetworkSearchIndex index = mainModel.getStudy(network).getSearchIndex(network);
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
        listenerManager.listen(mainModel.getNavigationHistory().currentEventProperty(), (observable, oldValue, newValue) -> {
            if (newValue != null) {
                onNavigationEvent(newValue);
            }
        });
        listenerManager.listen(mainModel.networkProperty(), (observable, oldValue, newValue) -> {
            ensureSearchIndex(newValue);
            updateMapButtonVisibility();
        });
        updateMapButtonVisibility();
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refreshSearchIndexBuses());

        FXMLLoader notificationsLoader = new FXMLLoader(getClass().getResource("notification/notifications-view.fxml"), Messages.bundle());
        try {
            notificationsView = notificationsLoader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        notificationsController = notificationsLoader.getController();
        notificationsController.setMainModel(mainModel);

        notificationsButton.setSelected(mainModel.getNotificationsModel().isPanelOpen());
        borderPane.setRight(mainModel.getNotificationsModel().isPanelOpen() ? notificationsView : null);

        listenerManager.listen(mainModel.getNotificationsModel().getNotifications(), (ListChangeListener<Notification>) change -> {
            while (change.next()) {
                if (change.wasReplaced()) {
                    for (int i = 0; i < change.getRemovedSize(); i++) {
                        Notification previous = change.getRemoved().get(i);
                        Notification current = mainModel.getNotificationsModel().getNotifications().get(change.getFrom() + i);
                        notificationOverlay().replace(previous, current);
                    }
                } else if (change.wasAdded()) {
                    // besides RUNNING ones, errors are added directly, and so is the outcome of a run whose
                    // RUNNING notification was dismissed (see NotificationsModel.replace)
                    change.getAddedSubList().forEach(notification -> notificationOverlay().show(notification));
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
        listenerManager.listen(mainModel.getNavigationHistory().getPast(), (ListChangeListener<NavigationEvent>) change -> {
            while (change.next()) {
                // consumed only to satisfy the Change cursor contract - the title is derived from the list's
                // current tail below, regardless of what kind of change (add/remove/permutation) occurred
            }
            if (!mainModel.getNavigationHistory().getPast().isEmpty()) {
                updateStageTitle(mainModel.getNavigationHistory().getPast().getLast());
            }
        });

        // at initialize() time the scene/stage don't exist yet (see MainApplication / reloadShell), so the
        // title for the very first navigation event is applied once the scene is attached to its window
        borderPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                listenerManager.listen(newScene.windowProperty(), (o, oldWindow, newWindow) -> updateCurrentStageTitle());
                updateCurrentStageTitle();
            }
        });

        backwardButton.setOnContextMenuRequested(event -> showNavigationHistoryMenu(backwardButton, event, true));
        forwardButton.setOnContextMenuRequested(event -> showNavigationHistoryMenu(forwardButton, event, false));
        backwardButton.disableProperty().bind(Bindings.size(mainModel.getNavigationHistory().getPast()).lessThanOrEqualTo(1));
        forwardButton.disableProperty().bind(Bindings.isEmpty(mainModel.getNavigationHistory().getFuture()));

        if ("fr".equals(Locale.getDefault().getLanguage())) {
            languageFrenchItem.setSelected(true);
        } else {
            languageEnglishItem.setSelected(true);
        }

        NavigationEvent current = mainModel.getNavigationHistory().currentEventProperty().getValue();
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
            Network target = newValue.state().getSelectedNetwork();
            // equipment states hold the equipment's own (sub)network: when its root network is selected, which
            // shows that equipment too, keep it rather than narrowing the view down to the subnetwork
            boolean rootSelected = !(newValue.state() instanceof NetworkNavigationState)
                    && mainModel.getNetwork() == target.getNetwork();
            if (!rootSelected) {
                this.mainModel.setNetwork(target);
            }
        }
        if (newValue.navigationType() == NavigationType.LOGS) {
            ensureController(LogsViewController.class, "logs/logs-view.fxml", c -> c.setLogsModel(mainModel.getLogsModel()));
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
        } else if (newValue.navigationType() == NavigationType.MAP) {
            ensureController(MapController.class, "map/map-view.fxml", c -> c.setMainModel(mainModel));
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
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS) {
            ensureController(SecurityAnalysisResultsController.class,
                    "network/tables/security-analysis-results-view.fxml", c -> c.setMainModel(mainModel));
        } else if (newValue.navigationType() == NavigationType.REPORTS) {
            ReportsController controller = ensureController(ReportsController.class, "report/reports-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof ReportNavigationState state) {
                controller.selectReport(state.getReportNode());
            }
        }
    }

    private void updateCurrentStageTitle() {
        NavigationEvent current = mainModel.getNavigationHistory().currentEventProperty().getValue();
        if (current != null) {
            updateStageTitle(current);
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
            notificationOverlay = new NotificationOverlay(borderPane.getScene().getWindow(), mainModel.getNotificationsModel()::remove);
        }
        return notificationOverlay;
    }

    // managed too, so the hidden button doesn't leave a gap in the toolbar
    private void updateMapButtonVisibility() {
        boolean hasPositions = MapController.hasPositions(mainModel.getNetwork());
        mapButton.setVisible(hasPositions);
        mapButton.setManaged(hasPositions);
    }

    private void disposeCurrentController() {
        if (Objects.nonNull(currentController)) {
            currentController.dispose();
        }
    }

    public void onNetworks() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(mainModel.getNetwork())));
    }

    public void onSubstations() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.createNoContainer(mainModel.getNetwork())));
    }

    public void onMap() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.MAP, NetworkNavigationState.create(mainModel.getNetwork())));
    }

    public void onContingencies() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.CONTINGENCIES, NetworkNavigationState.create(mainModel.getNetwork())));
    }

    // a separate non-modal window rather than a center view, so parameters can be edited while the main
    // stage keeps showing e.g. the diagram they apply to
    public void onParameters() {
        if (parametersStage != null) {
            parametersStage.requestFocus();
            parametersStage.toFront();
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("parameters/parameters-view.fxml"), Messages.bundle());
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        root.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/com/powsybl/powsybldesktop/styles.css")).toExternalForm());
        ParametersController controller = loader.getController();
        controller.setMainModel(mainModel);

        parametersStage = new Stage();
        parametersStage.initOwner(borderPane.getScene().getWindow());
        parametersStage.setTitle(Messages.get("main.toolbar.parameters"));
        parametersStage.getIcons().add(new Image(
                Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
        parametersStage.setScene(new Scene(root, 1000, 700));
        parametersStage.setOnHidden(event -> {
            controller.dispose();
            parametersStage = null;
        });
        parametersStage.show();
    }

    public void onLogs() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));
    }

    public void onSubstationsTable() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SUBSTATIONS, SubstationNavigationState.createNoSubstation(mainModel.getNetwork())));
    }

    public void onVoltageLevelsTable() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS, VoltageLevelNavigationState.createNoVoltageLevel(mainModel.getNetwork())));
    }

    public void onBusbarSectionsTable() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS,
                BusbarSectionNavigationState.createNoBusbarSection(mainModel.getNetwork())));
    }

    public void onBusesBusView() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW, BusNavigationState.createNoBus(mainModel.getNetwork())));
    }

    public void onBusesBusBreakerView() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW));
    }

    public void onGenerators() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_GENERATORS, GeneratorNavigationState.createNoGenerator(mainModel.getNetwork())));
    }

    public void onShuntCompensators() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS,
                ShuntCompensatorNavigationState.createNoShuntCompensator(mainModel.getNetwork())));
    }

    public void onStaticVarCompensators() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS,
                StaticVarCompensatorNavigationState.createNoStaticVarCompensator(mainModel.getNetwork())));
    }

    public void onLoads() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_LOADS, LoadNavigationState.createNoLoad(mainModel.getNetwork())));
    }

    public void onLines() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_LINES, LineNavigationState.createNoLine(mainModel.getNetwork())));
    }

    public void onTransformers() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_TRANSFORMERS, TransformerNavigationState.createNoTransformer(mainModel.getNetwork())));
    }

    public void onTieLines() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.createNoTieLine(mainModel.getNetwork())));
    }

    public void onBoundaryLines() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, BoundaryLineNavigationState.createNoBoundaryLine(mainModel.getNetwork())));
    }

    public void onComponents() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_COMPONENTS));
    }

    public void onSecurityAnalysisResults() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS));
    }

    public void onReports() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.REPORTS));
    }

    public void onNotifications() {
        mainModel.getNotificationsModel().setPanelOpen(notificationsButton.isSelected());
        borderPane.setRight(notificationsButton.isSelected() ? notificationsView : null);
    }

    public void onNavigateBackward() {
        mainModel.getNavigationHistory().navigateBackward();
    }

    public void onNavigateForward() {
        mainModel.getNavigationHistory().navigateForward();
    }

    private void showNavigationHistoryMenu(Button anchor, ContextMenuEvent event, boolean backward) {
        var events = backward ? mainModel.getNavigationHistory().getPast() : mainModel.getNavigationHistory().getFuture();
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
                    mainModel.getNavigationHistory().navigateBackwardToIndex(index);
                } else {
                    mainModel.getNavigationHistory().navigateForwardToIndex(index);
                }
            });
            contextMenu.getItems().add(item);
        }
        contextMenu.show(anchor, event.getScreenX(), event.getScreenY());
    }
}
