/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.sld.SldParameters;
import com.powsybl.sld.layout.HorizontalSubstationLayoutFactory;
import com.powsybl.sld.layout.PositionVoltageLevelLayoutFactory;
import com.powsybl.sld.layout.PositionVoltageLevelLayoutFactoryParameters;
import com.powsybl.sld.layout.SmartVoltageLevelLayoutFactory;
import com.powsybl.sld.layout.VerticalSubstationLayoutFactory;
import com.powsybl.sld.layout.position.clustering.PositionByClustering;
import com.powsybl.sld.library.ConvergenceComponentLibrary;
import com.powsybl.sld.library.FlatDesignLibrary;
import com.powsybl.sld.svg.styles.BusHighlightStyleProviderFactory;
import com.powsybl.sld.svg.styles.DefaultStyleProviderFactory;
import com.powsybl.sld.svg.styles.NominalVoltageStyleProviderFactory;

/**
 * {@link SldParameters}' voltage level layout factory creator is write-only (no getter), so the chosen layout
 * and its position-layout options are held here instead, to be editable and displayed back by
 * {@link SldParametersController}. The creator reads them at render time, so edits apply on the next render.
 * The substation layout, component library and style provider factories are opaque objects too: they're exposed
 * here as closed choices, recognized back by type, for the form and the saved parameters file to share.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DesktopSldParameters extends SldParameters {

    public enum VoltageLevelLayout {
        SMART,
        POSITION_FROM_EXTENSIONS,
        POSITION_BY_CLUSTERING
    }

    public enum SubstationLayoutChoice {
        HORIZONTAL,
        VERTICAL
    }

    public enum ComponentLibraryChoice {
        CONVERGENCE,
        FLAT_DESIGN
    }

    public enum StyleChoice {
        TOPOLOGICAL,
        BUS_HIGHLIGHT,
        NOMINAL_VOLTAGE
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

    public SubstationLayoutChoice getSubstationLayoutChoice() {
        return getSubstationLayoutFactory() instanceof VerticalSubstationLayoutFactory ? SubstationLayoutChoice.VERTICAL : SubstationLayoutChoice.HORIZONTAL;
    }

    public DesktopSldParameters setSubstationLayoutChoice(SubstationLayoutChoice choice) {
        setSubstationLayoutFactory(choice == SubstationLayoutChoice.VERTICAL ? new VerticalSubstationLayoutFactory() : new HorizontalSubstationLayoutFactory());
        return this;
    }

    public ComponentLibraryChoice getComponentLibraryChoice() {
        return getComponentLibrary() instanceof FlatDesignLibrary ? ComponentLibraryChoice.FLAT_DESIGN : ComponentLibraryChoice.CONVERGENCE;
    }

    public DesktopSldParameters setComponentLibraryChoice(ComponentLibraryChoice choice) {
        setComponentLibrary(choice == ComponentLibraryChoice.FLAT_DESIGN ? new FlatDesignLibrary() : new ConvergenceComponentLibrary());
        return this;
    }

    public StyleChoice getStyleChoice() {
        if (getStyleProviderFactory() instanceof BusHighlightStyleProviderFactory) {
            return StyleChoice.BUS_HIGHLIGHT;
        }
        if (getStyleProviderFactory() instanceof NominalVoltageStyleProviderFactory) {
            return StyleChoice.NOMINAL_VOLTAGE;
        }
        return StyleChoice.TOPOLOGICAL;
    }

    public DesktopSldParameters setStyleChoice(StyleChoice choice) {
        setStyleProviderFactory(switch (choice) {
            case TOPOLOGICAL -> new DefaultStyleProviderFactory();
            case BUS_HIGHLIGHT -> new BusHighlightStyleProviderFactory();
            case NOMINAL_VOLTAGE -> new NominalVoltageStyleProviderFactory();
        });
        return this;
    }
}
