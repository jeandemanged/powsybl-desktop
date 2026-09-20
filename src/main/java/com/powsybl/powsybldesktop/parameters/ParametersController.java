/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.loadflow.parameters.LoadFlowParametersController;
import com.powsybl.powsybldesktop.security.parameters.SecurityAnalysisParametersController;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import javafx.fxml.FXML;

/**
 * Tab host for the app's parameter screens: load flow parameters (existing {@link LoadFlowParametersController},
 * embedded unchanged) and security analysis parameters ({@link SecurityAnalysisParametersController}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ParametersController extends AbstractDisposableController {

    @FXML
    private LoadFlowParametersController loadFlowEmbeddedController;
    @FXML
    private SecurityAnalysisParametersController securityAnalysisEmbeddedController;

    public void setMainModel(MainModel mainModel) {
        loadFlowEmbeddedController.setLoadFlowParametersProperty(mainModel.loadFlowParametersProperty());
        securityAnalysisEmbeddedController.setSecurityAnalysisParametersProperty(mainModel.securityAnalysisParametersProperty());
    }

    @Override
    public void dispose() {
        loadFlowEmbeddedController.dispose();
        securityAnalysisEmbeddedController.dispose();
        super.dispose();
    }
}
