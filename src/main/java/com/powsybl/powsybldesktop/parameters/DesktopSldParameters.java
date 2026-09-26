/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.sld.SldParameters;
import com.powsybl.sld.layout.PositionVoltageLevelLayoutFactory;
import com.powsybl.sld.layout.PositionVoltageLevelLayoutFactoryParameters;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.layout.position.clustering.PositionByClustering;

/**
 * {@link SldParameters}' voltage level layout factory creator is write-only (no getter), so the chosen layout
 * and its position-layout options are held here instead, to be editable and displayed back by
 * {@link SldParametersController}. The creator reads them at render time, so edits apply on the next render.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DesktopSldParameters extends SldParameters {

    public enum VoltageLevelLayout {
        SMART,
        POSITION_FROM_EXTENSIONS,
        POSITION_BY_CLUSTERING
    }

    private VoltageLevelLayout voltageLevelLayout = VoltageLevelLayout.SMART;
    private final PositionVoltageLevelLayoutFactoryParameters positionLayoutParameters = new PositionVoltageLevelLayoutFactoryParameters();

    public DesktopSldParameters() {
        super.setVoltageLevelLayoutFactoryCreator(network -> switch (voltageLevelLayout) {
            case SMART -> new SmartVoltageLevelLayoutFactory(network);
            case POSITION_FROM_EXTENSIONS -> new PositionVoltageLevelLayoutFactory(positionLayoutParameters);
            case POSITION_BY_CLUSTERING -> new PositionVoltageLevelLayoutFactory(new PositionByClustering(), positionLayoutParameters);
        });
    }

    public VoltageLevelLayout getVoltageLevelLayout() {
        return voltageLevelLayout;
    }

    public DesktopSldParameters setVoltageLevelLayout(VoltageLevelLayout voltageLevelLayout) {
        this.voltageLevelLayout = voltageLevelLayout;
        return this;
    }

    public PositionVoltageLevelLayoutFactoryParameters getPositionLayoutParameters() {
        return positionLayoutParameters;
    }
}
