/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * A part of an equipment detail view, which {@link AbstractEquipmentDetailController} re-reads after every network
 * update and makes read-only while the network is busy.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public interface DetailPart {

    void refresh();

    BooleanProperty editableProperty();

    /**
     * A read-only part, e.g. a chart.
     */
    static DetailPart of(Runnable refresh) {
        BooleanProperty editable = new SimpleBooleanProperty();
        return new DetailPart() {
            @Override
            public void refresh() {
                refresh.run();
            }

            @Override
            public BooleanProperty editableProperty() {
                return editable;
            }
        };
    }
}
