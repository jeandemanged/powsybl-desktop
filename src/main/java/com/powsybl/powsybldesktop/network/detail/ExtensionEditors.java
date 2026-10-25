/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.extensions.ConnectablePositionAdder;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControl;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControlAdder;
import com.powsybl.iidm.network.extensions.DynamicModelInfo;
import com.powsybl.iidm.network.extensions.DynamicModelInfoAdder;
import com.powsybl.iidm.network.extensions.GeneratorEntsoeCategory;
import com.powsybl.iidm.network.extensions.GeneratorEntsoeCategoryAdder;
import com.powsybl.iidm.network.extensions.GeneratorFortescue;
import com.powsybl.iidm.network.extensions.GeneratorFortescueAdder;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuit;
import com.powsybl.iidm.network.extensions.GeneratorShortCircuitAdder;
import com.powsybl.iidm.network.extensions.GeneratorStartup;
import com.powsybl.iidm.network.extensions.GeneratorStartupAdder;
import com.powsybl.iidm.network.extensions.InjectionObservability;
import com.powsybl.iidm.network.extensions.InjectionObservabilityAdder;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserve;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserveAdder;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.iidm.network.extensions.ObservabilityQuality;
import com.powsybl.iidm.network.extensions.ReferencePriorities;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@link ExtensionsPane.Editor}s of the IIDM extensions an equipment detail view can edit, grouped per equipment
 * type ({@link #forGenerator}); those of extensions common to several types (injections, connectables) are shared.
 * A created extension gets neutral defaults (NaN, zero, false) the user then edits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ExtensionEditors {

    private static final StringConverter<Boolean> NULLABLE_BOOLEAN_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Boolean value) {
            return value == null ? "-" : value.toString();
        }

        @Override
        public Boolean fromString(String string) {
            throw new UnsupportedOperationException();
        }
    };

    private ExtensionEditors() {
    }

    public static List<ExtensionsPane.Editor<Generator>> forGenerator() {
        return List.of(
                activePowerControl(),
                coordinatedReactiveControl(),
                generatorShortCircuit(),
                generatorStartup(),
                generatorEntsoeCategory(),
                generatorFortescue(),
                connectablePosition(),
                referencePriorities(),
                injectionObservability(),
                measurements(),
                manualFrequencyRestorationReserve(),
                dynamicModelInfo());
    }

    @SuppressWarnings("unchecked")
    public static <I extends Injection<I>> ExtensionsPane.Editor<I> activePowerControl() {
        return ExtensionsPane.<I, ActivePowerControl<I>>editor(ActivePowerControl.class, "detail.extension.activePowerControl",
                injection -> injection.newExtension(ActivePowerControlAdder.class).withParticipate(false).add(),
                (extension, form) -> form
                        .booleanField("detail.extension.activePowerControl.participate", extension::isParticipate, extension::setParticipate)
                        .doubleField("detail.extension.activePowerControl.droop", null, GuiParameters.Quantity.PERCENTAGE,
                                extension::getDroop, extension::setDroop)
                        .doubleField("detail.extension.activePowerControl.participationFactor", null, GuiParameters.Quantity.PERCENTAGE,
                                extension::getParticipationFactor, extension::setParticipationFactor)
                        .doubleField("detail.extension.activePowerControl.minTargetP", DetailForm.MW, GuiParameters.Quantity.POWER,
                                () -> extension.getMinTargetP().orElse(Double.NaN), extension::setMinTargetP)
                        .doubleField("detail.extension.activePowerControl.maxTargetP", DetailForm.MW, GuiParameters.Quantity.POWER,
                                () -> extension.getMaxTargetP().orElse(Double.NaN), extension::setMaxTargetP));
    }

    public static ExtensionsPane.Editor<Generator> coordinatedReactiveControl() {
        return ExtensionsPane.<Generator, CoordinatedReactiveControl>editor(CoordinatedReactiveControl.class, "detail.extension.coordinatedReactiveControl",
                generator -> generator.newExtension(CoordinatedReactiveControlAdder.class).withQPercent(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.coordinatedReactiveControl.qPercent", DetailForm.PERCENT, GuiParameters.Quantity.PERCENTAGE,
                                extension::getQPercent, extension::setQPercent));
    }

    public static ExtensionsPane.Editor<Generator> generatorShortCircuit() {
        return ExtensionsPane.<Generator, GeneratorShortCircuit>editor(GeneratorShortCircuit.class, "detail.extension.shortCircuit",
                generator -> generator.newExtension(GeneratorShortCircuitAdder.class).withDirectTransX(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.shortCircuit.directTransX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getDirectTransX, extension::setDirectTransX)
                        .doubleField("detail.extension.shortCircuit.directSubtransX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getDirectSubtransX, extension::setDirectSubtransX)
                        .doubleField("detail.extension.shortCircuit.stepUpTransformerX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getStepUpTransformerX, extension::setStepUpTransformerX));
    }

    public static ExtensionsPane.Editor<Generator> generatorStartup() {
        return ExtensionsPane.<Generator, GeneratorStartup>editor(GeneratorStartup.class, "detail.extension.startup",
                generator -> generator.newExtension(GeneratorStartupAdder.class).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.startup.plannedActivePowerSetpoint", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getPlannedActivePowerSetpoint, extension::setPlannedActivePowerSetpoint)
                        .doubleField("detail.extension.startup.startupCost", null, GuiParameters.Quantity.POWER,
                                extension::getStartupCost, extension::setStartupCost)
                        .doubleField("detail.extension.startup.marginalCost", null, GuiParameters.Quantity.POWER,
                                extension::getMarginalCost, extension::setMarginalCost)
                        .doubleField("detail.extension.startup.plannedOutageRate", null, GuiParameters.Quantity.POWER_FACTOR,
                                extension::getPlannedOutageRate, extension::setPlannedOutageRate)
                        .doubleField("detail.extension.startup.forcedOutageRate", null, GuiParameters.Quantity.POWER_FACTOR,
                                extension::getForcedOutageRate, extension::setForcedOutageRate));
    }

    public static ExtensionsPane.Editor<Generator> generatorEntsoeCategory() {
        return ExtensionsPane.<Generator, GeneratorEntsoeCategory>editor(GeneratorEntsoeCategory.class, "detail.extension.entsoeCategory",
                generator -> generator.newExtension(GeneratorEntsoeCategoryAdder.class).withCode(0).add(),
                (extension, form) -> form
                        .intField("detail.extension.entsoeCategory.code", extension::getCode, code -> extension.setCode(mandatory(code))));
    }

    public static ExtensionsPane.Editor<Generator> generatorFortescue() {
        return ExtensionsPane.<Generator, GeneratorFortescue>editor(GeneratorFortescue.class, "detail.extension.fortescue",
                generator -> generator.newExtension(GeneratorFortescueAdder.class).add(),
                (extension, form) -> form
                        .booleanField("detail.extension.fortescue.grounded", extension::isGrounded, extension::setGrounded)
                        .doubleField("detail.extension.fortescue.rz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getRz, extension::setRz)
                        .doubleField("detail.extension.fortescue.xz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getXz, extension::setXz)
                        .doubleField("detail.extension.fortescue.rn", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getRn, extension::setRn)
                        .doubleField("detail.extension.fortescue.xn", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getXn, extension::setXn)
                        .doubleField("detail.extension.fortescue.groundingR", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingR, extension::setGroundingR)
                        .doubleField("detail.extension.fortescue.groundingX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingX, extension::setGroundingX));
    }

    /**
     * Of a single-feeder connectable (an injection).
     */
    @SuppressWarnings("unchecked")
    public static <C extends Connectable<C>> ExtensionsPane.Editor<C> connectablePosition() {
        return ExtensionsPane.<C, ConnectablePosition<C>>editor(ConnectablePosition.class, "detail.extension.connectablePosition",
                connectable -> connectable.newExtension(ConnectablePositionAdder.class).newFeeder()
                        .withName(connectable.getId())
                        .withDirection(ConnectablePosition.Direction.UNDEFINED)
                        .add()
                        .add(),
                (extension, form) -> {
                    ConnectablePosition.Feeder feeder = extension.getFeeder();
                    if (feeder == null) {
                        form.text("detail.extension.connectablePosition.feeder", () -> null);
                        return;
                    }
                    form.textField("detail.extension.connectablePosition.name", () -> feeder.getName().orElse(null), feeder::setName)
                            .intField("detail.extension.connectablePosition.order", () -> feeder.getOrder().orElse(null), order -> {
                                if (order == null) {
                                    feeder.removeOrder();
                                } else {
                                    feeder.setOrder(order);
                                }
                            })
                            .choiceField("detail.extension.connectablePosition.direction", Arrays.asList(ConnectablePosition.Direction.values()),
                                    enumFormat(Enum::name), feeder::getDirection, feeder::setDirection);
                });
    }

    public static <I extends Injection<I>> ExtensionsPane.Editor<I> referencePriorities() {
        return ExtensionsPane.<I, ReferencePriorities<I>>editor(ReferencePriorities.class, "detail.extension.referencePriorities",
                injection -> ReferencePriority.set(injection, 0),
                (extension, form) -> form
                        .intField("detail.extension.referencePriorities.priority", () -> ReferencePriority.get(extension.getExtendable()),
                                priority -> ReferencePriority.set(extension.getExtendable(), mandatory(priority))));
    }

    @SuppressWarnings("unchecked")
    public static <I extends Injection<I>> ExtensionsPane.Editor<I> injectionObservability() {
        return ExtensionsPane.<I, InjectionObservability<I>>editor(InjectionObservability.class, "detail.extension.observability",
                injection -> injection.newExtension(InjectionObservabilityAdder.class).withObservable(false).add(),
                (extension, form) -> {
                    form.booleanField("detail.extension.observability.observable", extension::isObservable, extension::setObservable);
                    quality(form, "detail.extension.observability.qualityP", "detail.extension.observability.redundantP",
                            extension::getNullableQualityP, extension::setQualityP);
                    quality(form, "detail.extension.observability.qualityQ", "detail.extension.observability.redundantQ",
                            extension::getNullableQualityQ, extension::setQualityQ);
                    quality(form, "detail.extension.observability.qualityV", "detail.extension.observability.redundantV",
                            extension::getNullableQualityV, extension::setQualityV);
                });
    }

    private interface QualitySetter {
        void set(double standardDeviation, Boolean redundant);
    }

    private static void quality(DetailForm form, String standardDeviationKey, String redundantKey,
                                Supplier<ObservabilityQuality<?>> quality, QualitySetter setter) {
        form.doubleField(standardDeviationKey, null, GuiParameters.Quantity.POWER,
                        () -> quality.get() == null ? Double.NaN : quality.get().getStandardDeviation(),
                        value -> setter.set(value, quality.get() == null ? null : quality.get().isRedundant().orElse(null)))
                .choiceField(redundantKey, Arrays.asList(null, true, false), NULLABLE_BOOLEAN_FORMAT,
                        () -> quality.get() == null ? null : quality.get().isRedundant().orElse(null),
                        redundant -> setter.set(quality.get() == null ? Double.NaN : quality.get().getStandardDeviation(), redundant));
    }

    /**
     * Values are editable, measurements aren't added nor removed: not offered in the add menu.
     */
    public static <C extends Connectable<C>> ExtensionsPane.Editor<C> measurements() {
        return ExtensionsPane.<C, Measurements<C>>editor(Measurements.class, "detail.extension.measurements", null,
                (extension, form) -> extension.getMeasurements().stream()
                        .sorted(Comparator.comparing(Measurement::getType).thenComparing(measurement -> String.valueOf(measurement.getId())))
                        .forEach(measurement -> {
                            form.section(null);
                            form.text("detail.extension.measurements.measurement", () -> measurement.getType()
                                    + (measurement.getId() == null ? "" : " " + measurement.getId())
                                    + (measurement.getSide() == null ? "" : " " + measurement.getSide()));
                            form.doubleField("detail.extension.measurements.value", null, GuiParameters.Quantity.POWER,
                                            measurement::getValue, measurement::setValue)
                                    .doubleField("detail.extension.measurements.standardDeviation", null, GuiParameters.Quantity.POWER,
                                            measurement::getStandardDeviation, measurement::setStandardDeviation)
                                    .booleanField("detail.extension.measurements.valid", measurement::isValid, measurement::setValid);
                        }));
    }

    @SuppressWarnings("unchecked")
    public static <I extends Injection<I>> ExtensionsPane.Editor<I> manualFrequencyRestorationReserve() {
        return ExtensionsPane.<I, ManualFrequencyRestorationReserve<I>>editor(ManualFrequencyRestorationReserve.class,
                "detail.extension.manualFrequencyRestorationReserve",
                injection -> injection.newExtension(ManualFrequencyRestorationReserveAdder.class).withParticipate(false).add(),
                (extension, form) -> form
                        .booleanField("detail.extension.manualFrequencyRestorationReserve.participate", extension::isParticipate, extension::setParticipate));
    }

    @SuppressWarnings("unchecked")
    public static <I extends Injection<I>> ExtensionsPane.Editor<I> dynamicModelInfo() {
        return ExtensionsPane.<I, DynamicModelInfo<I>>editor(DynamicModelInfo.class, "detail.extension.dynamicModelInfo",
                injection -> injection.newExtension(DynamicModelInfoAdder.class).setModelName("").add(),
                (extension, form) -> form
                        .textField("detail.extension.dynamicModelInfo.modelName", extension::getModelName, extension::setModelName));
    }

    private static int mandatory(Integer value) {
        if (value == null) {
            throw new PowsyblException(Messages.get("detail.editError.mandatory"));
        }
        return value;
    }

    private static <E extends Enum<E>> StringConverter<E> enumFormat(Function<E, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public E fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
