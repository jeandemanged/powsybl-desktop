/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import javafx.fxml.FXML;

/**
 * Tab host for the app's parameter screens: network import/export parameters per format
 * ({@link NetworkFormatParametersController}), single line and network area diagram parameters
 * ({@link SldParametersController}, {@link NadParametersController}), load flow parameters (existing
 * {@link LoadFlowParametersController}, embedded unchanged) and security analysis parameters
 * ({@link SecurityAnalysisParametersController}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ParametersController extends AbstractDisposableController {

    @FXML
    private NetworkFormatParametersController networkImportEmbeddedController;
    @FXML
    private NetworkFormatParametersController networkExportEmbeddedController;
    @FXML
    private SldParametersController sldEmbeddedController;
    @FXML
    private NadParametersController nadEmbeddedController;
    @FXML
    private LoadFlowParametersController loadFlowEmbeddedController;
    @FXML
    private SecurityAnalysisParametersController securityAnalysisEmbeddedController;

    public void setMainModel(MainModel mainModel) {
        networkImportEmbeddedController.setImportParameters(mainModel);
        networkExportEmbeddedController.setExportParameters(mainModel);
        sldEmbeddedController.setParametersProperty(mainModel.sldParametersProperty());
        sldEmbeddedController.setOnChange(mainModel::sldParametersChanged);
        nadEmbeddedController.setParametersProperty(mainModel.nadParametersProperty());
        nadEmbeddedController.setOnChange(mainModel::nadParametersChanged);
        loadFlowEmbeddedController.setLoadFlowParametersProperty(mainModel.loadFlowParametersProperty());
        securityAnalysisEmbeddedController.setSecurityAnalysisParametersProperty(mainModel.securityAnalysisParametersProperty());
    }

    @Override
    public void dispose() {
        networkImportEmbeddedController.dispose();
        networkExportEmbeddedController.dispose();
        sldEmbeddedController.dispose();
        nadEmbeddedController.dispose();
        loadFlowEmbeddedController.dispose();
        securityAnalysisEmbeddedController.dispose();
        super.dispose();
    }
}
