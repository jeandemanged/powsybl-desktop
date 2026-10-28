/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Set;

/**
 * The network objects created and removed by one edit of {@code rootNetwork}'s structure, see
 * {@link MainModel#setStructureChange}. The sets compare by identity, as navigation states do; the removed objects
 * can't be used anymore, except for their id.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record NetworkStructureChange(Network rootNetwork, Set<Identifiable<?>> created, Set<Identifiable<?>> removed) {
}
