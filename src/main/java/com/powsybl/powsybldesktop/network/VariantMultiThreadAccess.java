/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManager;

/**
 * Multi-thread access to a network's variants, for the duration of an Open Load Flow computation: {@link #allow} on the
 * FX thread, {@link #setWorkingVariant} on the computing thread, then {@link #restore} on the FX thread once the
 * computation is over.
 * <p>
 * Open Load Flow's multi-threaded security and sensitivity analyses switch the network to thread-local variants while
 * running, so that any other thread reading it meanwhile fails ("Variant index not set for current thread"), e.g. the
 * FX thread opening a table. So the switch is made beforehand, giving the FX thread (and the computing one) its working
 * variant, and undone afterwards: Open Load Flow then finds it on and leaves it on. The FX thread may still read the
 * network while Open Load Flow's threads build their own networks from it, racing on IIDM's lazily computed caches
 * (e.g. the bus view): running on a copy of the network would isolate it fully, at the cost of a copy per run.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class VariantMultiThreadAccess {

    private final Network network;
    private final String variantId;
    private final boolean allowedBefore;

    private VariantMultiThreadAccess(Network network) {
        this.network = network;
        VariantManager variantManager = network.getVariantManager();
        variantId = variantManager.getWorkingVariantId();
        allowedBefore = variantManager.isVariantMultiThreadAccessAllowed();
    }

    public static VariantMultiThreadAccess allow(Network network) {
        VariantMultiThreadAccess access = new VariantMultiThreadAccess(network);
        network.getVariantManager().allowVariantMultiThreadAccess(true);
        network.getVariantManager().setWorkingVariant(access.variantId);
        return access;
    }

    /**
     * Sets the working variant of the calling thread, the one {@link #allow} found.
     */
    public void setWorkingVariant() {
        network.getVariantManager().setWorkingVariant(variantId);
    }

    public void restore() {
        network.getVariantManager().allowVariantMultiThreadAccess(allowedBefore);
    }
}
