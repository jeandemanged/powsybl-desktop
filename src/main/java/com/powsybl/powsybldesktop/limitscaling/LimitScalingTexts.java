/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.criteria.AbstractNetworkElementEquipmentCriterion;
import com.powsybl.iidm.criteria.AtLeastOneCountryCriterion;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.Criterion;
import com.powsybl.iidm.criteria.NetworkElementCriterion;
import com.powsybl.iidm.criteria.NetworkElementIdListCriterion;
import com.powsybl.iidm.criteria.SingleCountryCriterion;
import com.powsybl.iidm.criteria.SingleNominalVoltageCriterion;
import com.powsybl.iidm.criteria.ThreeNominalVoltageCriterion;
import com.powsybl.iidm.criteria.TwoCountriesCriterion;
import com.powsybl.iidm.criteria.TwoNominalVoltageCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.AllTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.LimitDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How limit scalings read in the views.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LimitScalingTexts {

    private LimitScalingTexts() {
    }

    public static String limitType(LimitType type) {
        return Messages.get("desktop.common.limitViolationType." + type.name());
    }

    public static String context(ContingencyContext context) {
        return switch (context.getContextType()) {
            case ALL -> Messages.get("limitScalings.context.all");
            case NONE -> Messages.get("limitScalings.context.none");
            case ONLY_CONTINGENCIES -> Messages.get("limitScalings.context.onlyContingencies");
            case SPECIFIC -> Messages.get("limitScalings.context.specific.value", context.getContingencyId());
        };
    }

    public static String criteria(List<NetworkElementCriterion> criteria) {
        return criteria.isEmpty() ? Messages.get("limitScalings.criteria.all")
                : criteria.stream().map(LimitScalingTexts::criterion).collect(Collectors.joining(" | "));
    }

    public static String criterion(NetworkElementCriterion criterion) {
        String kind = CriterionKind.of(criterion).label();
        List<String> parts = new ArrayList<>();
        if (criterion instanceof NetworkElementIdListCriterion idList) {
            parts.add(idList.getNetworkElementIds().stream().sorted().collect(Collectors.joining(", ")));
        } else if (criterion instanceof AbstractNetworkElementEquipmentCriterion equipment) {
            countries(equipment.getCountryCriterion()).ifPresent(parts::add);
            voltages(equipment.getNominalVoltageCriterion()).ifPresent(parts::add);
        }
        return parts.isEmpty() ? kind : kind + " (" + String.join(", ", parts) + ")";
    }

    private static Optional<String> countries(Criterion criterion) {
        return switch (criterion) {
            case SingleCountryCriterion c -> countryList(c.getCountries());
            case AtLeastOneCountryCriterion c -> countryList(c.getCountries());
            case TwoCountriesCriterion c -> {
                Optional<String> side1 = countryList(c.getCountries1());
                Optional<String> side2 = countryList(c.getCountries2());
                yield side1.isEmpty() && side2.isEmpty() ? Optional.empty()
                        : Optional.of(side1.orElse("*") + " - " + side2.orElse("*"));
            }
            case null, default -> Optional.empty();
        };
    }

    private static Optional<String> countryList(List<Country> countries) {
        return countries == null || countries.isEmpty() ? Optional.empty()
                : Optional.of(countries.stream().map(Country::name).collect(Collectors.joining("/")));
    }

    private static Optional<String> voltages(Criterion criterion) {
        List<VoltageInterval> intervals = switch (criterion) {
            case SingleNominalVoltageCriterion c -> Stream.of(c.getVoltageInterval()).toList();
            case AtLeastOneNominalVoltageCriterion c -> Stream.of(c.getVoltageInterval()).toList();
            case TwoNominalVoltageCriterion c -> Stream.of(c.getVoltageInterval1().orElse(null), c.getVoltageInterval2().orElse(null)).toList();
            case ThreeNominalVoltageCriterion c -> Stream.of(c.getVoltageInterval1().orElse(null), c.getVoltageInterval2().orElse(null),
                    c.getVoltageInterval3().orElse(null)).toList();
            case null, default -> List.of();
        };
        if (intervals.stream().allMatch(i -> i == null)) {
            return Optional.empty();
        }
        return Optional.of(intervals.stream().map(i -> i == null ? "*" : interval(i)).collect(Collectors.joining(" - ")) + " kV");
    }

    private static String interval(VoltageInterval interval) {
        return interval(interval.getNominalVoltageLowBound().map(LimitScalingTexts::number).orElse(null), interval.isLowClosed(),
                interval.getNominalVoltageHighBound().map(LimitScalingTexts::number).orElse(null), interval.isHighClosed());
    }

    private static String interval(String low, boolean lowClosed, String high, boolean highClosed) {
        return (low == null ? "]-∞" : (lowClosed ? "[" : "]") + low) + ", " + (high == null ? "+∞[" : high + (highClosed ? "]" : "["));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public static String durations(List<LimitDurationCriterion> criteria) {
        return criteria.isEmpty() ? Messages.get("limitScalings.durations.all")
                : criteria.stream().map(LimitScalingTexts::duration).collect(Collectors.joining(", "));
    }

    public static String duration(LimitDurationCriterion criterion) {
        return switch (criterion) {
            case PermanentDurationCriterion _ -> Messages.get("limitScalings.durations.permanent");
            case AllTemporaryDurationCriterion _ -> Messages.get("limitScalings.durations.allTemporary");
            case EqualityTemporaryDurationCriterion c -> Messages.get("limitScalings.durations.temporary", c.getDurationEqualityValue() + " s");
            case IntervalTemporaryDurationCriterion c -> Messages.get("limitScalings.durations.temporary",
                    interval(c.getLowBound().map(String::valueOf).orElse(null), c.isLowClosed(),
                            c.getHighBound().map(String::valueOf).orElse(null), c.isHighClosed()) + " s");
            default -> criterion.getType().name();
        };
    }

    public static String limitsGroups(List<String> groupIds) {
        return groupIds.isEmpty() ? Messages.get("limitScalings.limitsGroups.all") : String.join(", ", groupIds);
    }
}
