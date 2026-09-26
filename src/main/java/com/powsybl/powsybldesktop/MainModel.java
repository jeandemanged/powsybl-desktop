/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.nad.NadParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.powsybldesktop.logs.LogsModel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.sld.SldParameters;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.svg.SvgParameters;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

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
    private final ObjectProperty<SldParameters> sldParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<NadParameters> nadParameters = new SimpleObjectProperty<>();
    // keyed by format as listed in the parameters view / import-export menus (e.g. "IIDM" for all IIDM importers),
    // holding only the values the user edited so the importer/exporter falls back to its own defaults otherwise
    private final Map<String, Properties> networkImportParameters = new HashMap<>();
    private final Map<String, Properties> networkExportParameters = new HashMap<>();
    private final Map<Network, LoadFlowResult> loadFlowResults = new HashMap<>();
    private final Map<Network, SecurityAnalysisResult> securityAnalysisResults = new HashMap<>();
    private final Map<Network, NetworkSearchIndex> searchIndexes = new HashMap<>();
    private final Map<Network, ObservableList<ContingencyList>> contingencyLists = new HashMap<>();
    private final Map<Network, MapView> mapViews = new HashMap<>();
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

    public MainModel() {
        loadFlowParameters.setValue(new LoadFlowParameters());
        OpenLoadFlowParameters.create(loadFlowParameters.getValue());
        securityAnalysisParameters.setValue(new SecurityAnalysisParameters());
        securityAnalysisParameters.getValue().addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters());
        syncSecurityAnalysisLoadFlowParameters();
        // SecurityAnalysisParameters embeds a LoadFlowParameters, but this app has a single authoritative
        // LoadFlowParameters instance (loadFlowParameters above); keep it wired into whichever
        // SecurityAnalysisParameters is current rather than letting the two diverge (e.g. after importing a
        // security analysis JSON that carries its own, stale load flow section).
        loadFlowParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        securityAnalysisParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        sldParameters.setValue(defaultSldParameters());
        nadParameters.setValue(new NadParameters());
        update.setValue(Instant.now());
    }

    private void syncSecurityAnalysisLoadFlowParameters() {
        securityAnalysisParameters.getValue().setLoadFlowParameters(loadFlowParameters.getValue());
    }

    // Diagram appearance defaults previously hardcoded in SubstationDiagramRenderer; svgWidthAndHeightAdded
    // and diagramName are excluded here as they're forced/computed by the renderer on every render call, not
    // user-editable via the SLD parameters popup.
    private static SldParameters defaultSldParameters() {
        return new SldParameters()
                .setSvgParameters(new SvgParameters()
                        .setUseName(true)
                        .setLabelDiagonal(false)
                        .setLabelCentered(true)
                        .setActivePowerUnit("MW")
                        .setReactivePowerUnit("MVAr")
                        .setCurrentUnit("A")
                        .setPowerValuePrecision(2)
                        .setCurrentValuePrecision(1)
                        .setVoltageValuePrecision(2)
                        .setAngleValuePrecision(2)
                        .setPercentageValuePrecision(1)
                        .setBusesLegendAdded(true)
                        .setTooltipEnabled(true))
                .setLayoutParameters(new LayoutParameters()
                        .setComponentsOnBusbars(Collections.emptyList()));
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
        boolean wasSelected = network == this.network.get();
        networks.remove(network);
        navigationPast.removeIf(event -> isRelatedToNetwork(event, network));
        navigationFuture.removeIf(event -> isRelatedToNetwork(event, network));
        loadFlowResults.remove(network);
        securityAnalysisResults.remove(network);
        contingencyLists.remove(network);
        // the Map view can show a subnetwork too, whose view goes with its root network
        mapViews.keySet().removeIf(n -> n.getNetwork() == network.getNetwork());
        NetworkSearchIndex index = searchIndexes.remove(network);
        if (index != null) {
            index.close();
        }
        if (wasSelected) {
            setNetwork(null);
        }
    }

    public void removeAllNetworks() {
        List.copyOf(networks).forEach(this::removeNetwork);
    }

    private static boolean isRelatedToNetwork(NavigationEvent event, Network network) {
        return event.state() != null && network.equals(event.state().getSelectedNetwork());
    }

    public void setNetwork(Network network) {
        this.network.setValue(network);
        NetworkSearchIndex cached = network == null ? null : searchIndexes.get(network);
        searchIndexState.setValue(cached != null ? NetworkSearchIndex.State.READY : NetworkSearchIndex.State.NOT_BUILT);
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

    public ObservableList<ContingencyList> getContingencyLists(Network network) {
        return contingencyLists.computeIfAbsent(network, n -> FXCollections.observableArrayList());
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

    public ObjectProperty<SldParameters> sldParametersProperty() {
        return sldParameters;
    }

    public ObjectProperty<NadParameters> nadParametersProperty() {
        return nadParameters;
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

    public void clearNotifications() {
        notifications.clear();
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
