/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LineNavigationState extends NetworkNavigationState {

    private final Line line;

    protected LineNavigationState(Network selectedNetwork, Line line) {
        super(selectedNetwork);
        this.line = line;
    }

    public static LineNavigationState create(Line line) {
        return new LineNavigationState(line.getNetwork(), line);
    }

    public static LineNavigationState createNoLine(Network network) {
        return new LineNavigationState(network, null);
    }

    public Line getLine() {
        return line;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && line == ((LineNavigationState) o).line;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), line);
    }
}
