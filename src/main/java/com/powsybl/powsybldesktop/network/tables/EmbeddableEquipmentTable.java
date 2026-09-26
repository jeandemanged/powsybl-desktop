/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Container;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.DisposableController;

/**
 * What {@link com.powsybl.powsybldesktop.network.SubstationsController} needs from an equipment table
 * controller (Generators, Loads, Lines, ...) to embed it as one of its tabs, filtered to the
 * substation/voltage level currently selected in its tree. Implemented by
 * {@link AbstractEquipmentTableController}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public interface EmbeddableEquipmentTable extends DisposableController {

    void setMainModel(MainModel mainModel);

    /**
     * Filters the table to the equipment belonging to {@code container}, or to nothing if {@code null}
     * (nothing selected in the substations tree). Once called, the table stays filtered - unlike a
     * standalone table (its own toolbar view), which always lists the whole network.
     */
    void setContainer(Container<?> container);

    /**
     * Whether the table currently has at least one row, i.e. whether its tab should be shown.
     */
    boolean hasRows();
}
