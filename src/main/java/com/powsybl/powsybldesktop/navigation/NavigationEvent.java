/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record NavigationEvent(NavigationType navigationType, NavigationState state) {
    public static NavigationEvent create(NavigationType navigationType, NavigationState state) {
        return new NavigationEvent(navigationType, state);
    }

    public static NavigationEvent create(NavigationType navigationType) {
        return new NavigationEvent(navigationType, null);
    }

    public String describe() {
        String screen = switch (navigationType) {
            case NETWORKS -> Messages.get("main.toolbar.networks");
            case SUBSTATIONS -> Messages.get("main.toolbar.substations");
            case MAP -> Messages.get("main.toolbar.map");
            case CONTINGENCIES -> Messages.get("main.toolbar.contingencies");
            case LOGS -> Messages.get("main.toolbar.logs");
            case PARAMETERS -> Messages.get("main.toolbar.parameters");
            case NETWORK_TABLE_SUBSTATIONS -> Messages.get("main.toolbar.substationsTable");
            case NETWORK_TABLE_VOLTAGE_LEVELS -> Messages.get("main.toolbar.voltageLevelsTable");
            case NETWORK_TABLE_BUSBAR_SECTIONS -> Messages.get("main.toolbar.busbarSectionsTable");
            case NETWORK_TABLE_BUSES_BUS_VIEW -> Messages.get("main.toolbar.busesBusView");
            case NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW -> Messages.get("main.toolbar.busesBusBreakerView");
            case NETWORK_TABLE_GENERATORS -> Messages.get("main.toolbar.generators");
            case NETWORK_TABLE_SHUNT_COMPENSATORS -> Messages.get("main.toolbar.shuntCompensators");
            case NETWORK_TABLE_STATIC_VAR_COMPENSATORS -> Messages.get("main.toolbar.staticVarCompensators");
            case NETWORK_TABLE_LOADS -> Messages.get("main.toolbar.loads");
            case NETWORK_TABLE_LINES -> Messages.get("main.toolbar.lines");
            case NETWORK_TABLE_TRANSFORMERS -> Messages.get("main.toolbar.transformers");
            case NETWORK_TABLE_TIE_LINES -> Messages.get("main.toolbar.tieLines");
            case NETWORK_TABLE_BOUNDARY_LINES -> Messages.get("main.toolbar.boundaryLines");
            case NETWORK_TABLE_COMPONENTS -> Messages.get("main.toolbar.loadflowresult");
            case NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS -> Messages.get("main.toolbar.securityAnalysisResults");
            case REPORTS -> Messages.get("main.toolbar.reports");
        };
        String selection = null;
        if (state instanceof BusNavigationState s && s.getBus() != null) {
            selection = s.getBus().getNameOrId();
        } else if (state instanceof SubstationNavigationState s && s.getSubstation() != null) {
            selection = s.getSubstation().getNameOrId();
        } else if (state instanceof VoltageLevelNavigationState s && s.getVoltageLevel() != null) {
            selection = s.getVoltageLevel().getNameOrId();
        } else if (state instanceof BusbarSectionNavigationState s && s.getBusbarSection() != null) {
            selection = s.getBusbarSection().getNameOrId();
        } else if (state instanceof GeneratorNavigationState s && s.getGenerator() != null) {
            selection = s.getGenerator().getNameOrId();
        } else if (state instanceof ShuntCompensatorNavigationState s && s.getShuntCompensator() != null) {
            selection = s.getShuntCompensator().getNameOrId();
        } else if (state instanceof StaticVarCompensatorNavigationState s && s.getStaticVarCompensator() != null) {
            selection = s.getStaticVarCompensator().getNameOrId();
        } else if (state instanceof LoadNavigationState s && s.getLoad() != null) {
            selection = s.getLoad().getNameOrId();
        } else if (state instanceof LineNavigationState s && s.getLine() != null) {
            selection = s.getLine().getNameOrId();
        } else if (state instanceof TransformerNavigationState s && s.getTransformer() != null) {
            selection = s.getTransformer().getNameOrId();
        } else if (state instanceof TieLineNavigationState s && s.getTieLine() != null) {
            selection = s.getTieLine().getNameOrId();
        } else if (state instanceof BoundaryLineNavigationState s && s.getBoundaryLine() != null) {
            selection = s.getBoundaryLine().getNameOrId();
        } else if (state instanceof ContainerNavigationState s && s.getContainer() != null) {
            selection = s.getContainer().getNameOrId();
        } else if (state instanceof ReportNavigationState s) {
            selection = s.getReportNode().getMessage();
        } else if (state != null && state.getSelectedNetwork() != null) {
            selection = Labels.networkLabel(state.getSelectedNetwork());
        }
        return selection == null ? screen : screen + " - " + selection;
    }
}
