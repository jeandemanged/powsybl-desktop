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
            case SUBSTATIONS -> Messages.get("desktop.common.equipment.substation.plural");
            case MAP -> Messages.get("main.toolbar.map");
            case CONTINGENCIES -> Messages.get("main.toolbar.contingencies");
            case LOGS -> Messages.get("main.toolbar.logs");
            case NETWORK_TABLE_SUBSTATIONS -> Messages.get("desktop.common.equipment.substation.plural");
            case NETWORK_TABLE_VOLTAGE_LEVELS -> Messages.get("desktop.common.equipment.voltageLevel.plural");
            case NETWORK_TABLE_BUSBAR_SECTIONS -> Messages.get("desktop.common.equipment.busbarSection.plural");
            case NETWORK_TABLE_BUSES_BUS_VIEW -> Messages.get("desktop.common.equipment.busesBusBranchView");
            case NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW -> Messages.get("desktop.common.equipment.busesBusBreakerView");
            case NETWORK_TABLE_GENERATORS -> Messages.get("desktop.common.equipment.generator.plural");
            case NETWORK_TABLE_BATTERIES -> Messages.get("desktop.common.equipment.battery.plural");
            case NETWORK_TABLE_SHUNT_COMPENSATORS -> Messages.get("desktop.common.equipment.shuntCompensator.plural");
            case NETWORK_TABLE_STATIC_VAR_COMPENSATORS -> Messages.get("desktop.common.equipment.staticVarCompensator.plural");
            case NETWORK_TABLE_VSC_CONVERTER_STATIONS -> Messages.get("desktop.common.equipment.vscConverterStation.plural");
            case NETWORK_TABLE_LCC_CONVERTER_STATIONS -> Messages.get("desktop.common.equipment.lccConverterStation.plural");
            case NETWORK_TABLE_LOADS -> Messages.get("desktop.common.equipment.load.plural");
            case NETWORK_TABLE_LINES -> Messages.get("desktop.common.equipment.line.plural");
            case NETWORK_TABLE_TRANSFORMERS -> Messages.get("desktop.common.equipment.transformer.plural");
            case NETWORK_TABLE_TIE_LINES -> Messages.get("desktop.common.equipment.tieLine.plural");
            case NETWORK_TABLE_BOUNDARY_LINES -> Messages.get("desktop.common.equipment.boundaryLine.plural");
            case NETWORK_TABLE_HVDC_LINES -> Messages.get("desktop.common.equipment.hvdcLine.plural");
            case NETWORK_TABLE_GROUNDS -> Messages.get("desktop.common.equipment.ground.plural");
            case NETWORK_TABLE_AREAS -> Messages.get("desktop.common.equipment.area.plural");
            case NETWORK_TABLE_OVERLOAD_MANAGEMENT_SYSTEMS -> Messages.get("desktop.common.equipment.overloadManagementSystem.plural");
            case NETWORK_TABLE_VOLTAGE_ANGLE_LIMITS -> Messages.get("desktop.common.equipment.voltageAngleLimit.plural");
            case NETWORK_TABLE_COMPONENTS -> Messages.get("main.toolbar.loadflowresult");
            case NETWORK_TABLE_SECURITY_ANALYSIS_RESULTS -> Messages.get("main.toolbar.securityAnalysisResults");
            case NETWORK_TABLE_VIOLATIONS -> Messages.get("main.toolbar.violations");
            case REPORTS -> Messages.get("main.toolbar.reports");
            case PARAMETERS -> Messages.get("desktop.common.parameters");
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
        } else if (state instanceof BatteryNavigationState s && s.getBattery() != null) {
            selection = s.getBattery().getNameOrId();
        } else if (state instanceof ShuntCompensatorNavigationState s && s.getShuntCompensator() != null) {
            selection = s.getShuntCompensator().getNameOrId();
        } else if (state instanceof StaticVarCompensatorNavigationState s && s.getStaticVarCompensator() != null) {
            selection = s.getStaticVarCompensator().getNameOrId();
        } else if (state instanceof VscConverterStationNavigationState s && s.getVscConverterStation() != null) {
            selection = s.getVscConverterStation().getNameOrId();
        } else if (state instanceof LccConverterStationNavigationState s && s.getLccConverterStation() != null) {
            selection = s.getLccConverterStation().getNameOrId();
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
        } else if (state instanceof HvdcLineNavigationState s && s.getHvdcLine() != null) {
            selection = s.getHvdcLine().getNameOrId();
        } else if (state instanceof GroundNavigationState s && s.getGround() != null) {
            selection = s.getGround().getNameOrId();
        } else if (state instanceof AreaNavigationState s && s.getArea() != null) {
            selection = s.getArea().getNameOrId();
        } else if (state instanceof OverloadManagementSystemNavigationState s && s.getOverloadManagementSystem() != null) {
            selection = s.getOverloadManagementSystem().getNameOrId();
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
