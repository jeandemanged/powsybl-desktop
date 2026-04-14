/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * Wraps either a {@code TwoWindingsTransformer} or a {@code ThreeWindingsTransformer}, the Transformers table
 * having a single row type for both.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TransformerNavigationState extends NetworkNavigationState {

    private final Identifiable<?> transformer;

    protected TransformerNavigationState(Network selectedNetwork, Identifiable<?> transformer) {
        super(selectedNetwork);
        this.transformer = transformer;
    }

    public static TransformerNavigationState create(Identifiable<?> transformer) {
        return new TransformerNavigationState(transformer.getNetwork(), transformer);
    }

    public static TransformerNavigationState createNoTransformer(Network network) {
        return new TransformerNavigationState(network, null);
    }

    public Identifiable<?> getTransformer() {
        return transformer;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && transformer == ((TransformerNavigationState) o).transformer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), transformer);
    }
}
