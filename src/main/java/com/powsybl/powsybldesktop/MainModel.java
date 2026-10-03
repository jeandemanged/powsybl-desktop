/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.logs.LogsModel;
import com.powsybl.powsybldesktop.map.MapController;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;
import com.powsybl.powsybldesktop.parameters.DesktopParameters;
import com.powsybl.powsybldesktop.parameters.DesktopSldParameters;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MainModel {
    private static final int MAX_NAVIGATION_PAST_SIZE = 30;

    private final ObservableList<NavigationEvent> navigationPast = FXCollections.observableArrayList();
    private final ObservableList<NavigationEvent> navigationFuture = FXCollections.observableArrayList();
    private final ObservableList<Network> networks = FXCollections.observableArrayList();
    private final ObservableList<ReportNode> reports = FXCollections.observableArrayList();
    private final ObservableList<Notification> notifications = FXCollections.observableArrayList();

    // Unmodifiable views, cached rather than wrapped on every getter call: the wrapper forwards
    // changes via a WeakListChangeListener on the backing list, so a fresh, unreferenced wrapper
    // is liable to be garbage-collected and silently stop forwarding to whoever listens on it.
    private final ObservableList<NavigationEvent> navigationPastView = FXCollections.unmodifiableObservableList(navigationPast);
    private final ObservableList<NavigationEvent> navigationFutureView = FXCollections.unmodifiableObservableList(navigationFuture);
    private final ObservableList<ReportNode> reportsView = FXCollections.unmodifiableObservableList(reports);
    private final ObservableList<Notification> notificationsView = FXCollections.unmodifiableObservableList(notifications);
    private final ObjectProperty<Network> network = new SimpleObjectProperty<>();
    private final ObjectProperty<Instant> update = new SimpleObjectProperty<>();
    // the single voltage level touched by the change that triggered the most recent update() firing, or null
    // if the change isn't confined to one (e.g. a load flow run) - read by MainController.refreshSearchIndexBuses()
    // to scope its search index refresh instead of re-indexing every bus in the network
    private VoltageLevel updatedVoltageLevel;
    private final ObjectProperty<LoadFlowParameters> loadFlowParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<SecurityAnalysisParameters> securityAnalysisParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<DesktopSldParameters> sldParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<DesktopNadParameters> nadParameters = new SimpleObjectProperty<>();
    // the diagram parameters are edited in place, so their properties don't fire: these counters are bumped
    // instead, for the displayed diagrams to re-render
    private final LongProperty sldParametersRevision = new SimpleLongProperty();
    private final LongProperty nadParametersRevision = new SimpleLongProperty();
    // bumped on any parameter edit or replacement, for the parameters view to compare them against the saved file
    private final LongProperty parametersRevision = new SimpleLongProperty();
    // JSON of the parameters as last saved to / restored from the configuration file, null if there's none
    private JsonNode savedParameters;
    // keyed by format as listed in the parameters view / import-export menus (e.g. "IIDM" for all IIDM importers),
    // holding only the values the user edited so the importer/exporter falls back to its own defaults otherwise
    private final Map<String, Properties> networkImportParameters = new HashMap<>();
    private final Map<String, Properties> networkExportParameters = new HashMap<>();
    private final Map<Network, LoadFlowResult> loadFlowResults = new HashMap<>();
    private final Map<Network, SecurityAnalysisResult> securityAnalysisResults = new HashMap<>();
    private final Map<Network, NetworkSearchIndex> searchIndexes = new HashMap<>();
    private final Map<Network, ObservableList<ContingencyList>> contingencyLists = new HashMap<>();
    private final Map<Network, MapView> mapViews = new HashMap<>();
    // Running background operations, kept here rather than in MainController so that a language reload, which
    // replaces MainController, doesn't forget them and allow a second concurrent run on the same network
    private final Map<Network, Service<?>> loadFlowServices = new HashMap<>();
    private final Map<Network, Service<?>> securityAnalysisServices = new HashMap<>();
    private final Map<Network, Service<?>> searchIndexServices = new HashMap<>();
    // Whether a sublist is included when contingencies are resolved for a security analysis run, keyed by
    // identity since editing a sublist's form replaces it with a brand-new instance (see ContingenciesController's
    // showForm/onReplace) rather than mutating it in place - transferContingencyListEnabled carries the flag
    // over to the replacement so an edit doesn't silently re-enable a sublist the user disabled.
    private final Map<ContingencyList, BooleanProperty> contingencyListEnabled = new IdentityHashMap<>();
    private final ObjectProperty<NetworkSearchIndex.State> searchIndexState = new SimpleObjectProperty<>(NetworkSearchIndex.State.NOT_BUILT);
    private final ObjectProperty<NavigationEvent> navigationEvent = new SimpleObjectProperty<>();
    private final LogsModel logsModel = new LogsModel();
    private final BooleanProperty notificationsPanelOpen = new SimpleBooleanProperty();
    private final DoubleProperty diagramZoom = new SimpleDoubleProperty(1.0);
    private final BooleanProperty diagramFitToScreen = new SimpleBooleanProperty(false);
    private final IntegerProperty diagramAreaDepth = new SimpleIntegerProperty(1);
    private final ObservableSet<String> mapHiddenBaseVoltages = FXCollections.observableSet();
    private MapController.Basemap mapBasemap = MapController.Basemap.OFFLINE;

    public MainModel() {
        DesktopParameters defaults = DesktopParameters.createDefault();
        loadFlowParameters.setValue(defaults.loadFlow());
        securityAnalysisParameters.setValue(defaults.securityAnalysis());
        syncSecurityAnalysisLoadFlowParameters();
        // SecurityAnalysisParameters embeds a LoadFlowParameters, but this app has a single authoritative
        // LoadFlowParameters instance (loadFlowParameters above); keep it wired into whichever
        // SecurityAnalysisParameters is current rather than letting the two diverge (e.g. after importing a
        // security analysis JSON that carries its own, stale load flow section).
        loadFlowParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        securityAnalysisParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        sldParameters.setValue(defaults.sld());
        nadParameters.setValue(defaults.nad());
        loadFlowParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        securityAnalysisParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        sldParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        nadParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        update.setValue(Instant.now());
    }

    private void syncSecurityAnalysisLoadFlowParameters() {
        securityAnalysisParameters.getValue().setLoadFlowParameters(loadFlowParameters.getValue());
    }

    public void addNetwork(Network network) {
        this.networks.add(network);
        if (this.network.get() == null) {
            setNetwork(network);
        }
    }

    public ObservableList<Network> getNetworks() {
        return networks;
    }

    public void removeNetwork(Network network) {
        Objects.requireNonNull(network);
        // the selection and history entries can be a subnetwork of the removed root network
        boolean wasSelected = isPartOf(this.network.get(), network);
        networks.remove(network);
        // cancelled first, so that a run finishing afterwards can't store a result for the removed network
        cancelServices(loadFlowServices, network);
        cancelServices(securityAnalysisServices, network);
        cancelServices(searchIndexServices, network);
        navigationPast.removeIf(event -> isRelatedToNetwork(event, network));
        navigationFuture.removeIf(event -> isRelatedToNetwork(event, network));
        loadFlowResults.remove(network);
        securityAnalysisResults.remove(network);
        ObservableList<ContingencyList> lists = contingencyLists.remove(network);
        if (lists != null) {
            lists.forEach(contingencyListEnabled::remove);
        }
        // the Map view can show a subnetwork too, whose view goes with its root network
        mapViews.keySet().removeIf(n -> n.getNetwork() == network.getNetwork());
        removeSearchIndexes(network);
        if (isRelatedToNetwork(navigationEvent.get(), network)) {
            // a language reload re-fires the current event, which would otherwise re-select the removed network
            navigationEvent.setValue(null);
        }
        if (wasSelected) {
            setNetwork(null);
        }
    }

    // Closes the cached search indexes of a root network and of all its subnetworks
    public void removeSearchIndexes(Network rootNetwork) {
        searchIndexes.entrySet().removeIf(entry -> {
            if (isPartOf(entry.getKey(), rootNetwork)) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    // collected first: cancelling runs the services' onCancelled handlers, which remove them from the map
    private static void cancelServices(Map<Network, Service<?>> services, Network rootNetwork) {
        services.entrySet().stream()
                .filter(entry -> isPartOf(entry.getKey(), rootNetwork))
                .map(Map.Entry::getValue)
                .toList()
                .forEach(Service::cancel);
    }

    public Map<Network, Service<?>> getLoadFlowServices() {
        return loadFlowServices;
    }

    public Map<Network, Service<?>> getSecurityAnalysisServices() {
        return securityAnalysisServices;
    }

    public Map<Network, Service<?>> getSearchIndexServices() {
        return searchIndexServices;
    }

    private static boolean isPartOf(Network network, Network rootNetwork) {
        return network != null && network.getNetwork() == rootNetwork;
    }

    // Subnetwork.detach() moves the equipment into a new network and leaves the subnetwork object empty and
    // orphaned, so nothing may keep referencing it - and the root's search indexes still hold the moved equipment.
    public void detachSubnetworks(List<Network> subnetworks) {
        Network rootNetwork = subnetworks.getFirst().getNetwork();
        Predicate<NavigationEvent> related = event -> event != null && event.state() != null
                && subnetworks.contains(event.state().getSelectedNetwork());
        navigationPast.removeIf(related);
        navigationFuture.removeIf(related);
        if (related.test(navigationEvent.get())) {
            navigationEvent.setValue(null);
        }
        cancelServices(searchIndexServices, rootNetwork);
        removeSearchIndexes(rootNetwork);
        mapViews.keySet().removeIf(n -> isPartOf(n, rootNetwork));
        // contingency lists stay with the root network they were edited for
        for (Network subnetwork : subnetworks) {
            Network detached = subnetwork.detach();
            networks.add(detached);
            if (subnetwork == subnetworks.getFirst()) {
                // always a new selection, so that the network listeners run again (e.g. rebuilding a search index)
                setNetwork(detached);
            }
        }
    }

    public void removeAllNetworks() {
        List.copyOf(networks).forEach(this::removeNetwork);
    }

    private static boolean isRelatedToNetwork(NavigationEvent event, Network network) {
        return event != null && event.state() != null && isPartOf(event.state().getSelectedNetwork(), network);
    }

    public void setNetwork(Network network) {
        // set before the network itself: MainController's network listener may then switch it to BUILDING
        NetworkSearchIndex cached = network == null ? null : searchIndexes.get(network);
        searchIndexState.setValue(cached != null ? NetworkSearchIndex.State.READY : NetworkSearchIndex.State.NOT_BUILT);
        this.network.setValue(network);
    }

    public Network getNetwork() {
        return network.get();
    }

    public ObjectProperty<Network> networkProperty() {
        return network;
    }

    public NetworkSearchIndex getSearchIndex(Network network) {
        return network == null ? null : searchIndexes.get(network);
    }

    // Only updates searchIndexState if the built network is still the one currently selected - a network can be
    // deselected (or removed) while its index is still building in the background.
    public void setSearchIndex(Network network, NetworkSearchIndex index) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(index);
        searchIndexes.put(network, index);
        if (network.equals(this.network.get())) {
            searchIndexState.setValue(NetworkSearchIndex.State.READY);
        }
    }

    public ObjectProperty<NetworkSearchIndex.State> searchIndexStateProperty() {
        return searchIndexState;
    }

    // Keyed by root network only (load flow always runs on the root, never a subnetwork - see MainController.onLoadFlow),
    // so a subnetwork passed in here is resolved to its root to still hit the result of the load flow it was part of.
    public void setLoadFlowResult(Network network, LoadFlowResult loadFlowResult) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(loadFlowResult);
        loadFlowResults.put(network.getNetwork(), loadFlowResult);
    }

    public LoadFlowResult getLoadFlowResult(Network network) {
        return loadFlowResults.get(network.getNetwork());
    }

    // Keyed by root network only, for the same reason as setLoadFlowResult - security analysis always runs on
    // the root network too (see MainController.onSecurityAnalysis).
    public void setSecurityAnalysisResult(Network network, SecurityAnalysisResult securityAnalysisResult) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(securityAnalysisResult);
        securityAnalysisResults.put(network.getNetwork(), securityAnalysisResult);
    }

    public SecurityAnalysisResult getSecurityAnalysisResult(Network network) {
        return securityAnalysisResults.get(network.getNetwork());
    }

    // Keyed by root network only, like the security analysis results: contingency lists are edited for the root
    // network the security analysis runs on, never for a subnetwork
    public ObservableList<ContingencyList> getContingencyLists(Network network) {
        return contingencyLists.computeIfAbsent(network.getNetwork(), n -> FXCollections.observableArrayList());
    }

    public BooleanProperty contingencyListEnabledProperty(ContingencyList list) {
        return contingencyListEnabled.computeIfAbsent(list, l -> new SimpleBooleanProperty(true));
    }

    public void transferContingencyListEnabled(ContingencyList from, ContingencyList to) {
        BooleanProperty property = contingencyListEnabled.remove(from);
        if (property != null) {
            contingencyListEnabled.put(to, property);
        }
    }

    public void setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        Objects.requireNonNull(loadFlowParameters);
        this.loadFlowParameters.setValue(loadFlowParameters);
    }

    public ObjectProperty<LoadFlowParameters> loadFlowParametersProperty() {
        return loadFlowParameters;
    }

    public ObjectProperty<SecurityAnalysisParameters> securityAnalysisParametersProperty() {
        return securityAnalysisParameters;
    }

    public ObjectProperty<DesktopSldParameters> sldParametersProperty() {
        return sldParameters;
    }

    public ObjectProperty<DesktopNadParameters> nadParametersProperty() {
        return nadParameters;
    }

    public ReadOnlyLongProperty sldParametersRevisionProperty() {
        return sldParametersRevision;
    }

    public void sldParametersChanged() {
        sldParametersRevision.set(sldParametersRevision.get() + 1);
        parametersChanged();
    }

    public ReadOnlyLongProperty nadParametersRevisionProperty() {
        return nadParametersRevision;
    }

    public void nadParametersChanged() {
        nadParametersRevision.set(nadParametersRevision.get() + 1);
        parametersChanged();
    }

    public ReadOnlyLongProperty parametersRevisionProperty() {
        return parametersRevision;
    }

    public void parametersChanged() {
        parametersRevision.set(parametersRevision.get() + 1);
    }

    // the returned objects are the live ones, edited in place by the parameters view
    public DesktopParameters getParameters() {
        return new DesktopParameters(Map.copyOf(networkImportParameters), Map.copyOf(networkExportParameters),
                sldParameters.getValue(), nadParameters.getValue(), loadFlowParameters.getValue(), securityAnalysisParameters.getValue());
    }

    public void setParameters(DesktopParameters parameters) {
        Objects.requireNonNull(parameters);
        networkImportParameters.clear();
        networkImportParameters.putAll(copy(parameters.networkImport()));
        networkExportParameters.clear();
        networkExportParameters.putAll(copy(parameters.networkExport()));
        securityAnalysisParameters.setValue(parameters.securityAnalysis());
        loadFlowParameters.setValue(parameters.loadFlow());
        sldParameters.setValue(parameters.sld());
        nadParameters.setValue(parameters.nad());
        sldParametersChanged();
        nadParametersChanged();
    }

    private static Map<String, Properties> copy(Map<String, Properties> parameters) {
        return parameters.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            Properties properties = new Properties();
            properties.putAll(e.getValue());
            return properties;
        }));
    }

    public JsonNode getSavedParameters() {
        return savedParameters;
    }

    public void setSavedParameters(JsonNode savedParameters) {
        this.savedParameters = savedParameters;
    }

    public Properties getNetworkImportParameters(String format) {
        return networkImportParameters.computeIfAbsent(format, f -> new Properties());
    }

    public Properties getNetworkExportParameters(String format) {
        return networkExportParameters.computeIfAbsent(format, f -> new Properties());
    }

    public ObjectProperty<Instant> updateProperty() {
        return update;
    }

    public void setUpdate() {
        updatedVoltageLevel = null;
        update.setValue(Instant.now());
    }

    // for a change confined to one voltage level (switch open/close, terminal connect/disconnect) - lets
    // MainController.refreshSearchIndexBuses() re-index only that voltage level's buses
    public void setUpdate(VoltageLevel voltageLevel) {
        updatedVoltageLevel = Objects.requireNonNull(voltageLevel);
        update.setValue(Instant.now());
    }

    public VoltageLevel getUpdatedVoltageLevel() {
        return updatedVoltageLevel;
    }

    public ObjectProperty<NavigationEvent> navigationEventProperty() {
        return navigationEvent;
    }

    public void addNavigationEvent(NavigationEvent navigationEvent, boolean notify) {
        Objects.requireNonNull(navigationEvent);
        if (!navigationPast.isEmpty() && navigationPast.getLast().equals(navigationEvent)) {
            return;
        }
        if (notify) {
            dispatch(navigationEvent);
        }
        navigationPast.add(navigationEvent);
        while (navigationPast.size() > MAX_NAVIGATION_PAST_SIZE) {
            navigationPast.removeFirst();
        }
        navigationFuture.clear();
    }

    // NavigationEvent/ContainerNavigationState etc. are records/override equals(), and this property is only
    // ever read via a ChangeListener (MainController) - JavaFX suppresses that listener when the new value
    // content-equals the currently held one (e.g. navigating back to a container+tab that was itself the
    // last *notified* event, with only notify=false pushes in between), silently dropping the navigation.
    // Routing every dispatch through a transient null forces the listener to fire regardless of equality.
    private void dispatch(NavigationEvent navigationEvent) {
        this.navigationEvent.setValue(null);
        this.navigationEvent.setValue(navigationEvent);
    }

    public void addNavigationEvent(NavigationEvent navigationEvent) {
        addNavigationEvent(navigationEvent, true);
    }

    public ObservableList<NavigationEvent> getNavigationPast() {
        return navigationPastView;
    }

    public ObservableList<NavigationEvent> getNavigationFuture() {
        return navigationFutureView;
    }

    public void navigateBackward() {
        if (navigationPast.size() > 1) {
            navigateBackwardToIndex(navigationPast.size() - 2);
        }
    }

    public void navigateForward() {
        if (!navigationFuture.isEmpty()) {
            navigateForwardToIndex(navigationFuture.size() - 1);
        }
    }

    public void navigateBackwardToIndex(int index) {
        if (index < 0 || index >= navigationPast.size() - 1) {
            return;
        }
        while (navigationPast.size() - 1 > index) {
            navigationFuture.add(navigationPast.removeLast());
        }
        dispatch(navigationPast.getLast());
    }

    public void navigateForwardToIndex(int index) {
        if (index < 0 || index >= navigationFuture.size()) {
            return;
        }
        while (navigationFuture.size() - 1 >= index) {
            navigationPast.add(navigationFuture.removeLast());
        }
        dispatch(navigationPast.getLast());
    }

    public LogsModel getLogsModel() {
        return logsModel;
    }

    public void addReport(ReportNode reportNode) {
        Objects.requireNonNull(reportNode);
        reports.add(reportNode);
    }

    public ObservableList<ReportNode> getReports() {
        return reportsView;
    }

    public void clearReports() {
        reports.clear();
    }

    public void addNotification(Notification notification) {
        Objects.requireNonNull(notification);
        notifications.add(notification);
    }

    public void removeNotification(Notification notification) {
        Objects.requireNonNull(notification);
        notifications.remove(notification);
    }

    public void replaceNotification(Notification oldNotification, Notification newNotification) {
        Objects.requireNonNull(oldNotification);
        Objects.requireNonNull(newNotification);
        int index = notifications.indexOf(oldNotification);
        if (index >= 0) {
            notifications.set(index, newNotification);
        } else {
            notifications.add(newNotification);
        }
    }

    // running ones are kept: they carry the only way to cancel their operation, and are replaced by its outcome
    public void clearNotifications() {
        notifications.removeIf(notification -> notification.status() != NotificationStatus.RUNNING);
    }

    public ObservableList<Notification> getNotifications() {
        return notificationsView;
    }

    public BooleanProperty notificationsPanelOpenProperty() {
        return notificationsPanelOpen;
    }

    public boolean isNotificationsPanelOpen() {
        return notificationsPanelOpen.get();
    }

    public void setNotificationsPanelOpen(boolean open) {
        notificationsPanelOpen.set(open);
    }

    public DoubleProperty diagramZoomProperty() {
        return diagramZoom;
    }

    public double getDiagramZoom() {
        return diagramZoom.get();
    }

    public void setDiagramZoom(double zoom) {
        diagramZoom.set(zoom);
    }

    public BooleanProperty diagramFitToScreenProperty() {
        return diagramFitToScreen;
    }

    public boolean isDiagramFitToScreen() {
        return diagramFitToScreen.get();
    }

    public void setDiagramFitToScreen(boolean fitToScreen) {
        diagramFitToScreen.set(fitToScreen);
    }

    public IntegerProperty diagramAreaDepthProperty() {
        return diagramAreaDepth;
    }

    public int getDiagramAreaDepth() {
        return diagramAreaDepth.get();
    }

    public void setDiagramAreaDepth(int depth) {
        diagramAreaDepth.set(depth);
    }

    public MapController.Basemap getMapBasemap() {
        return mapBasemap;
    }

    public void setMapBasemap(MapController.Basemap basemap) {
        mapBasemap = Objects.requireNonNull(basemap);
    }

    /**
     * Names of the base voltages (see {@code BaseVoltagesConfig}) whose substations and lines the Map view hides.
     */
    public ObservableSet<String> getMapHiddenBaseVoltages() {
        return mapHiddenBaseVoltages;
    }

    /**
     * Center and zoom of the Map view, as last panned or zoomed by the user.
     */
    public record MapView(double latitude, double longitude, double zoom) {
    }

    /**
     * The Map view's last view of {@code network}, or null if the user never panned or zoomed it: the view is then
     * fitted to the network.
     */
    public MapView getMapView(Network network) {
        return mapViews.get(network);
    }

    public void setMapView(Network network, MapView mapView) {
        mapViews.put(Objects.requireNonNull(network), Objects.requireNonNull(mapView));
    }
}
