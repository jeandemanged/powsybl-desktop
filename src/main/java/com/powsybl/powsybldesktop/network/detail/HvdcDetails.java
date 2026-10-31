/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.parameters.GuiParameters;

/**
 * What VSC and LCC converter stations' detail views share.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class HvdcDetails {

    private HvdcDetails() {
    }

    /**
     * Loss factor, and links to the HVDC line and the other station.
     */
    static void converterStation(DetailForm form, HvdcConverterStation<?> station, SceneModel sceneModel) {
        form.doubleField("detail.hvdc.lossFactor", DetailForm.PERCENT, GuiParameters.Quantity.PERCENTAGE,
                        station::getLossFactor, lossFactor -> station.setLossFactor((float) lossFactor))
                .text("detail.hvdc.type", () -> station.getHvdcType().name());
        DetailSections.equipmentLink(form, "desktop.common.equipment.hvdcLine", sceneModel, station::getHvdcLine);
        DetailSections.equipmentLink(form, "detail.hvdc.otherStation", sceneModel, () -> station.getOtherConverterStation().orElse(null));
    }

    /**
     * The station's active power target in generator convention, from its HVDC line's setpoint, losses aside: the
     * rectifier draws it, the inverter injects it.
     */
    static double targetP(HvdcConverterStation<?> station) {
        HvdcLine line = station.getHvdcLine();
        if (line == null) {
            return Double.NaN;
        }
        boolean side1 = line.getConverterStation(TwoSides.ONE) == station;
        boolean side1Rectifier = line.getConvertersMode() == HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER;
        return side1 == side1Rectifier ? -line.getActivePowerSetpoint() : line.getActivePowerSetpoint();
    }
}
