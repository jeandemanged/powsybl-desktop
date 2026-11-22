/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.powsybldesktop.utils.Messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The CRAC creation parameters an import needs, by format (OpenRAO 7.5.1): a CIM CRAC, an NC profile set and a
 * flow-based constraint document hold several time stamps, the one to import is required; NC also requires its
 * capacity calculation region. The other parameters of the formats (aligned range action groups, busbar changes,
 * voltage CNECs, curative instants...) come from an OpenRAO CRAC creation parameters JSON file, if any.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracImportParameters {

    public static final String JSON = "JSON";
    public static final String CSE = "CseCrac";
    public static final String CIM = "CimCrac";
    public static final String NC = "NC";
    public static final String FLOW_BASED_CONSTRAINT = "FlowBasedConstraintDocument";

    private CracImportParameters() {
    }

    public static boolean needsTimestamp(String format) {
        return CIM.equals(format) || NC.equals(format) || FLOW_BASED_CONSTRAINT.equals(format);
    }

    public static boolean needsCapacityCalculationRegion(String format) {
        return NC.equals(format);
    }

    public static boolean hasIcsCosts(String format) {
        return FLOW_BASED_CONSTRAINT.equals(format);
    }

    /**
     * A copy of {@code parameters}, through their JSON, so that a dialog can edit it. The time stamps are copied as they
     * are: OpenRAO 7.5.1's JSON serializers write them with a literal 'Z' without converting them to UTC, which would
     * shift those of another offset.
     */
    public static CracCreationParameters copy(CracCreationParameters parameters) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        JsonCracCreationParameters.write(parameters, json);
        CracCreationParameters copy = JsonCracCreationParameters.read(new ByteArrayInputStream(json.toByteArray()));
        for (String format : List.of(CIM, NC, FLOW_BASED_CONSTRAINT)) {
            OffsetDateTime timestamp = timestampIfAny(parameters, format);
            if (timestamp != null) {
                setTimestamp(copy, format, timestamp);
            }
        }
        return copy;
    }

    // without adding the extension
    private static OffsetDateTime timestampIfAny(CracCreationParameters parameters, String format) {
        return switch (format) {
            case CIM -> parameters.getExtension(CimCracCreationParameters.class) == null ? null : parameters.getExtension(CimCracCreationParameters.class).getTimestamp();
            case NC -> parameters.getExtension(NcCracCreationParameters.class) == null ? null : parameters.getExtension(NcCracCreationParameters.class).getTimestamp();
            case FLOW_BASED_CONSTRAINT -> parameters.getExtension(FbConstraintCracCreationParameters.class) == null ? null
                    : parameters.getExtension(FbConstraintCracCreationParameters.class).getTimestamp();
            default -> null;
        };
    }

    public static OffsetDateTime timestamp(CracCreationParameters parameters, String format) {
        return switch (format) {
            case CIM -> extension(parameters, CimCracCreationParameters.class, CimCracCreationParameters::new).getTimestamp();
            case NC -> extension(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new).getTimestamp();
            case FLOW_BASED_CONSTRAINT -> extension(parameters, FbConstraintCracCreationParameters.class, FbConstraintCracCreationParameters::new).getTimestamp();
            default -> null;
        };
    }

    public static void setTimestamp(CracCreationParameters parameters, String format, OffsetDateTime timestamp) {
        switch (format) {
            case CIM -> extension(parameters, CimCracCreationParameters.class, CimCracCreationParameters::new).setTimestamp(timestamp);
            case NC -> extension(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new).setTimestamp(timestamp);
            case FLOW_BASED_CONSTRAINT -> extension(parameters, FbConstraintCracCreationParameters.class, FbConstraintCracCreationParameters::new).setTimestamp(timestamp);
            default -> throw new IllegalArgumentException("No time stamp for " + format);
        }
    }

    public static CapacityCalculationRegion capacityCalculationRegion(CracCreationParameters parameters) {
        return extension(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new).getCapacityCalculationRegion();
    }

    public static void setCapacityCalculationRegion(CracCreationParameters parameters, CapacityCalculationRegion region) {
        extension(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new).setCapacityCalculationRegion(region);
    }

    public static FbConstraintCracCreationParameters flowBasedConstraint(CracCreationParameters parameters) {
        return extension(parameters, FbConstraintCracCreationParameters.class, FbConstraintCracCreationParameters::new);
    }

    /**
     * Why {@code parameters} can't import a CRAC of {@code format}: the values it requires and lacks, empty when it can.
     */
    public static List<String> missing(CracCreationParameters parameters, String format) {
        List<String> missing = new ArrayList<>();
        if (needsTimestamp(format) && timestamp(parameters, format) == null) {
            missing.add(Messages.get("crac.import.missingTimestamp"));
        }
        if (needsCapacityCalculationRegion(format) && capacityCalculationRegion(parameters) == null) {
            missing.add(Messages.get("crac.import.missingRegion"));
        }
        return missing;
    }

    private static <E extends Extension<CracCreationParameters>> E extension(CracCreationParameters parameters, Class<E> type,
                                                                             Supplier<E> creator) {
        E extension = parameters.getExtension(type);
        if (extension == null) {
            extension = creator.get();
            parameters.addExtension(type, extension);
        }
        return extension;
    }
}
