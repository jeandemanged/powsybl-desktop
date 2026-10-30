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
import com.powsybl.iidm.network.VariantManager;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
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
import com.powsybl.powsybldesktop.navigation.AreaNavigationState;
import com.powsybl.powsybldesktop.navigation.GroundNavigationState;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import com.powsybl.powsybldesktop.navigation.OverloadManagementSystemNavigationState;
import com.powsybl.powsybldesktop.network.NetworksController;
import com.powsybl.powsybldesktop.network.SubstationsController;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.network.tables.AreasController;
import com.powsybl.powsybldesktop.network.tables.BatteriesController;
import com.powsybl.powsybldesktop.network.tables.BoundaryLinesController;
import com.powsybl.powsybldesktop.network.tables.BusbarSectionsController;
import com.powsybl.powsybldesktop.network.tables.BusesBusBreakerViewController;
import com.powsybl.powsybldesktop.network.tables.BusesBusViewController;
import com.powsybl.powsybldesktop.network.tables.ComponentsController;
import com.powsybl.powsybldesktop.network.tables.GeneratorsController;
import com.powsybl.powsybldesktop.network.tables.GroundsController;
import com.powsybl.powsybldesktop.network.tables.HvdcLinesController;
import com.powsybl.powsybldesktop.network.tables.LccConverterStationsController;
import com.powsybl.powsybldesktop.network.tables.LinesController;
import com.powsybl.powsybldesktop.network.tables.LoadsController;
import com.powsybl.powsybldesktop.network.tables.OverloadManagementSystemsController;
import com.powsybl.powsybldesktop.network.tables.SecurityAnalysisResultsController;
import com.powsybl.powsybldesktop.network.tables.ShuntCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.StaticVarCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.SubstationsTableController;
import com.powsybl.powsybldesktop.network.tables.TieLinesController;
import com.powsybl.powsybldesktop.network.tables.TransformersController;
import com.powsybl.powsybldesktop.network.tables.ViolationsController;
import com.powsybl.powsybldesktop.network.tables.VoltageAngleLimitsController;
import com.powsybl.powsybldesktop.network.tables.VoltageLevelsController;
import com.powsybl.powsybldesktop.network.tables.VscConverterStationsController;
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
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.powsybldesktop.window.SeparateWindows;
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
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns the shell built from {@code main-view.fxml} (menu bar, toolbar, center view). Rebuilt in
 * place by {@link #reloadShell()} on a language change, since {@code %key} FXML text is resolved
 * once at load time rather than bound live - the {@link MainModel} instance is carried over to the
 * new controller so no application state is lost.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MainController extends AbstractDisposableController {
    private static final Set<NavigationType> NETWORK_OBJECT_TABLES = EnumSet.of(NavigationType.NETWORK_TABLE_GROUNDS,
            NavigationType.NETWORK_TABLE_AREAS, NavigationType.NETWORK_TABLE_OVERLOAD_MANAGEMENT_SYSTEMS, NavigationType.NETWORK_TABLE_VOLTAGE_ANGLE_LIMITS);

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    @FXML
    public BorderPane borderPane;
    @FXML
    public Button backwardButton;
    @FXML
    public Button forwardButton;
    @FXML
    private Menu windowsMenu;
    @FXML
    public RadioMenuItem languageEnglishItem;
    @FXML
    public RadioMenuItem languageFrenchItem;
    @FXML
    public ToggleButton notificationsButton;
    @FXML
    private Button mapButton;
    @FXML
    private Button contingenciesButton;
    @FXML
    private Button parametersButton;
    @FXML
    private Button logsButton;
    @FXML
    private Button reportsButton;

    private static final String LOGS_WINDOW = "logs";
    private static final String REPORTS_WINDOW = "reports";
    private static final String PARAMETERS_WINDOW = "parameters";
    private static final String MAP_FXML = "map/map-view.fxml";
    private static final String CONTINGENCIES_FXML = "contingency/contingencies-view.fxml";

    private final MainModel mainModel;
    private final SeparateWindows separateWindows;
    // the selected root network, when selected by navigation to show its own equipment rather than by the user, see
    // networkToSelect
    private Network implicitlySelectedRoot;
    private final SceneModel sceneModel;

    private DisposableController currentController;

    private Parent notificationsView;

    private NotificationsController notificationsController;

    private NotificationOverlay notificationOverlay;

    private Stage memoryStage;

    public MainController(MainModel mainModel) {
        this(mainModel, new SeparateWindows(mainModel, () -> null));
    }

    // separateWindows: kept open across a language change, see reloadShell
    private MainController(MainModel mainModel, SeparateWindows separateWindows) {
        this.mainModel = Objects.requireNonNull(mainModel);
        this.separateWindows = separateWindows;
        separateWindows.setMainWindow(() -> borderPane.getScene() == null ? null : borderPane.getScene().getWindow());
        this.sceneModel = SceneModel.main(mainModel, separateWindows);
        mainModel.setNetworkChangeGuard(separateWindows::confirmNetworkChange);
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
        memoryStage.setOnHidden(_ -> {
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
        dialog.setTitle(Messages.get("main.about"));
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
     * FXML under the old bundle as well. Separate windows are kept, their views rebuilt the same way.
     */
    private void reloadShell() {
        Stage stage = (Stage) borderPane.getScene().getWindow();
        disposeShell();
        MainController newController = new MainController(mainModel, separateWindows);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> newController);
        try {
            stage.getScene().setRoot(loader.load());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        separateWindows.relocalize();
    }

    @Override
    public void dispose() {
        separateWindows.closeAll();
        disposeShell();
    }

    private void disposeShell() {
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
                                .withMessageTemplate("desktop.loadFlow")
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
                            return new LoadFlowResultAndReport(loadFlowResult, reportNode,
                                    NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS));
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
            mainModel.getStudy(network).setViolations(loadFlowResultAndReport.violations());
            mainModel.setUpdate();
            mainModel.addReport(loadFlowResultAndReport.reportNode());
            mainModel.getNotificationsModel().replace(runningNotification,
                    loadFlowOutcome(runningNotification.startTimestamp(), loadFlowResultAndReport));
        });

        loadFlowService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", _ ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.getNotificationsModel().replace(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.loadFlow.failed", viewLogsAction));
        });
        loadFlowService.setOnCancelled(_ -> mainModel.getNotificationsModel().replace(runningNotification,
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
        NotificationAction viewResultsAction = new NotificationAction("desktop.common.action.viewResults", _ ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_COMPONENTS)));
        NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", _ ->
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
                .toList();
        ContingencyList contingencyList = new ListOfContingencyLists(network.getNameOrId(), enabledContingencyLists);

        // OpenLoadFlow's multi-threaded security analysis switches the network to thread-local variants while running,
        // so that any other thread reading it meanwhile fails ("Variant index not set for current thread"), e.g. the FX
        // thread opening a table. So the switch is made here, giving the FX thread (and the task's) its working variant,
        // and undone once the computation is over: OpenLoadFlow then finds it on and leaves it on.
        // The FX thread may still read the network while OpenLoadFlow's threads build their own networks from it, racing
        // on IIDM's lazily computed caches (e.g. the bus view): running the security analysis on a copy of the network
        // would isolate it fully, at the cost of a copy per run.
        VariantManager variantManager = network.getVariantManager();
        String variantId = variantManager.getWorkingVariantId();
        boolean variantMultiThreadAccessAllowed = variantManager.isVariantMultiThreadAccessAllowed();
        variantManager.allowVariantMultiThreadAccess(true);
        variantManager.setWorkingVariant(variantId);
        Runnable restoreVariantAccess = () -> variantManager.allowVariantMultiThreadAccess(variantMultiThreadAccessAllowed);
        AtomicReference<CompletableFuture<SecurityAnalysisReport>> computation = new AtomicReference<>();

        Service<SecurityAnalysisResultAndReport> securityAnalysisService = new Service<>() {
            @Override
            protected Task<SecurityAnalysisResultAndReport> createTask() {
                return new AbstractNetworkTask<>(mainModel, network) {
                    @Override
                    protected SecurityAnalysisResultAndReport compute() throws Exception {
                        variantManager.setWorkingVariant(variantId);
                        ReportNode reportNode = ReportNode.newRootReportNode()
                                .withAllResourceBundlesFromClasspath()
                                .withMessageTemplate("desktop.securityAnalysis")
                                .withTimestamp()
                                .build();

                        List<Contingency> contingencies = ContingencyNames.deduplicate(contingencyList.getContingencies(network));
                        SecurityAnalysisRunParameters runParameters = SecurityAnalysisRunParameters.getDefault()
                                .setSecurityAnalysisParameters(parameters)
                                .addMonitor(SecurityAnalysisResultsController.preContingencyMonitor(network))
                                .setReportNode(reportNode);
                        // SecurityAnalysis.run() blocks on CompletableFuture.join(), which ignores thread interruption,
                        // so cancelling this task wouldn't stop the underlying computation - runAsync()+get() honors it
                        CompletableFuture<SecurityAnalysisReport> future = SecurityAnalysis.runAsync(network, contingencies, runParameters);
                        computation.set(future);
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
            restoreVariantAccess.run();
            SecurityAnalysisResultAndReport securityAnalysisResultAndReport = (SecurityAnalysisResultAndReport) event.getSource().getValue();
            mainModel.getStudy(network).setSecurityAnalysisResult(securityAnalysisResultAndReport.securityAnalysisResult());
            mainModel.setUpdate();
            mainModel.addReport(securityAnalysisResultAndReport.reportNode());
            mainModel.getNotificationsModel().replace(runningNotification,
                    securityAnalysisOutcome(runningNotification.startTimestamp(), securityAnalysisResultAndReport));
        });

        securityAnalysisService.setOnFailed(event -> {
            restoreVariantAccess.run();
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", _ ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.getNotificationsModel().replace(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.securityAnalysis.failed", viewLogsAction));
        });
        securityAnalysisService.setOnCancelled(_ -> {
            // a cancelled computation may still be running, until OpenLoadFlow notices
            CompletableFuture<SecurityAnalysisReport> future = computation.get();
            if (future == null) {
                restoreVariantAccess.run();
            } else {
                future.whenComplete((_, _) -> Platform.runLater(restoreVariantAccess));
            }
            mainModel.getNotificationsModel().replace(runningNotification,
                    Notification.createCancelled(runningNotification.startTimestamp(), "main.securityAnalysis.cancelled"));
        });
        mainModel.getStudy(network).trackComputation(securityAnalysisService);
        securityAnalysisService.start();
    }

    // completing without exception doesn't mean the base case converged - without it, no contingency was simulated
    private Notification securityAnalysisOutcome(Instant start, SecurityAnalysisResultAndReport securityAnalysisResultAndReport) {
        NotificationAction viewResultsAction = new NotificationAction("desktop.common.action.viewResults", _ ->
                mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS)));
        NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", _ ->
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
            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", _ ->
                    mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS)));
            mainModel.getNotificationsModel().add(Notification.createError(startTimestamp, "main.search.indexFailed", viewLogsAction));
        });
        study.trackSearchIndexBuild(network, searchIndexService);
        searchIndexService.start();
    }

    // Every loaded network (imported, merged, detached...) gets its violations computed once, in the background; load
    // flow runs recompute them afterwards (see onLoadFlow). A detached subnetwork's former root keeps its cache: the
    // views skip the violations of equipment no longer in the network.
    private void computeViolations(Network network) {
        Service<NetworkViolations> violationsService = new Service<>() {
            @Override
            protected Task<NetworkViolations> createTask() {
                return new AbstractNetworkTask<>(mainModel, network) {
                    @Override
                    protected NetworkViolations compute() {
                        return NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS);
                    }
                };
            }
        };
        violationsService.setOnSucceeded(event -> {
            if (mainModel.getNetworks().contains(network)) {
                mainModel.getStudy(network).setViolations((NetworkViolations) event.getSource().getValue());
                mainModel.setUpdate();
            }
        });
        violationsService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);
        });
        mainModel.getStudy(network).trackComputation(violationsService);
        violationsService.start();
    }

    // Bus-view merged buses are recalculated on every topology change (switch open/close, terminal
    // connect/disconnect) - both of which route through mainModel.setUpdate() - so the cached index's BUS entries
    // need refreshing too, unlike every other equipment kind (see NetworkSearchIndex's class javadoc). Cheap
    // enough (proportional to bus count, not the whole network) to run synchronously, unlike the initial build;
    // a no-op if the current network has no cached index yet (still building, or none requested). Scoped to just
    // the voltage level the change was confined to when known, cheaper still than the whole-network fallback
    // used when it isn't (e.g. after a load flow run).
    private void onStructureChange(NetworkStructureChange change) {
        mainModel.getStudy(change.rootNetwork()).getSearchIndexes()
                .forEach((network, index) -> index.applyStructureChange(network, change.created(), change.removed()));
        separateWindows.closeRemoved(change.rootNetwork(), change.removed());
    }

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
        return ensureController(controllerClass, fxml, setup, _ -> { });
    }

    // decorate: called with a newly loaded view's root, e.g. to add an open in new window button
    private <T extends DisposableController> T ensureController(Class<T> controllerClass, String fxml, Consumer<T> setup, Consumer<Parent> decorate) {
        if (!controllerClass.isInstance(currentController)) {
            disposeCurrentController();
            Pair<Parent, T> viewAndController = loadView(fxml);
            T controller = viewAndController.getValue();
            currentController = controller;
            setup.accept(controller);
            decorate.accept(viewAndController.getKey());
            borderPane.setCenter(viewAndController.getKey());
        }
        return controllerClass.cast(currentController);
    }

    // a view that can also be opened in a separate window, on the selected network - in a single one, since it shows the
    // whole network
    private <T extends SceneView> T ensureSceneView(Class<T> controllerClass, String fxml, NavigationType type) {
        return ensureController(controllerClass, fxml, c -> c.setSceneModel(sceneModel),
                root -> separateWindows.addOpenButton(root, () -> detachSceneView(fxml, type)));
    }

    private void detachSceneView(String fxml, NavigationType type) {
        Function<SceneModel, SeparateWindows.View> viewFactory = scene -> SeparateWindows.<SceneView>load(fxml, c -> c.setSceneModel(scene));
        if (type == NavigationType.MAP) {
            separateWindows.openFollowingSelection(type.name(), () -> viewTitle(type), viewFactory);
        } else {
            separateWindows.open(type.name(), () -> viewTitle(type), viewFactory);
        }
    }

    private String viewTitle(NavigationType type) {
        Network network = mainModel.getNetwork();
        // the map shows the whole root network
        if (network != null && type == NavigationType.MAP) {
            network = network.getNetwork();
        }
        return (network == null ? NavigationEvent.create(type) : NavigationEvent.create(type, NetworkNavigationState.create(network))).describe();
    }

    @FXML
    private void initialize() {
        mainModel.getNavigationHistory().setGuard(event -> {
            Network target = networkToSelect(event);
            return target == null || mainModel.confirmNetworkChange(target);
        });
        // MainModel briefly sets this property to null before every real dispatch, to force the listener
        // to fire even when navigating to a content-equal NavigationEvent - ignore that transient value
        listenerManager.listen(mainModel.getNavigationHistory().currentEventProperty(), (_, _, newValue) -> {
            if (newValue != null) {
                onNavigationEvent(newValue);
            }
        });
        listenerManager.listen(mainModel.networkProperty(), (_, _, newValue) -> {
            // selected otherwise, e.g. by the user in the networks view
            if (newValue != implicitlySelectedRoot) {
                implicitlySelectedRoot = null;
            }
            ensureSearchIndex(newValue);
        });
        listenerManager.listen(mainModel.structureChangeProperty(), (_, _, change) -> {
            if (change != null) {
                onStructureChange(change);
            }
        });
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> refreshSearchIndexBuses());
        listenerManager.listen(mainModel.getNetworks(), (ListChangeListener<Network>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::computeViolations);
            }
        });
        listenerManager.listen(separateWindows.getOpenWindows(), (ListChangeListener<SeparateWindows.DetachedWindow>) change -> updateWindowsMenu());
        // also on showing: windows' names and tabs change without changing the list
        windowsMenu.setOnShowing(_ -> updateWindowsMenu());
        updateWindowsMenu();
        separateWindows.addDragToOpen(mapButton, NavigationType.MAP.name(), () -> detachSceneView(MAP_FXML, NavigationType.MAP));
        separateWindows.addDragToOpen(contingenciesButton, NavigationType.CONTINGENCIES.name(),
                () -> detachSceneView(CONTINGENCIES_FXML, NavigationType.CONTINGENCIES));
        separateWindows.addDragToOpen(parametersButton, PARAMETERS_WINDOW, this::detachParameters);
        separateWindows.addDragToOpen(logsButton, LOGS_WINDOW, this::detachLogs);
        separateWindows.addDragToOpen(reportsButton, REPORTS_WINDOW, this::detachReports);

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
        borderPane.sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                listenerManager.listen(newScene.windowProperty(), (_, _, _) -> updateCurrentStageTitle());
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

    // the network navigating to event selects, or null if it keeps the selected one. A network state (networks view)
    // selects its network as asked; an equipment or container state holds the equipment's own network: within the
    // selected root network, the selection stays where the user put it - a root network, which shows its subnetworks'
    // equipment too, or a subnetwork - except to show equipment held by the root network (e.g. a tie line between
    // two subnetworks), only shown with the root network selected, or by another subnetwork. A root network selected
    // that way, not asked by the user, is left again for the subnetwork of the next equipment navigated to.
    private Network networkToSelect(NavigationEvent event) {
        if (event.state() == null || event.state().getSelectedNetwork() == null) {
            return null;
        }
        Network target = event.state().getSelectedNetwork();
        Network selected = mainModel.getNetwork();
        if (event.state().getClass() == NetworkNavigationState.class || selected == null || selected.getNetwork() != target.getNetwork()) {
            return target;
        }
        if (selected == target) {
            return null;
        }
        boolean rootSelected = selected == selected.getNetwork();
        if (rootSelected) {
            return selected == implicitlySelectedRoot ? target : null;
        }
        return target;
    }

    private void updateWindowsMenu() {
        if (separateWindows.getOpenWindows().isEmpty()) {
            MenuItem none = new MenuItem(Messages.get("main.menu.windows.none"));
            none.setDisable(true);
            windowsMenu.getItems().setAll(none);
        } else {
            windowsMenu.getItems().setAll(separateWindows.getOpenWindows().stream().map(this::windowMenu).toList());
        }
    }

    private Menu windowMenu(SeparateWindows.DetachedWindow window) {
        Menu menu = new Menu(window.getName());
        menu.setMnemonicParsing(false);
        MenuItem show = new MenuItem(Messages.get("main.menu.windows.show"));
        show.setOnAction(_ -> window.show());
        MenuItem rename = new MenuItem(Messages.get("main.menu.windows.rename"));
        rename.setOnAction(_ -> renameWindow(window));
        menu.getItems().addAll(show, rename, new SeparatorMenuItem());
        List<String> tabTitles = window.getTabTitles();
        for (int i = 0; i < tabTitles.size(); i++) {
            int index = i;
            MenuItem tab = new MenuItem(tabTitles.get(i));
            tab.setMnemonicParsing(false);
            tab.setOnAction(_ -> window.showTab(index));
            menu.getItems().add(tab);
        }
        return menu;
    }

    private void renameWindow(SeparateWindows.DetachedWindow window) {
        TextInputDialog dialog = new TextInputDialog(window.getName());
        dialog.initOwner(borderPane.getScene().getWindow());
        dialog.setTitle(Messages.get("main.menu.windows.rename.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("main.menu.windows.rename.name"));
        dialog.showAndWait().ifPresent(window::rename);
    }

    private void onNavigationEvent(NavigationEvent newValue) {
        Objects.requireNonNull(newValue);
        Network target = networkToSelect(newValue);
        if (target != null) {
            Network previous = mainModel.getNetwork();
            this.mainModel.setNetwork(target);
            // a root network selected to show its own equipment, from one of its subnetworks
            boolean implicitRoot = target == target.getNetwork() && newValue.state().getClass() != NetworkNavigationState.class
                    && previous != null && previous != target && previous.getNetwork() == target;
            implicitlySelectedRoot = implicitRoot ? target : null;
        }
        if (newValue.navigationType() == NavigationType.LOGS) {
            ensureController(LogsViewController.class, "logs/logs-view.fxml", c -> c.setModels(mainModel.getLogsModel(), mainModel.getParametersModel()),
                    root -> separateWindows.addOpenButton(root, this::detachLogs));
        } else if (newValue.navigationType() == NavigationType.NETWORKS) {
            NetworksController controller = ensureController(NetworksController.class, "network/networks-view.fxml", c -> c.setMainModel(mainModel));
            if (newValue.state() instanceof NetworkNavigationState state) {
                controller.navigateTo(state.getSelectedNetwork());
            } else if (newValue.state() == null) {
                controller.navigateTo(null);
            }
        } else if (newValue.navigationType() == NavigationType.SUBSTATIONS) {
            SubstationsController controller = ensureController(SubstationsController.class, "network/substations-view.fxml", c -> c.setSceneModel(sceneModel));
            if (newValue.state() instanceof ContainerNavigationState state) {
                controller.navigateTo(state.getContainer(), state.getTab(), state.getEquipment());
            } else if (newValue.state() == null) {
                controller.navigateTo(null);
            }
        } else if (newValue.navigationType() == NavigationType.MAP) {
            ensureSceneView(MapController.class, MAP_FXML, NavigationType.MAP);
        } else if (newValue.navigationType() == NavigationType.CONTINGENCIES) {
            ensureSceneView(ContingenciesController.class, CONTINGENCIES_FXML, NavigationType.CONTINGENCIES);
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SUBSTATIONS) {
            SubstationsTableController controller = ensureSceneView(SubstationsTableController.class,
                    "network/tables/substations-view.fxml", NavigationType.NETWORK_TABLE_SUBSTATIONS);
            if (newValue.state() instanceof SubstationNavigationState state) {
                controller.goToSubstation(state.getSubstation());
            } else if (newValue.state() == null) {
                controller.goToSubstation(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS) {
            VoltageLevelsController controller = ensureSceneView(VoltageLevelsController.class,
                    "network/tables/voltage-levels-view.fxml", NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS);
            if (newValue.state() instanceof VoltageLevelNavigationState state) {
                controller.goToVoltageLevel(state.getVoltageLevel());
            } else if (newValue.state() == null) {
                controller.goToVoltageLevel(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS) {
            BusbarSectionsController controller = ensureSceneView(BusbarSectionsController.class,
                    "network/tables/busbar-sections-view.fxml", NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS);
            if (newValue.state() instanceof BusbarSectionNavigationState state) {
                controller.goToBusbarSection(state.getBusbarSection());
            } else if (newValue.state() == null) {
                controller.goToBusbarSection(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW) {
            BusesBusViewController controller = ensureSceneView(BusesBusViewController.class,
                    "network/tables/buses-bus-view.fxml", NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW);
            if (newValue.state() instanceof BusNavigationState state) {
                controller.goToBus(state.getBus());
            } else if (newValue.state() == null) {
                controller.goToBus(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW) {
            BusesBusBreakerViewController controller = ensureSceneView(BusesBusBreakerViewController.class,
                    "network/tables/buses-bus-breaker-view.fxml", NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW);
            if (newValue.state() instanceof BusNavigationState state) {
                controller.goToBus(state.getBus());
            } else if (newValue.state() == null) {
                controller.goToBus(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_GENERATORS) {
            GeneratorsController controller = ensureSceneView(GeneratorsController.class,
                    "network/tables/generators-view.fxml", NavigationType.NETWORK_TABLE_GENERATORS);
            if (newValue.state() instanceof GeneratorNavigationState state) {
                controller.goToGenerator(state.getGenerator());
            } else if (newValue.state() == null) {
                controller.goToGenerator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BATTERIES) {
            BatteriesController controller = ensureSceneView(BatteriesController.class,
                    "network/tables/batteries-view.fxml", NavigationType.NETWORK_TABLE_BATTERIES);
            if (newValue.state() instanceof BatteryNavigationState state) {
                controller.goToBattery(state.getBattery());
            } else if (newValue.state() == null) {
                controller.goToBattery(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS) {
            ShuntCompensatorsController controller = ensureSceneView(ShuntCompensatorsController.class,
                    "network/tables/shunt-compensators-view.fxml", NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS);
            if (newValue.state() instanceof ShuntCompensatorNavigationState state) {
                controller.goToShuntCompensator(state.getShuntCompensator());
            } else if (newValue.state() == null) {
                controller.goToShuntCompensator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS) {
            StaticVarCompensatorsController controller = ensureSceneView(StaticVarCompensatorsController.class,
                    "network/tables/static-var-compensators-view.fxml", NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS);
            if (newValue.state() instanceof StaticVarCompensatorNavigationState state) {
                controller.goToStaticVarCompensator(state.getStaticVarCompensator());
            } else if (newValue.state() == null) {
                controller.goToStaticVarCompensator(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS) {
            VscConverterStationsController controller = ensureSceneView(VscConverterStationsController.class,
                    "network/tables/vsc-converter-stations-view.fxml", NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS);
            if (newValue.state() instanceof VscConverterStationNavigationState state) {
                controller.goToVscConverterStation(state.getVscConverterStation());
            } else if (newValue.state() == null) {
                controller.goToVscConverterStation(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_LCC_CONVERTER_STATIONS) {
            LccConverterStationsController controller = ensureSceneView(LccConverterStationsController.class,
                    "network/tables/lcc-converter-stations-view.fxml", NavigationType.NETWORK_TABLE_LCC_CONVERTER_STATIONS);
            if (newValue.state() instanceof LccConverterStationNavigationState state) {
                controller.goToLccConverterStation(state.getLccConverterStation());
            } else if (newValue.state() == null) {
                controller.goToLccConverterStation(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_LOADS) {
            LoadsController controller = ensureSceneView(LoadsController.class, "network/tables/loads-view.fxml", NavigationType.NETWORK_TABLE_LOADS);
            if (newValue.state() instanceof LoadNavigationState state) {
                controller.goToLoad(state.getLoad());
            } else if (newValue.state() == null) {
                controller.goToLoad(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_LINES) {
            LinesController controller = ensureSceneView(LinesController.class, "network/tables/lines-view.fxml", NavigationType.NETWORK_TABLE_LINES);
            if (newValue.state() instanceof LineNavigationState state) {
                controller.goToLine(state.getLine());
            } else if (newValue.state() == null) {
                controller.goToLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_TRANSFORMERS) {
            TransformersController controller = ensureSceneView(TransformersController.class,
                    "network/tables/transformers-view.fxml", NavigationType.NETWORK_TABLE_TRANSFORMERS);
            if (newValue.state() instanceof TransformerNavigationState state) {
                controller.goToTransformer(state.getTransformer());
            } else if (newValue.state() == null) {
                controller.goToTransformer(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_TIE_LINES) {
            TieLinesController controller = ensureSceneView(TieLinesController.class, "network/tables/tie-lines-view.fxml", NavigationType.NETWORK_TABLE_TIE_LINES);
            if (newValue.state() instanceof TieLineNavigationState state) {
                controller.goToTieLine(state.getTieLine());
            } else if (newValue.state() == null) {
                controller.goToTieLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_BOUNDARY_LINES) {
            BoundaryLinesController controller = ensureSceneView(BoundaryLinesController.class,
                    "network/tables/boundary-lines-view.fxml", NavigationType.NETWORK_TABLE_BOUNDARY_LINES);
            if (newValue.state() instanceof BoundaryLineNavigationState state) {
                controller.goToBoundaryLine(state.getBoundaryLine());
            } else if (newValue.state() == null) {
                controller.goToBoundaryLine(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_HVDC_LINES) {
            HvdcLinesController controller = ensureSceneView(HvdcLinesController.class, "network/tables/hvdc-lines-view.fxml", NavigationType.NETWORK_TABLE_HVDC_LINES);
            if (newValue.state() instanceof HvdcLineNavigationState state) {
                controller.goToHvdcLine(state.getHvdcLine());
            } else if (newValue.state() == null) {
                controller.goToHvdcLine(null);
            }
        } else if (NETWORK_OBJECT_TABLES.contains(newValue.navigationType())) {
            onNetworkObjectTableEvent(newValue);
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_COMPONENTS) {
            ensureSceneView(ComponentsController.class, "network/tables/components-view.fxml", NavigationType.NETWORK_TABLE_COMPONENTS);
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS) {
            ensureSceneView(SecurityAnalysisResultsController.class,
                    "network/tables/security-analysis-results-view.fxml", NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS);
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_VIOLATIONS) {
            ensureSceneView(ViolationsController.class,
                    "network/tables/violations-view.fxml", NavigationType.NETWORK_TABLE_VIOLATIONS);
        } else if (newValue.navigationType() == NavigationType.PARAMETERS) {
            ensureController(ParametersController.class, "parameters/parameters-view.fxml", c -> c.setMainModel(mainModel),
                    root -> separateWindows.addOpenButton(root, this::detachParameters));
        } else if (newValue.navigationType() == NavigationType.REPORTS) {
            ReportsController controller = ensureController(ReportsController.class, "report/reports-view.fxml", c -> c.setSceneModel(sceneModel),
                    root -> separateWindows.addOpenButton(root, this::detachReports));
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

    public void onParameters() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.PARAMETERS));
    }

    private void detachParameters() {
        separateWindows.openSingleInstance(PARAMETERS_WINDOW, () -> Messages.get("desktop.common.parameters"), _ -> {
            SeparateWindows.View view = SeparateWindows.<ParametersController>load("parameters/parameters-view.fxml", c -> c.setMainModel(mainModel));
            return new SeparateWindows.View(view.root(), view.controller(), 1000, 700);
        });
    }

    private void detachLogs() {
        separateWindows.openSingleInstance(LOGS_WINDOW, () -> NavigationEvent.create(NavigationType.LOGS).describe(),
                _ -> SeparateWindows.<LogsViewController>load("logs/logs-view.fxml",
                        c -> c.setModels(mainModel.getLogsModel(), mainModel.getParametersModel())));
    }

    private void detachReports() {
        separateWindows.openSingleInstance(REPORTS_WINDOW, () -> NavigationEvent.create(NavigationType.REPORTS).describe(),
                scene -> SeparateWindows.<ReportsController>load("report/reports-view.fxml", c -> c.setSceneModel(scene)));
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

    public void onBatteries() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BATTERIES, BatteryNavigationState.createNoBattery(mainModel.getNetwork())));
    }

    public void onShuntCompensators() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS,
                ShuntCompensatorNavigationState.createNoShuntCompensator(mainModel.getNetwork())));
    }

    public void onStaticVarCompensators() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS,
                StaticVarCompensatorNavigationState.createNoStaticVarCompensator(mainModel.getNetwork())));
    }

    public void onVscConverterStations() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS,
                VscConverterStationNavigationState.createNoVscConverterStation(mainModel.getNetwork())));
    }

    public void onLccConverterStations() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_LCC_CONVERTER_STATIONS,
                LccConverterStationNavigationState.createNoLccConverterStation(mainModel.getNetwork())));
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

    public void onHvdcLines() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_HVDC_LINES, HvdcLineNavigationState.createNoHvdcLine(mainModel.getNetwork())));
    }

    // kept out of onNavigationEvent, whose length checkstyle limits
    private void onNetworkObjectTableEvent(NavigationEvent newValue) {
        if (newValue.navigationType() == NavigationType.NETWORK_TABLE_GROUNDS) {
            GroundsController controller = ensureSceneView(GroundsController.class, "network/tables/grounds-view.fxml", NavigationType.NETWORK_TABLE_GROUNDS);
            if (newValue.state() instanceof GroundNavigationState state) {
                controller.goToGround(state.getGround());
            } else if (newValue.state() == null) {
                controller.goToGround(null);
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_AREAS) {
            AreasController controller = ensureSceneView(AreasController.class, "network/tables/areas-view.fxml", NavigationType.NETWORK_TABLE_AREAS);
            if (newValue.state() instanceof AreaNavigationState state) {
                controller.goToArea(state.getArea());
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_OVERLOAD_MANAGEMENT_SYSTEMS) {
            OverloadManagementSystemsController controller = ensureSceneView(OverloadManagementSystemsController.class,
                    "network/tables/overload-management-systems-view.fxml", NavigationType.NETWORK_TABLE_OVERLOAD_MANAGEMENT_SYSTEMS);
            if (newValue.state() instanceof OverloadManagementSystemNavigationState state) {
                controller.goToOverloadManagementSystem(state.getOverloadManagementSystem());
            }
        } else if (newValue.navigationType() == NavigationType.NETWORK_TABLE_VOLTAGE_ANGLE_LIMITS) {
            ensureSceneView(VoltageAngleLimitsController.class, "network/tables/voltage-angle-limits-view.fxml",
                    NavigationType.NETWORK_TABLE_VOLTAGE_ANGLE_LIMITS);
        }
    }

    public void onGrounds() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_GROUNDS, GroundNavigationState.createNoGround(mainModel.getNetwork())));
    }

    public void onAreas() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_AREAS, AreaNavigationState.createNoArea(mainModel.getNetwork())));
    }

    public void onOverloadManagementSystems() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_OVERLOAD_MANAGEMENT_SYSTEMS,
                OverloadManagementSystemNavigationState.createNoOverloadManagementSystem(mainModel.getNetwork())));
    }

    public void onVoltageAngleLimits() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_VOLTAGE_ANGLE_LIMITS,
                NetworkNavigationState.create(mainModel.getNetwork())));
    }

    public void onComponents() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_COMPONENTS));
    }

    public void onSecurityAnalysisResults() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS));
    }

    public void onViolations() {
        mainModel.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_VIOLATIONS));
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
            item.setOnAction(_ -> {
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
