/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.sld.layout.LayoutParameters;
import com.powsybl.sld.svg.SvgParameters;

import java.util.Map;
import java.util.Properties;

/**
 * Every parameter edited from the parameters view, as saved to / restored from a single JSON file by
 * {@link ApplicationParametersJson}: the PowSyBl ones and the app's own {@link GuiParameters}. Network import/export
 * parameters are keyed by format as in {@link NetworkFormatParametersController}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record ApplicationParameters(Map<String, Properties> networkImport,
                                    Map<String, Properties> networkExport,
                                    DesktopSldParameters sld,
                                    DesktopNadParameters nad,
                                    LoadFlowParameters loadFlow,
                                    SecurityAnalysisParameters securityAnalysis,
                                    GuiParameters gui) {

    public static ApplicationParameters createDefault() {
        return new ApplicationParameters(Map.of(), Map.of(), defaultSldParameters(), new DesktopNadParameters(),
                defaultLoadFlowParameters(), defaultSecurityAnalysisParameters(), new GuiParameters());
    }

    public static LoadFlowParameters defaultLoadFlowParameters() {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters);
        return parameters;
    }

    public static SecurityAnalysisParameters defaultSecurityAnalysisParameters() {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters());
        return parameters;
    }

    // Diagram appearance defaults previously hardcoded in SubstationDiagramRenderer; svgWidthAndHeightAdded
    // and diagramName are excluded here as they're forced/computed by the renderer on every render call, not
    // user-editable via the SLD parameters popup.
    public static DesktopSldParameters defaultSldParameters() {
        DesktopSldParameters parameters = new DesktopSldParameters();
        parameters.setSvgParameters(new SvgParameters()
                        .setUseName(true)
                        .setLabelDiagonal(false)
                        .setLabelCentered(true)
                        .setActivePowerUnit("MW")
                        .setReactivePowerUnit("MVAr")
                        .setCurrentUnit("A")
                        .setBusesLegendAdded(true)
                        .setTooltipEnabled(true))
                .setLayoutParameters(new LayoutParameters());
        return parameters;
    }
}
