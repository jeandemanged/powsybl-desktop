/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GeneratorNavigationState extends NetworkNavigationState {

    private final Generator generator;

    protected GeneratorNavigationState(Network selectedNetwork, Generator generator) {
        super(selectedNetwork);
        this.generator = generator;
    }

    public static GeneratorNavigationState create(Generator generator) {
        return new GeneratorNavigationState(generator.getNetwork(), generator);
    }

    public static GeneratorNavigationState createNoGenerator(Network network) {
        return new GeneratorNavigationState(network, null);
    }

    public Generator getGenerator() {
        return generator;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && generator == ((GeneratorNavigationState) o).generator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), generator);
    }
}
