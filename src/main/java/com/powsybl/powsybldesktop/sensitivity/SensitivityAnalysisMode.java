/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.loadflow.LoadFlowParameters;

/**
 * Whether a sensitivity analysis is AC or DC. The app has a single load flow parameters instance, shared with the
 * sensitivity analysis: rather than switching the load flow itself to DC for a quick DC sensitivity analysis, this
 * overrides its {@link LoadFlowParameters#isDc()} on the run's copy only.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum SensitivityAnalysisMode {
    LOAD_FLOW,
    AC,
    DC;

    public boolean isDc(LoadFlowParameters loadFlowParameters) {
        return this == DC || this == LOAD_FLOW && loadFlowParameters.isDc();
    }
}
