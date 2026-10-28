/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.modification.NetworkModification;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkListener;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.NetworkStructureChange;
import com.powsybl.powsybldesktop.notification.Notification;

import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The single way the UI creates or removes network objects, so that every view showing the network's structure
 * learns what changed (see {@link MainModel#setStructureChange}). Edits run on the FX thread, like the value edits
 * of tables and detail views: creating or removing a few objects is cheap.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkEdits {

    private NetworkEdits() {
    }

    /**
     * Runs {@code change} on {@code network}, then publishes the objects it created and removed - also when it
     * throws, having possibly changed the network already, the exception then propagating. Refused, with an error
     * notification, while the network is busy.
     *
     * @return false if refused
     */
    public static boolean apply(MainModel mainModel, Network network, Consumer<Network> change) {
        if (mainModel.isBusy(network)) {
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "main.networkBusy"));
            return false;
        }
        Network rootNetwork = network.getNetwork();
        // modifications create and remove more than they are given: switches of a bay, contents of a voltage
        // level, HVDC lines of its converter stations... only the network knows it all
        Set<Identifiable<?>> created = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Identifiable<?>> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        NetworkListener listener = new NetworkListener() {
            @Override
            public void onCreation(Identifiable<?> identifiable) {
                created.add(identifiable);
            }

            @Override
            public void beforeRemoval(Identifiable<?> identifiable) {
                if (!created.remove(identifiable)) {
                    removed.add(identifiable);
                }
            }
        };
        rootNetwork.addListener(listener);
        try {
            change.accept(network);
        } finally {
            rootNetwork.removeListener(listener);
            if (!created.isEmpty() || !removed.isEmpty()) {
                mainModel.setStructureChange(new NetworkStructureChange(rootNetwork,
                        Collections.unmodifiableSet(created), Collections.unmodifiableSet(removed)));
            } else {
                // e.g. an internal connection, a moved terminal: no network object created nor removed
                mainModel.setUpdate();
            }
        }
        return true;
    }

    /**
     * The first of {@code <prefix>1}, {@code <prefix>2}... not taken by any of {@code network}'s objects.
     */
    public static String uniqueId(Network network, String prefix) {
        int n = 1;
        while (network.getNetwork().getIdentifiable(prefix + n) != null) {
            n++;
        }
        return prefix + n;
    }

    /**
     * Applies {@code modification}, throwing on any error instead of logging it as modifications do by default.
     */
    public static void apply(Network network, NetworkModification modification) {
        modification.apply(network, true, ReportNode.NO_OP);
    }
}
