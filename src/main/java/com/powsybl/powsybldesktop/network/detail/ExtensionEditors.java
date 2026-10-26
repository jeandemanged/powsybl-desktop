/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.BatteryShortCircuit;
import com.powsybl.iidm.network.extensions.BatteryShortCircuitAdder;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.iidm.network.extensions.BusbarSectionPositionAdder;
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
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.HvdcOperatorActivePowerRange;
import com.powsybl.iidm.network.extensions.HvdcOperatorActivePowerRangeAdder;
import com.powsybl.iidm.network.extensions.InjectionObservability;
import com.powsybl.iidm.network.extensions.InjectionObservabilityAdder;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserve;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserveAdder;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.iidm.network.extensions.ObservabilityQuality;
import com.powsybl.iidm.network.extensions.OperatingStatus;
import com.powsybl.iidm.network.extensions.OperatingStatusAdder;
import com.powsybl.iidm.network.extensions.ReferencePriorities;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.iidm.network.extensions.StandbyAutomaton;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerPhaseAngleClock;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerPhaseAngleClockAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerToBeEstimated;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerToBeEstimatedAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerPhaseAngleClock;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerPhaseAngleClockAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerToBeEstimated;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerToBeEstimatedAdder;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The {@link ExtensionsPane.Editor}s of the IIDM extensions an equipment detail view can edit, grouped per equipment
 * type ({@link #forGenerator}, ...); those of extensions common to several types (injections, branches, any identifiable)
 * are shared.
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
        return concat(List.of(
                activePowerControl(),
                coordinatedReactiveControl(),
                generatorShortCircuit(),
                generatorStartup(),
                generatorEntsoeCategory(),
                generatorFortescue(),
                manualFrequencyRestorationReserve()), forInjection());
    }

    public static List<ExtensionsPane.Editor<Battery>> forBattery() {
        return concat(List.of(activePowerControl(), batteryShortCircuit(), manualFrequencyRestorationReserve()), forInjection());
    }

    public static List<ExtensionsPane.Editor<Load>> forLoad() {
        return concat(List.of(loadDetail()), forInjection());
    }

    public static List<ExtensionsPane.Editor<StaticVarCompensator>> forStaticVarCompensator() {
        return concat(List.of(standbyAutomaton()), forInjection());
    }

    // operating status: IIDM allows it on busbar sections, boundary lines, branches, three-winding transformers and HVDC lines only
    public static List<ExtensionsPane.Editor<BusbarSection>> forBusbarSection() {
        return concat(List.of(busbarSectionPosition(), ExtensionEditors.<BusbarSection>operatingStatus()), forInjection());
    }

    public static List<ExtensionsPane.Editor<BoundaryLine>> forBoundaryLine() {
        return concat(List.of(ExtensionEditors.<BoundaryLine>operatingStatus()), forInjection());
    }

    /**
     * Those of any injection.
     */
    public static <I extends Injection<I>> List<ExtensionsPane.Editor<I>> forInjection() {
        return concat(List.of(ExtensionEditors.<I>connectablePosition(), ExtensionEditors.<I>referencePriorities(),
                ExtensionEditors.<I>injectionObservability(), ExtensionEditors.<I>measurements()),
                ExtensionEditors.<I>forIdentifiable());
    }

    /**
     * Those of lines and two-winding transformers.
     */
    public static <B extends Branch<B> & Connectable<B>> List<ExtensionsPane.Editor<B>> forBranch() {
        return concat(List.of(ExtensionEditors.<B>connectablePosition(), ExtensionEditors.<B>measurements(), ExtensionEditors.<B>operatingStatus()),
                ExtensionEditors.<B>forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<TwoWindingsTransformer>> forTwoWindingsTransformer() {
        return concat(List.of(twoWindingsTransformerPhaseAngleClock(), twoWindingsTransformerToBeEstimated()),
                ExtensionEditors.<TwoWindingsTransformer>forBranch());
    }

    public static List<ExtensionsPane.Editor<ThreeWindingsTransformer>> forThreeWindingsTransformer() {
        return concat(List.of(threeWindingsTransformerPhaseAngleClock(), threeWindingsTransformerToBeEstimated(),
                connectablePosition(), measurements(), operatingStatus()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<TieLine>> forTieLine() {
        return concat(List.of(ExtensionEditors.<TieLine>operatingStatus()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<HvdcLine>> forHvdcLine() {
        return concat(List.of(hvdcAngleDroopActivePowerControl(), hvdcOperatorActivePowerRange(), operatingStatus()), forIdentifiable());
    }

    /**
     * Those of any identifiable.
     */
    public static <I extends Identifiable<I>> List<ExtensionsPane.Editor<I>> forIdentifiable() {
        return List.of(dynamicModelInfo());
    }

    private static <I> List<ExtensionsPane.Editor<I>> concat(List<ExtensionsPane.Editor<I>> specific, List<ExtensionsPane.Editor<I>> common) {
        return Stream.concat(specific.stream(), common.stream()).toList();
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
                        .intField("detail.extension.entsoeCategory.code", extension::getCode, code -> extension.setCode(DetailForm.mandatory(code))));
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
     * One feeder per terminal: an injection's single one, a branch's or three-winding transformer's per side.
     */
    @SuppressWarnings("unchecked")
    public static <C extends Connectable<C>> ExtensionsPane.Editor<C> connectablePosition() {
        return ExtensionsPane.<C, ConnectablePosition<C>>editor(ConnectablePosition.class, "detail.extension.connectablePosition",
                connectable -> {
                    ConnectablePositionAdder<C> adder = connectable.newExtension(ConnectablePositionAdder.class);
                    int terminals = connectable.getTerminals().size();
                    for (int side = 1; side <= terminals; side++) {
                        ConnectablePositionAdder.FeederAdder<C> feeder = switch (terminals == 1 ? 0 : side) {
                            case 0 -> adder.newFeeder();
                            case 1 -> adder.newFeeder1();
                            case 2 -> adder.newFeeder2();
                            default -> adder.newFeeder3();
                        };
                        feeder.withName(connectable.getId()).withDirection(ConnectablePosition.Direction.UNDEFINED).add();
                    }
                    adder.add();
                },
                (extension, form) -> {
                    List<ConnectablePosition.Feeder> feeders = Stream.of(extension.getFeeder(), extension.getFeeder1(), extension.getFeeder2(),
                            extension.getFeeder3()).filter(Objects::nonNull).toList();
                    for (int i = 0; i < feeders.size(); i++) {
                        ConnectablePosition.Feeder feeder = feeders.get(i);
                        if (feeders.size() > 1) {
                            form.section("limits.side", i + 1);
                        }
                        form.textField("detail.extension.connectablePosition.name", () -> feeder.getName().orElse(null), feeder::setName)
                                .intField("detail.extension.connectablePosition.order", () -> feeder.getOrder().orElse(null), order -> {
                                    if (order == null) {
                                        feeder.removeOrder();
                                    } else {
                                        feeder.setOrder(order);
                                    }
                                })
                                .choiceField("detail.extension.connectablePosition.direction", DetailSections.values(ConnectablePosition.Direction.class),
                                        DetailSections.enumFormat(), feeder::getDirection, feeder::setDirection);
                    }
                });
    }

    public static <I extends Injection<I>> ExtensionsPane.Editor<I> referencePriorities() {
        return ExtensionsPane.<I, ReferencePriorities<I>>editor(ReferencePriorities.class, "detail.extension.referencePriorities",
                injection -> ReferencePriority.set(injection, 0),
                (extension, form) -> form
                        .intField("detail.extension.referencePriorities.priority", () -> ReferencePriority.get(extension.getExtendable()),
                                priority -> ReferencePriority.set(extension.getExtendable(), DetailForm.mandatory(priority))));
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
                        .booleanField("detail.extension.activePowerControl.participate", extension::isParticipate, extension::setParticipate));
    }

    @SuppressWarnings("unchecked")
    public static <I extends Identifiable<I>> ExtensionsPane.Editor<I> dynamicModelInfo() {
        return ExtensionsPane.<I, DynamicModelInfo<I>>editor(DynamicModelInfo.class, "detail.extension.dynamicModelInfo",
                identifiable -> identifiable.newExtension(DynamicModelInfoAdder.class).setModelName("").add(),
                (extension, form) -> form
                        .textField("detail.extension.dynamicModelInfo.modelName", extension::getModelName, extension::setModelName));
    }

    @SuppressWarnings("unchecked")
    public static <I extends Identifiable<I>> ExtensionsPane.Editor<I> operatingStatus() {
        return ExtensionsPane.<I, OperatingStatus<I>>editor(OperatingStatus.class, "detail.extension.operatingStatus",
                identifiable -> identifiable.newExtension(OperatingStatusAdder.class).withStatus(OperatingStatus.Status.IN_OPERATION).add(),
                (extension, form) -> form
                        .choiceField("desktop.common.column.status", DetailSections.values(OperatingStatus.Status.class),
                                DetailSections.enumFormat(), extension::getStatus, extension::setStatus));
    }

    public static ExtensionsPane.Editor<Battery> batteryShortCircuit() {
        return ExtensionsPane.<Battery, BatteryShortCircuit>editor(BatteryShortCircuit.class, "detail.extension.shortCircuit",
                battery -> battery.newExtension(BatteryShortCircuitAdder.class).withDirectTransX(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.shortCircuit.directTransX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getDirectTransX, extension::setDirectTransX)
                        .doubleField("detail.extension.shortCircuit.directSubtransX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getDirectSubtransX, extension::setDirectSubtransX)
                        .doubleField("detail.extension.shortCircuit.stepUpTransformerX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getStepUpTransformerX, extension::setStepUpTransformerX));
    }

    public static ExtensionsPane.Editor<Load> loadDetail() {
        return ExtensionsPane.<Load, LoadDetail>editor(LoadDetail.class, "detail.extension.loadDetail",
                load -> load.newExtension(LoadDetailAdder.class).withFixedActivePower(0).withFixedReactivePower(0)
                        .withVariableActivePower(0).withVariableReactivePower(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.loadDetail.fixedActivePower", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getFixedActivePower, extension::setFixedActivePower)
                        .doubleField("detail.extension.loadDetail.fixedReactivePower", DetailForm.MVAR, GuiParameters.Quantity.POWER,
                                extension::getFixedReactivePower, extension::setFixedReactivePower)
                        .doubleField("detail.extension.loadDetail.variableActivePower", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getVariableActivePower, extension::setVariableActivePower)
                        .doubleField("detail.extension.loadDetail.variableReactivePower", DetailForm.MVAR, GuiParameters.Quantity.POWER,
                                extension::getVariableReactivePower, extension::setVariableReactivePower));
    }

    public static ExtensionsPane.Editor<StaticVarCompensator> standbyAutomaton() {
        return ExtensionsPane.<StaticVarCompensator, StandbyAutomaton>editor(StandbyAutomaton.class, "detail.extension.standbyAutomaton",
                svc -> svc.newExtension(StandbyAutomatonAdder.class).withStandbyStatus(false).withB0(0)
                        .withLowVoltageThreshold(0).withLowVoltageSetpoint(0).withHighVoltageThreshold(0).withHighVoltageSetpoint(0).add(),
                (extension, form) -> form
                        .booleanField("detail.extension.standbyAutomaton.standby", extension::isStandby, extension::setStandby)
                        .doubleField("detail.extension.standbyAutomaton.b0", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                                extension::getB0, extension::setB0)
                        .doubleField("detail.extension.standbyAutomaton.lowVoltageThreshold", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                                extension::getLowVoltageThreshold, extension::setLowVoltageThreshold)
                        .doubleField("detail.extension.standbyAutomaton.lowVoltageSetpoint", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                                extension::getLowVoltageSetpoint, extension::setLowVoltageSetpoint)
                        .doubleField("detail.extension.standbyAutomaton.highVoltageThreshold", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                                extension::getHighVoltageThreshold, extension::setHighVoltageThreshold)
                        .doubleField("detail.extension.standbyAutomaton.highVoltageSetpoint", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                                extension::getHighVoltageSetpoint, extension::setHighVoltageSetpoint));
    }

    public static ExtensionsPane.Editor<BusbarSection> busbarSectionPosition() {
        return ExtensionsPane.<BusbarSection, BusbarSectionPosition>editor(BusbarSectionPosition.class, "detail.extension.busbarSectionPosition",
                busbarSection -> busbarSection.newExtension(BusbarSectionPositionAdder.class).withBusbarIndex(1).withSectionIndex(1).add(),
                (extension, form) -> form
                        .intField("detail.extension.busbarSectionPosition.busbarIndex", extension::getBusbarIndex,
                                index -> extension.setBusbarIndex(DetailForm.mandatory(index)))
                        .intField("detail.extension.busbarSectionPosition.sectionIndex", extension::getSectionIndex,
                                index -> extension.setSectionIndex(DetailForm.mandatory(index))));
    }

    public static ExtensionsPane.Editor<HvdcLine> hvdcAngleDroopActivePowerControl() {
        return ExtensionsPane.<HvdcLine, HvdcAngleDroopActivePowerControl>editor(HvdcAngleDroopActivePowerControl.class,
                "detail.extension.hvdcAngleDroopActivePowerControl",
                line -> line.newExtension(HvdcAngleDroopActivePowerControlAdder.class).withP0(0).withDroop(0).withEnabled(false).add(),
                (extension, form) -> form
                        .booleanField("detail.extension.hvdcAngleDroopActivePowerControl.enabled", extension::isEnabled, extension::setEnabled)
                        .doubleField("desktop.common.column.p0", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getP0, p0 -> extension.setP0((float) p0))
                        .doubleField("detail.extension.activePowerControl.droop", null, GuiParameters.Quantity.POWER,
                                extension::getDroop, droop -> extension.setDroop((float) droop)));
    }

    public static ExtensionsPane.Editor<HvdcLine> hvdcOperatorActivePowerRange() {
        return ExtensionsPane.<HvdcLine, HvdcOperatorActivePowerRange>editor(HvdcOperatorActivePowerRange.class,
                "detail.extension.hvdcOperatorActivePowerRange",
                line -> line.newExtension(HvdcOperatorActivePowerRangeAdder.class).withOprFromCS1toCS2((float) line.getMaxP())
                        .withOprFromCS2toCS1((float) line.getMaxP()).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.hvdcOperatorActivePowerRange.fromCs1ToCs2", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getOprFromCS1toCS2, value -> extension.setOprFromCS1toCS2((float) value))
                        .doubleField("detail.extension.hvdcOperatorActivePowerRange.fromCs2ToCs1", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getOprFromCS2toCS1, value -> extension.setOprFromCS2toCS1((float) value)));
    }

    public static ExtensionsPane.Editor<TwoWindingsTransformer> twoWindingsTransformerPhaseAngleClock() {
        return ExtensionsPane.<TwoWindingsTransformer, TwoWindingsTransformerPhaseAngleClock>editor(TwoWindingsTransformerPhaseAngleClock.class,
                "detail.extension.phaseAngleClock",
                transformer -> transformer.newExtension(TwoWindingsTransformerPhaseAngleClockAdder.class).withPhaseAngleClock(0).add(),
                (extension, form) -> form
                        .intField("detail.extension.phaseAngleClock.clock", extension::getPhaseAngleClock,
                                clock -> extension.setPhaseAngleClock(DetailForm.mandatory(clock))));
    }

    public static ExtensionsPane.Editor<ThreeWindingsTransformer> threeWindingsTransformerPhaseAngleClock() {
        return ExtensionsPane.<ThreeWindingsTransformer, ThreeWindingsTransformerPhaseAngleClock>editor(ThreeWindingsTransformerPhaseAngleClock.class,
                "detail.extension.phaseAngleClock",
                transformer -> transformer.newExtension(ThreeWindingsTransformerPhaseAngleClockAdder.class)
                        .withPhaseAngleClockLeg2(0).withPhaseAngleClockLeg3(0).add(),
                (extension, form) -> form
                        .intField("detail.extension.phaseAngleClock.clockLeg2", extension::getPhaseAngleClockLeg2,
                                clock -> extension.setPhaseAngleClockLeg2(DetailForm.mandatory(clock)))
                        .intField("detail.extension.phaseAngleClock.clockLeg3", extension::getPhaseAngleClockLeg3,
                                clock -> extension.setPhaseAngleClockLeg3(DetailForm.mandatory(clock))));
    }

    public static ExtensionsPane.Editor<TwoWindingsTransformer> twoWindingsTransformerToBeEstimated() {
        return ExtensionsPane.<TwoWindingsTransformer, TwoWindingsTransformerToBeEstimated>editor(TwoWindingsTransformerToBeEstimated.class,
                "detail.extension.toBeEstimated",
                transformer -> transformer.newExtension(TwoWindingsTransformerToBeEstimatedAdder.class)
                        .withRatioTapChangerStatus(false).withPhaseTapChangerStatus(false).add(),
                (extension, form) -> form
                        .booleanField("transformers.column.ratioTapChanger", extension::shouldEstimateRatioTapChanger,
                                extension::shouldEstimateRatioTapChanger)
                        .booleanField("transformers.column.phaseTapChanger", extension::shouldEstimatePhaseTapChanger,
                                extension::shouldEstimatePhaseTapChanger));
    }

    public static ExtensionsPane.Editor<ThreeWindingsTransformer> threeWindingsTransformerToBeEstimated() {
        return ExtensionsPane.<ThreeWindingsTransformer, ThreeWindingsTransformerToBeEstimated>editor(ThreeWindingsTransformerToBeEstimated.class,
                "detail.extension.toBeEstimated",
                transformer -> transformer.newExtension(ThreeWindingsTransformerToBeEstimatedAdder.class).add(),
                (extension, form) -> {
                    for (ThreeSides side : ThreeSides.values()) {
                        form.section("detail.section.leg", side.getNum())
                                .booleanField("transformers.column.ratioTapChanger", () -> extension.shouldEstimateRatioTapChanger(side),
                                        estimate -> extension.shouldEstimateRatioTapChanger(estimate, side))
                                .booleanField("transformers.column.phaseTapChanger", () -> extension.shouldEstimatePhaseTapChanger(side),
                                        estimate -> extension.shouldEstimatePhaseTapChanger(estimate, side));
                    }
                });
    }
}
