/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.security.SecurityAnalysisResult;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Worker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the app holds about one loaded root network and its subnetworks: computation results, contingency
 * lists, running background jobs, search indexes and Map views. Created and disposed of by {@link MainModel}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworkStudy {
    private final Network network;
    // results are kept per root network only: load flow and security analysis always run on the root, never a
    // subnetwork (see MainController.onLoadFlow)
    private LoadFlowResult loadFlowResult;
    private SecurityAnalysisResult securityAnalysisResult;
    private final ObservableList<ContingencyList> contingencyLists = FXCollections.observableArrayList();
    // Whether a sublist is included when contingencies are resolved for a security analysis run, keyed by
    // identity since editing a sublist's form replaces it with a brand-new instance (see ContingenciesController's
    // showForm/onReplace) rather than mutating it in place - transferContingencyListEnabled carries the flag
    // over to the replacement so an edit doesn't silently re-enable a sublist the user disabled.
    private final Map<ContingencyList, BooleanProperty> contingencyListEnabled = new IdentityHashMap<>();
    // Running background operations, kept here rather than in MainController so that a language reload, which
    // replaces MainController, doesn't forget them and allow a second concurrent run on the same network
    private final Set<Service<?>> computationServices = new HashSet<>();
    // keyed by the root network or one of its subnetworks, like the Map views
    private final Map<Network, NetworkSearchIndex> searchIndexes = new HashMap<>();
    private final Map<Network, Service<?>> searchIndexServices = new HashMap<>();
    private final Map<Network, MapView> mapViews = new HashMap<>();
    // Background jobs (computations, search index/map builds, diagram renders) currently reading or writing the
    // network: IIDM isn't thread-safe, so the UI doesn't edit it while any of them runs
    private int busyCount;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();

    /**
     * Center and zoom of the Map view, as last panned or zoomed by the user.
     */
    public record MapView(double latitude, double longitude, double zoom) {
    }

    NetworkStudy(Network network) {
        this.network = Objects.requireNonNull(network);
    }

    public Network getNetwork() {
        return network;
    }

    public LoadFlowResult getLoadFlowResult() {
        return loadFlowResult;
    }

    public void setLoadFlowResult(LoadFlowResult loadFlowResult) {
        this.loadFlowResult = Objects.requireNonNull(loadFlowResult);
    }

    public SecurityAnalysisResult getSecurityAnalysisResult() {
        return securityAnalysisResult;
    }

    public void setSecurityAnalysisResult(SecurityAnalysisResult securityAnalysisResult) {
        this.securityAnalysisResult = Objects.requireNonNull(securityAnalysisResult);
    }

    public ObservableList<ContingencyList> getContingencyLists() {
        return contingencyLists;
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

    /**
     * Tracks a load flow or security analysis run until it ends, for {@link #dispose} to cancel it.
     */
    public void trackComputation(Service<?> service) {
        computationServices.add(service);
        onDone(service, () -> computationServices.remove(service));
    }

    public NetworkSearchIndex getSearchIndex(Network network) {
        return searchIndexes.get(network);
    }

    void setSearchIndex(Network network, NetworkSearchIndex index) {
        searchIndexes.put(Objects.requireNonNull(network), Objects.requireNonNull(index));
    }

    public boolean isSearchIndexBuilding(Network network) {
        return searchIndexServices.containsKey(network);
    }

    public void trackSearchIndexBuild(Network network, Service<?> service) {
        searchIndexServices.put(network, service);
        onDone(service, () -> searchIndexServices.remove(network));
    }

    private static void onDone(Service<?> service, Runnable action) {
        service.stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                action.run();
            }
        });
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

    /**
     * Marks the network as read or written by a background job, until the returned action (idempotent) is run.
     * FX thread only.
     */
    Runnable markBusy() {
        busyCount++;
        busy.set(true);
        boolean[] released = {false};
        return () -> {
            if (!released[0]) {
                released[0] = true;
                busyCount--;
                busy.set(busyCount > 0);
            }
        };
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public boolean isBusy() {
        return busy.get();
    }

    /**
     * Drops what refers to subnetworks about to be detached: the search indexes (the root's ones still hold the
     * moved equipment) and Map views. Contingency lists stay with the root network they were edited for.
     */
    void forgetSubnetworks() {
        cancel(List.copyOf(searchIndexServices.values()));
        closeSearchIndexes();
        mapViews.clear();
    }

    // cancelled first, so that a run finishing afterwards can't store a result for the removed network
    void dispose() {
        cancel(List.copyOf(computationServices));
        cancel(List.copyOf(searchIndexServices.values()));
        closeSearchIndexes();
    }

    // copied first by the callers: cancelling runs the services' onCancelled handlers, which untrack them
    private static void cancel(List<Service<?>> services) {
        services.forEach(Service::cancel);
    }

    private void closeSearchIndexes() {
        searchIndexes.values().forEach(NetworkSearchIndex::close);
        searchIndexes.clear();
    }
}
