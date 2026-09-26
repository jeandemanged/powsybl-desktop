/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.diagram.util.layout.algorithms.parameters.Atlas2Parameters;
import com.powsybl.diagram.util.layout.postprocessing.OverlapPreventionPostProcessing;
import com.powsybl.diagram.util.layout.postprocessing.parameters.OverlapPreventionPostProcessingParameters;
import com.powsybl.diagram.util.layout.setup.SquareRandomSetup;
import com.powsybl.nad.NadParameters;
import com.powsybl.nad.layout.Atlas2ForceLayout;
import com.powsybl.nad.layout.BasicForceLayout;

/**
 * {@link NadParameters}' layout factory is opaque (a {@code LayoutFactory} exposing nothing), so the chosen
 * algorithm and the Atlas2 tuning are held here instead, to be editable and displayed back by
 * {@link NadParametersController} - and kept when switching algorithm back and forth. The factory reads them
 * at render time, so edits apply on the next render.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DesktopNadParameters extends NadParameters {

    public enum LayoutAlgorithm {
        ATLAS2_FORCE,
        BASIC_FORCE,
        GEOGRAPHICAL
    }

    private LayoutAlgorithm layoutAlgorithm = LayoutAlgorithm.ATLAS2_FORCE;
    private Atlas2Parameters atlas2Parameters = new Atlas2Parameters.Builder().build();
    private OverlapPreventionPostProcessingParameters overlapPreventionParameters = new OverlapPreventionPostProcessingParameters.Builder().build();

    public DesktopNadParameters() {
        super.setLayoutFactory(() -> switch (layoutAlgorithm) {
            case ATLAS2_FORCE -> new Atlas2ForceLayout(new SquareRandomSetup<>(), atlas2Parameters,
                    new OverlapPreventionPostProcessing<>(overlapPreventionParameters));
            // BasicForceLayout's parameters constructor is package-private: only LayoutParameters' maxSteps/timeoutSeconds apply
            case BASIC_FORCE -> new BasicForceLayout();
            case GEOGRAPHICAL -> throw new IllegalStateException(
                    "Geographical layout needs the network, see NetworkAreaDiagramRenderer");
        });
    }

    public LayoutAlgorithm getLayoutAlgorithm() {
        return layoutAlgorithm;
    }

    public DesktopNadParameters setLayoutAlgorithm(LayoutAlgorithm layoutAlgorithm) {
        this.layoutAlgorithm = layoutAlgorithm;
        return this;
    }

    public Atlas2Parameters getAtlas2Parameters() {
        return atlas2Parameters;
    }

    public DesktopNadParameters setAtlas2Parameters(Atlas2Parameters atlas2Parameters) {
        this.atlas2Parameters = atlas2Parameters;
        return this;
    }

    public OverlapPreventionPostProcessingParameters getOverlapPreventionParameters() {
        return overlapPreventionParameters;
    }

    public DesktopNadParameters setOverlapPreventionParameters(OverlapPreventionPostProcessingParameters overlapPreventionParameters) {
        this.overlapPreventionParameters = overlapPreventionParameters;
        return this;
    }
}
