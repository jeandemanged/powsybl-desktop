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
import com.powsybl.powsybldesktop.logs.LogsModel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationHistory;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.notification.NotificationsModel;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MainModel {
    private final ObservableList<Network> networks = FXCollections.observableArrayList();
    // keyed by root network, like networks
    private final Map<Network, NetworkStudy> studies = new HashMap<>();
    private final ObservableList<ReportNode> modifiableReports = FXCollections.observableArrayList();
    // cached, see NavigationHistory's unmodifiable views
    private final ObservableList<ReportNode> reports = FXCollections.unmodifiableObservableList(modifiableReports);
    private final ObjectProperty<Network> network = new SimpleObjectProperty<>();
    private final ObjectProperty<Instant> update = new SimpleObjectProperty<>();
    // the single voltage level touched by the change that triggered the most recent update() firing, or null
    // if the change isn't confined to one (e.g. a load flow run) - read by MainController.refreshSearchIndexBuses()
    // to scope its search index refresh instead of re-indexing every bus in the network
    private VoltageLevel updatedVoltageLevel;
    private final BooleanProperty networkBusy = new SimpleBooleanProperty();
    private final ObjectProperty<NetworkSearchIndex.State> searchIndexState = new SimpleObjectProperty<>(NetworkSearchIndex.State.NOT_BUILT);
    private final NavigationHistory navigationHistory = new NavigationHistory();
    private final ParametersModel parametersModel = new ParametersModel();
    private final NotificationsModel notificationsModel = new NotificationsModel();
    private final LogsModel logsModel = new LogsModel();
    private final DoubleProperty diagramZoom = new SimpleDoubleProperty(1.0);
    private final BooleanProperty diagramFitToScreen = new SimpleBooleanProperty(false);
    private final IntegerProperty diagramAreaDepth = new SimpleIntegerProperty(1);

    public MainModel() {
        update.setValue(Instant.now());
    }

    public void addNetwork(Network network) {
        studies.put(network, new NetworkStudy(network));
        this.networks.add(network);
        if (this.network.get() == null) {
            setNetwork(network);
        }
    }

    public ObservableList<Network> getNetworks() {
        return networks;
    }

    /**
     * The study of {@code network}'s root network, which must be loaded.
     */
    public NetworkStudy getStudy(Network network) {
        NetworkStudy study = studies.get(network.getNetwork());
        if (study == null) {
            throw new IllegalArgumentException("Network " + network.getId() + " is not loaded");
        }
        return study;
    }

    public void removeNetwork(Network network) {
        Objects.requireNonNull(network);
        // the selection and history entries can be a subnetwork of the removed root network
        boolean wasSelected = isPartOf(this.network.get(), network);
        networks.remove(network);
        NetworkStudy study = studies.remove(network);
        if (study != null) {
            study.dispose();
        }
        navigationHistory.removeIf(event -> isRelatedToNetwork(event, network));
        if (wasSelected) {
            setNetwork(null);
        }
    }

    private static boolean isPartOf(Network network, Network rootNetwork) {
        return network != null && network.getNetwork() == rootNetwork;
    }

    private static boolean isRelatedToNetwork(NavigationEvent event, Network network) {
        return event.state() != null && isPartOf(event.state().getSelectedNetwork(), network);
    }

    // Subnetwork.detach() moves the equipment into a new network and leaves the subnetwork object empty and
    // orphaned, so nothing may keep referencing it - and the root's search indexes still hold the moved equipment.
    public void detachSubnetworks(List<Network> subnetworks) {
        navigationHistory.removeIf(event -> event.state() != null && subnetworks.contains(event.state().getSelectedNetwork()));
        getStudy(subnetworks.getFirst()).forgetSubnetworks();
        for (Network subnetwork : subnetworks) {
            Network detached = subnetwork.detach();
            studies.put(detached, new NetworkStudy(detached));
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

    public void setNetwork(Network network) {
        NetworkStudy study = network == null ? null : getStudy(network);
        // set before the network itself: MainController's network listener may then switch it to BUILDING
        boolean indexed = study != null && study.getSearchIndex(network) != null;
        searchIndexState.setValue(indexed ? NetworkSearchIndex.State.READY : NetworkSearchIndex.State.NOT_BUILT);
        networkBusy.unbind();
        if (study != null) {
            networkBusy.bind(study.busyProperty());
        } else {
            networkBusy.set(false);
        }
        this.network.setValue(network);
    }

    /**
     * Marks {@code network}'s root network as read or written by a background job, until the returned action
     * (idempotent) is run - a no-op for a network that isn't loaded. FX thread only.
     */
    public Runnable markBusy(Network network) {
        NetworkStudy study = studies.get(network.getNetwork());
        return study == null ? () -> { } : study.markBusy();
    }

    public boolean isBusy(Network network) {
        NetworkStudy study = network == null ? null : studies.get(network.getNetwork());
        return study != null && study.isBusy();
    }

    /**
     * Whether the selected network is busy (see {@link #markBusy}): the UI must not edit it meanwhile.
     */
    public ReadOnlyBooleanProperty networkBusyProperty() {
        return networkBusy;
    }

    public Network getNetwork() {
        return network.get();
    }

    public ObjectProperty<Network> networkProperty() {
        return network;
    }

    // Only updates searchIndexState if the built network is still the one currently selected - a network can be
    // deselected while its index is still building in the background.
    public void setSearchIndex(Network network, NetworkSearchIndex index) {
        getStudy(network).setSearchIndex(network, index);
        if (network.equals(this.network.get())) {
            searchIndexState.setValue(NetworkSearchIndex.State.READY);
        }
    }

    public ObjectProperty<NetworkSearchIndex.State> searchIndexStateProperty() {
        return searchIndexState;
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

    public NavigationHistory getNavigationHistory() {
        return navigationHistory;
    }

    public ParametersModel getParametersModel() {
        return parametersModel;
    }

    public NotificationsModel getNotificationsModel() {
        return notificationsModel;
    }

    public LogsModel getLogsModel() {
        return logsModel;
    }

    public void addReport(ReportNode reportNode) {
        Objects.requireNonNull(reportNode);
        modifiableReports.add(reportNode);
    }

    public ObservableList<ReportNode> getReports() {
        return reports;
    }

    public void clearReports() {
        modifiableReports.clear();
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
}
