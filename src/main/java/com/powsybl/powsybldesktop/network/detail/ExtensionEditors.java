/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.cgmes.extensions.BaseVoltageMapping;
import com.powsybl.cgmes.extensions.CgmesBoundaryLineBoundaryNode;
import com.powsybl.cgmes.extensions.CgmesLineBoundaryNode;
import com.powsybl.cgmes.extensions.CgmesMetadataModels;
import com.powsybl.cgmes.extensions.CgmesTapChanger;
import com.powsybl.cgmes.extensions.CgmesTapChangers;
import com.powsybl.cgmes.extensions.CimCharacteristics;
import com.powsybl.entsoe.util.EntsoeArea;
import com.powsybl.entsoe.util.EntsoeAreaAdder;
import com.powsybl.entsoe.util.EntsoeGeographicalCode;
import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ActivePowerControl;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.BatteryShortCircuit;
import com.powsybl.iidm.network.extensions.BatteryShortCircuitAdder;
import com.powsybl.iidm.network.extensions.BranchObservability;
import com.powsybl.iidm.network.extensions.BranchObservabilityAdder;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.iidm.network.extensions.BusbarSectionPositionAdder;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.extensions.ConnectablePositionAdder;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControl;
import com.powsybl.iidm.network.extensions.CoordinatedReactiveControlAdder;
import com.powsybl.iidm.network.extensions.DiscreteMeasurement;
import com.powsybl.iidm.network.extensions.DiscreteMeasurementAdder;
import com.powsybl.iidm.network.extensions.DiscreteMeasurements;
import com.powsybl.iidm.network.extensions.DiscreteMeasurementsAdder;
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
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuit;
import com.powsybl.iidm.network.extensions.IdentifiableShortCircuitAdder;
import com.powsybl.iidm.network.extensions.InjectionObservability;
import com.powsybl.iidm.network.extensions.InjectionObservabilityAdder;
import com.powsybl.iidm.network.extensions.LegFortescue;
import com.powsybl.iidm.network.extensions.LineCouplings;
import com.powsybl.iidm.network.extensions.LineFortescue;
import com.powsybl.iidm.network.extensions.LineFortescueAdder;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.LoadAsymmetrical;
import com.powsybl.iidm.network.extensions.LoadAsymmetricalAdder;
import com.powsybl.iidm.network.extensions.LoadConnectionType;
import com.powsybl.iidm.network.extensions.LoadDetail;
import com.powsybl.iidm.network.extensions.LoadDetailAdder;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserve;
import com.powsybl.iidm.network.extensions.ManualFrequencyRestorationReserveAdder;
import com.powsybl.iidm.network.extensions.Measurement;
import com.powsybl.iidm.network.extensions.Measurements;
import com.powsybl.iidm.network.extensions.MeasurementsAdder;
import com.powsybl.iidm.network.extensions.ObservabilityArea;
import com.powsybl.iidm.network.extensions.ObservabilityQuality;
import com.powsybl.iidm.network.extensions.OperatingStatus;
import com.powsybl.iidm.network.extensions.OperatingStatusAdder;
import com.powsybl.iidm.network.extensions.PilotPoint;
import com.powsybl.iidm.network.extensions.ReferencePriorities;
import com.powsybl.iidm.network.extensions.ReferencePriority;
import com.powsybl.iidm.network.extensions.ReferenceTerminals;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.iidm.network.extensions.StandbyAutomaton;
import com.powsybl.iidm.network.extensions.StandbyAutomatonAdder;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerFortescue;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerFortescueAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerPhaseAngleClock;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerPhaseAngleClockAdder;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerToBeEstimated;
import com.powsybl.iidm.network.extensions.ThreeWindingsTransformerToBeEstimatedAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerFortescue;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerFortescueAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerPhaseAngleClock;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerPhaseAngleClockAdder;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerToBeEstimated;
import com.powsybl.iidm.network.extensions.TwoWindingsTransformerToBeEstimatedAdder;
import com.powsybl.iidm.network.extensions.WindingConnectionType;
import com.powsybl.powsybldesktop.network.edit.PositionEdits;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

    private static final int MAX_LINES = 50;

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
        return concat(List.of(loadDetail(), loadAsymmetrical()), forInjection());
    }

    public static List<ExtensionsPane.Editor<StaticVarCompensator>> forStaticVarCompensator() {
        return concat(List.of(standbyAutomaton()), forInjection());
    }

    // operating status: IIDM allows it on busbar sections, boundary lines, branches, three-winding transformers and HVDC lines only
    public static List<ExtensionsPane.Editor<BusbarSection>> forBusbarSection() {
        return concat(List.of(busbarSectionPosition(), ExtensionEditors.<BusbarSection>operatingStatus()), forInjection());
    }

    public static List<ExtensionsPane.Editor<BoundaryLine>> forBoundaryLine() {
        return concat(List.of(ExtensionEditors.<BoundaryLine>operatingStatus(), ExtensionEditors.<BoundaryLine>linePosition(),
                cgmesBoundaryLineBoundaryNode()), forInjection());
    }

    public static List<ExtensionsPane.Editor<ShuntCompensator>> forShuntCompensator() {
        return concat(List.of(ExtensionEditors.<ShuntCompensator>discreteMeasurements()), ExtensionEditors.<ShuntCompensator>forInjection());
    }

    public static List<ExtensionsPane.Editor<Switch>> forSwitch() {
        return concat(List.of(ExtensionEditors.<Switch>discreteMeasurements()), ExtensionEditors.<Switch>forIdentifiable());
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
        return concat(List.of(ExtensionEditors.<B>connectablePosition(), ExtensionEditors.<B>measurements(), ExtensionEditors.<B>operatingStatus(),
                        ExtensionEditors.<B>branchObservability()),
                ExtensionEditors.<B>forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<Line>> forLine() {
        return concat(List.of(lineFortescue(), ExtensionEditors.<Line>linePosition()), ExtensionEditors.<Line>forBranch());
    }

    public static List<ExtensionsPane.Editor<TwoWindingsTransformer>> forTwoWindingsTransformer() {
        return concat(List.of(twoWindingsTransformerPhaseAngleClock(), twoWindingsTransformerToBeEstimated(), twoWindingsTransformerFortescue(),
                        ExtensionEditors.<TwoWindingsTransformer>cgmesTapChangers(), ExtensionEditors.<TwoWindingsTransformer>discreteMeasurements()),
                ExtensionEditors.<TwoWindingsTransformer>forBranch());
    }

    public static List<ExtensionsPane.Editor<ThreeWindingsTransformer>> forThreeWindingsTransformer() {
        return concat(List.of(threeWindingsTransformerPhaseAngleClock(), threeWindingsTransformerToBeEstimated(), threeWindingsTransformerFortescue(),
                connectablePosition(), measurements(), operatingStatus(), cgmesTapChangers(), discreteMeasurements()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<TieLine>> forTieLine() {
        return concat(List.of(ExtensionEditors.<TieLine>operatingStatus(), cgmesLineBoundaryNode()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<HvdcLine>> forHvdcLine() {
        return concat(List.of(hvdcAngleDroopActivePowerControl(), hvdcOperatorActivePowerRange(), operatingStatus()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<Substation>> forSubstation() {
        return concat(List.of(substationPosition(), entsoeArea()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<VoltageLevel>> forVoltageLevel() {
        return concat(List.of(identifiableShortCircuit(), slackTerminal(), observabilityArea()), forIdentifiable());
    }

    public static List<ExtensionsPane.Editor<Network>> forNetwork() {
        return List.of(secondaryVoltageControl(), lineCouplings(), referenceTerminals(), cgmesMetadataModels(), cimCharacteristics(),
                baseVoltageMapping());
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
     * Measurements are added from a type and, on a multi-terminal equipment, a side (IIDM refuses a side on an
     * injection), invalid and without value for the user to fill in; each is removed on its own.
     */
    @SuppressWarnings("unchecked")
    public static <C extends Connectable<C>> ExtensionsPane.Editor<C> measurements() {
        return ExtensionsPane.<C, Measurements<C>>editor(Measurements.class, "detail.extension.measurements",
                connectable -> connectable.newExtension(MeasurementsAdder.class).add(),
                ExtensionEditors::measurementsForm);
    }

    private static <C extends Connectable<C>> void measurementsForm(Measurements<C> extension, DetailForm form) {
        form.structure(() -> List.copyOf(extension.getMeasurements()));
        C connectable = extension.getExtendable();
        int terminals = connectable.getTerminals().size();
        ChoiceBox<Measurement.Type> type = choiceBox(DetailSections.values(Measurement.Type.class), DetailSections.enumFormat());
        ChoiceBox<ThreeSides> side = choiceBox(terminals > 1 ? Arrays.asList(ThreeSides.values()).subList(0, terminals) : List.of(),
                format(value -> Messages.get("limits.side", value.getNum())));
        Button add = addButton(form, () -> extension.newMeasurement()
                .setId(connectable.getId() + "_" + type.getValue() + (side.getValue() == null ? "" : "_" + side.getValue().getNum()))
                .setEnsureIdUnicity(true)
                .setType(type.getValue())
                .setSide(side.getValue())
                .setValue(Double.NaN)
                .setStandardDeviation(Double.NaN)
                .setValid(false)
                .add());
        form.node("detail.extension.measurements.add", terminals > 1 ? new HBox(4, type, side, add) : new HBox(4, type, add), null, null);
        extension.getMeasurements().stream()
                .sorted(Comparator.comparing(Measurement::getType).thenComparing(measurement -> String.valueOf(measurement.getId())))
                .forEach(measurement -> {
                    measurementHeader(form, () -> measurement.getType()
                            + (measurement.getId() == null ? "" : " " + measurement.getId())
                            + (measurement.getSide() == null ? "" : " " + measurement.getSide()), measurement::remove);
                    form.doubleField("detail.extension.measurements.value", null, GuiParameters.Quantity.POWER,
                                    measurement::getValue, measurement::setValue)
                            .doubleField("detail.extension.measurements.standardDeviation", null, GuiParameters.Quantity.POWER,
                                    measurement::getStandardDeviation, measurement::setStandardDeviation)
                            .booleanField("detail.extension.measurements.valid", measurement::isValid, measurement::setValid);
                });
    }

    private static <T> ChoiceBox<T> choiceBox(List<T> choices, StringConverter<T> converter) {
        ChoiceBox<T> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().setAll(choices);
        choiceBox.setConverter(converter);
        if (!choices.isEmpty()) {
            choiceBox.setValue(choices.getFirst());
        }
        return choiceBox;
    }

    private static <T> StringConverter<T> format(Function<T, String> format) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : format.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Button addButton(DetailForm form, Runnable add) {
        Button button = new Button(Messages.get("desktop.common.action.add"));
        button.disableProperty().bind(form.editableProperty().not());
        button.setOnAction(_ -> form.apply(button, add));
        return button;
    }

    // a separator, then the measurement's label and a button removing it
    private static void measurementHeader(DetailForm form, Supplier<String> label, Runnable remove) {
        Label text = new Label();
        Button removeButton = new Button(Messages.get("desktop.common.action.remove"));
        removeButton.disableProperty().bind(form.editableProperty().not());
        removeButton.setOnAction(_ -> form.apply(removeButton, remove));
        form.section(null).node("detail.extension.measurements.measurement", new HBox(8, text, removeButton), null, () -> text.setText(label.get()));
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

    /**
     * Its coordinate is immutable: edited by replacing the extension.
     */
    public static ExtensionsPane.Editor<Substation> substationPosition() {
        return ExtensionsPane.<Substation, SubstationPosition>editor(SubstationPosition.class, "detail.extension.substationPosition",
                substation -> substation.newExtension(SubstationPositionAdder.class).withCoordinate(new Coordinate(0, 0)).add(),
                (extension, form) -> {
                    Substation substation = extension.getExtendable();
                    form.doubleField("detail.extension.substationPosition.latitude", DetailForm.DEGREE, GuiParameters.Quantity.COORDINATE,
                                    () -> position(substation).getCoordinate().getLatitude(),
                                    latitude -> setPosition(substation, latitude, position(substation).getCoordinate().getLongitude()))
                            .doubleField("detail.extension.substationPosition.longitude", DetailForm.DEGREE, GuiParameters.Quantity.COORDINATE,
                                    () -> position(substation).getCoordinate().getLongitude(),
                                    longitude -> setPosition(substation, position(substation).getCoordinate().getLatitude(), longitude));
                });
    }

    private static SubstationPosition position(Substation substation) {
        return substation.getExtension(SubstationPosition.class);
    }

    // the ends of its lines' routes follow
    private static void setPosition(Substation substation, double latitude, double longitude) {
        PositionEdits.setSubstationPosition(substation, new Coordinate(latitude, longitude));
    }

    @SuppressWarnings("unchecked")
    public static ExtensionsPane.Editor<VoltageLevel> identifiableShortCircuit() {
        return ExtensionsPane.<VoltageLevel, IdentifiableShortCircuit<VoltageLevel>>editor(IdentifiableShortCircuit.class, "detail.extension.shortCircuit",
                voltageLevel -> voltageLevel.newExtension(IdentifiableShortCircuitAdder.class).withIpMin(0).withIpMax(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.shortCircuit.ipMin", DetailForm.A, GuiParameters.Quantity.CURRENT,
                                extension::getIpMin, extension::setIpMin)
                        .doubleField("detail.extension.shortCircuit.ipMax", DetailForm.A, GuiParameters.Quantity.CURRENT,
                                extension::getIpMax, extension::setIpMax));
    }

    @SuppressWarnings("unchecked")
    public static <B extends Branch<B>> ExtensionsPane.Editor<B> branchObservability() {
        return ExtensionsPane.<B, BranchObservability<B>>editor(BranchObservability.class, "detail.extension.observability",
                branch -> branch.newExtension(BranchObservabilityAdder.class).withObservable(false).add(),
                (extension, form) -> {
                    form.booleanField("detail.extension.observability.observable", extension::isObservable, extension::setObservable);
                    form.section("limits.side", 1);
                    quality(form, "detail.extension.observability.qualityP", "detail.extension.observability.redundantP",
                            extension::getNullableQualityP1, extension::setQualityP1);
                    quality(form, "detail.extension.observability.qualityQ", "detail.extension.observability.redundantQ",
                            extension::getNullableQualityQ1, extension::setQualityQ1);
                    form.section("limits.side", 2);
                    quality(form, "detail.extension.observability.qualityP", "detail.extension.observability.redundantP",
                            extension::getNullableQualityP2, extension::setQualityP2);
                    quality(form, "detail.extension.observability.qualityQ", "detail.extension.observability.redundantQ",
                            extension::getNullableQualityQ2, extension::setQualityQ2);
                });
    }

    public static ExtensionsPane.Editor<Line> lineFortescue() {
        return ExtensionsPane.<Line, LineFortescue>editor(LineFortescue.class, "detail.extension.fortescue",
                line -> line.newExtension(LineFortescueAdder.class).withRz(0).withXz(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.fortescue.rz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getRz, extension::setRz)
                        .doubleField("detail.extension.fortescue.xz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getXz, extension::setXz)
                        .doubleField("detail.extension.fortescue.g1z", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                                extension::getG1z, extension::setG1z)
                        .doubleField("detail.extension.fortescue.b1z", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                                extension::getB1z, extension::setB1z)
                        .doubleField("detail.extension.fortescue.g2z", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                                extension::getG2z, extension::setG2z)
                        .doubleField("detail.extension.fortescue.b2z", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                                extension::getB2z, extension::setB2z)
                        .booleanField("detail.extension.fortescue.openPhaseA", extension::isOpenPhaseA, extension::setOpenPhaseA)
                        .booleanField("detail.extension.fortescue.openPhaseB", extension::isOpenPhaseB, extension::setOpenPhaseB)
                        .booleanField("detail.extension.fortescue.openPhaseC", extension::isOpenPhaseC, extension::setOpenPhaseC));
    }

    public static ExtensionsPane.Editor<TwoWindingsTransformer> twoWindingsTransformerFortescue() {
        return ExtensionsPane.<TwoWindingsTransformer, TwoWindingsTransformerFortescue>editor(TwoWindingsTransformerFortescue.class,
                "detail.extension.fortescue",
                transformer -> transformer.newExtension(TwoWindingsTransformerFortescueAdder.class).withRz(0).withXz(0).add(),
                (extension, form) -> form
                        .doubleField("detail.extension.fortescue.rz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getRz, extension::setRz)
                        .doubleField("detail.extension.fortescue.xz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getXz, extension::setXz)
                        .booleanField("detail.extension.fortescue.freeFluxes", extension::isFreeFluxes, extension::setFreeFluxes)
                        .doubleField("detail.extension.fortescue.xm", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, extension::getXm, extension::setXm)
                        .section("limits.side", 1)
                        .choiceField("detail.extension.fortescue.connectionType", DetailSections.values(WindingConnectionType.class),
                                DetailSections.enumFormat(), extension::getConnectionType1, extension::setConnectionType1)
                        .doubleField("detail.extension.fortescue.groundingR", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingR1, extension::setGroundingR1)
                        .doubleField("detail.extension.fortescue.groundingX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingX1, extension::setGroundingX1)
                        .section("limits.side", 2)
                        .choiceField("detail.extension.fortescue.connectionType", DetailSections.values(WindingConnectionType.class),
                                DetailSections.enumFormat(), extension::getConnectionType2, extension::setConnectionType2)
                        .doubleField("detail.extension.fortescue.groundingR", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingR2, extension::setGroundingR2)
                        .doubleField("detail.extension.fortescue.groundingX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                extension::getGroundingX2, extension::setGroundingX2));
    }

    public static ExtensionsPane.Editor<ThreeWindingsTransformer> threeWindingsTransformerFortescue() {
        return ExtensionsPane.<ThreeWindingsTransformer, ThreeWindingsTransformerFortescue>editor(ThreeWindingsTransformerFortescue.class,
                "detail.extension.fortescue",
                transformer -> transformer.newExtension(ThreeWindingsTransformerFortescueAdder.class)
                        .leg1().withRz(0).withXz(0).leg2().withRz(0).withXz(0).leg3().withRz(0).withXz(0).add(),
                (extension, form) -> {
                    List<LegFortescue> legs = List.of(extension.getLeg1(), extension.getLeg2(), extension.getLeg3());
                    for (int i = 0; i < legs.size(); i++) {
                        LegFortescue leg = legs.get(i);
                        form.section("detail.section.leg", i + 1)
                                .doubleField("detail.extension.fortescue.rz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, leg::getRz, leg::setRz)
                                .doubleField("detail.extension.fortescue.xz", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, leg::getXz, leg::setXz)
                                .booleanField("detail.extension.fortescue.freeFluxes", leg::isFreeFluxes, leg::setFreeFluxes)
                                .choiceField("detail.extension.fortescue.connectionType", DetailSections.values(WindingConnectionType.class),
                                        DetailSections.enumFormat(), leg::getConnectionType, leg::setConnectionType)
                                .doubleField("detail.extension.fortescue.groundingR", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                        leg::getGroundingR, leg::setGroundingR)
                                .doubleField("detail.extension.fortescue.groundingX", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE,
                                        leg::getGroundingX, leg::setGroundingX);
                    }
                });
    }

    public static ExtensionsPane.Editor<Load> loadAsymmetrical() {
        return ExtensionsPane.<Load, LoadAsymmetrical>editor(LoadAsymmetrical.class, "detail.extension.loadAsymmetrical",
                load -> load.newExtension(LoadAsymmetricalAdder.class).withConnectionType(LoadConnectionType.Y).add(),
                (extension, form) -> form
                        .choiceField("detail.extension.fortescue.connectionType", DetailSections.values(LoadConnectionType.class),
                                DetailSections.enumFormat(), extension::getConnectionType, extension::setConnectionType)
                        .doubleField("detail.extension.loadAsymmetrical.deltaPa", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getDeltaPa, extension::setDeltaPa)
                        .doubleField("detail.extension.loadAsymmetrical.deltaQa", DetailForm.MVAR, GuiParameters.Quantity.POWER,
                                extension::getDeltaQa, extension::setDeltaQa)
                        .doubleField("detail.extension.loadAsymmetrical.deltaPb", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getDeltaPb, extension::setDeltaPb)
                        .doubleField("detail.extension.loadAsymmetrical.deltaQb", DetailForm.MVAR, GuiParameters.Quantity.POWER,
                                extension::getDeltaQb, extension::setDeltaQb)
                        .doubleField("detail.extension.loadAsymmetrical.deltaPc", DetailForm.MW, GuiParameters.Quantity.POWER,
                                extension::getDeltaPc, extension::setDeltaPc)
                        .doubleField("detail.extension.loadAsymmetrical.deltaQc", DetailForm.MVAR, GuiParameters.Quantity.POWER,
                                extension::getDeltaQc, extension::setDeltaQc));
    }

    /**
     * What a discrete measurement can measure on an equipment: per IIDM, a switch's position, a transformer's tap
     * changers (by leg on a three-winding one), a shunt compensator's section, or anything else.
     */
    private record DiscreteKind(DiscreteMeasurement.Type type, DiscreteMeasurement.TapChanger tapChanger) {
    }

    private static List<DiscreteKind> discreteKinds(Identifiable<?> identifiable) {
        List<DiscreteKind> kinds = new ArrayList<>();
        switch (identifiable) {
            case Switch ignored -> kinds.add(new DiscreteKind(DiscreteMeasurement.Type.SWITCH_POSITION, null));
            case ShuntCompensator ignored -> kinds.add(new DiscreteKind(DiscreteMeasurement.Type.SHUNT_COMPENSATOR_SECTION, null));
            case TwoWindingsTransformer transformer -> {
                if (transformer.hasRatioTapChanger()) {
                    kinds.add(new DiscreteKind(DiscreteMeasurement.Type.TAP_POSITION, DiscreteMeasurement.TapChanger.RATIO_TAP_CHANGER));
                }
                if (transformer.hasPhaseTapChanger()) {
                    kinds.add(new DiscreteKind(DiscreteMeasurement.Type.TAP_POSITION, DiscreteMeasurement.TapChanger.PHASE_TAP_CHANGER));
                }
            }
            case ThreeWindingsTransformer transformer -> transformer.getLegStream().forEach(leg -> {
                int num = leg.getSide().getNum();
                if (leg.hasRatioTapChanger()) {
                    kinds.add(new DiscreteKind(DiscreteMeasurement.Type.TAP_POSITION, DiscreteMeasurement.TapChanger.valueOf("RATIO_TAP_CHANGER_" + num)));
                }
                if (leg.hasPhaseTapChanger()) {
                    kinds.add(new DiscreteKind(DiscreteMeasurement.Type.TAP_POSITION, DiscreteMeasurement.TapChanger.valueOf("PHASE_TAP_CHANGER_" + num)));
                }
            });
            default -> {
            }
        }
        kinds.add(new DiscreteKind(DiscreteMeasurement.Type.OTHER, null));
        return kinds;
    }

    /**
     * Discrete measurements are added from what they measure, invalid and with a neutral value of the type IIDM
     * expects (a boolean switch position, an integer tap position or section, a text otherwise); each is removed on
     * its own.
     */
    @SuppressWarnings("unchecked")
    public static <I extends Identifiable<I>> ExtensionsPane.Editor<I> discreteMeasurements() {
        return ExtensionsPane.<I, DiscreteMeasurements<I>>editor(DiscreteMeasurements.class, "detail.extension.discreteMeasurements",
                identifiable -> identifiable.newExtension(DiscreteMeasurementsAdder.class).add(),
                ExtensionEditors::discreteMeasurementsForm);
    }

    private static <I extends Identifiable<I>> void discreteMeasurementsForm(DiscreteMeasurements<I> extension, DetailForm form) {
        form.structure(() -> List.copyOf(extension.getDiscreteMeasurements()));
        ChoiceBox<DiscreteKind> kind = choiceBox(discreteKinds(extension.getExtendable()),
                format(value -> value.type() + (value.tapChanger() == null ? "" : " " + value.tapChanger())));
        Button add = addButton(form, () -> addDiscreteMeasurement(extension, kind.getValue()));
        form.node("detail.extension.measurements.add", new HBox(4, kind, add), null, null);
        extension.getDiscreteMeasurements().stream()
                .sorted(Comparator.comparing(DiscreteMeasurement::getType).thenComparing(measurement -> String.valueOf(measurement.getId())))
                .forEach(measurement -> {
                    measurementHeader(form, () -> measurement.getType()
                            + (measurement.getId() == null ? "" : " " + measurement.getId())
                            + (measurement.getTapChanger() == null ? "" : " " + measurement.getTapChanger()), measurement::remove);
                    switch (measurement.getValueType()) {
                        case BOOLEAN -> form.booleanField("detail.extension.measurements.value", measurement::getValueAsBoolean,
                                measurement::setValue);
                        case INT -> form.intField("detail.extension.measurements.value", measurement::getValueAsInt,
                                value -> measurement.setValue(DetailForm.mandatory(value)));
                        case STRING -> form.textField("detail.extension.measurements.value", measurement::getValueAsString,
                                measurement::setValue);
                        case null -> form.text("detail.extension.measurements.value", () -> null);
                    }
                    form.booleanField("detail.extension.measurements.valid", measurement::isValid, measurement::setValid);
                });
    }

    private static void addDiscreteMeasurement(DiscreteMeasurements<?> extension, DiscreteKind kind) {
        DiscreteMeasurementAdder adder = extension.newDiscreteMeasurement()
                .setId(extension.getExtendable().getId() + "_" + (kind.tapChanger() == null ? kind.type() : kind.tapChanger()))
                .setEnsureIdUnicity(true)
                .setType(kind.type())
                .setTapChanger(kind.tapChanger())
                .setValid(false);
        switch (kind.type()) {
            case SWITCH_POSITION -> adder.setValue(false);
            case TAP_POSITION, SHUNT_COMPENSATOR_SECTION -> adder.setValue(0);
            case OTHER -> adder.setValue("");
        }
        adder.add();
    }

    /**
     * Pilot point targets and control unit participations are editable, zones aren't added nor removed: not offered
     * in the add menu.
     */
    public static ExtensionsPane.Editor<Network> secondaryVoltageControl() {
        return ExtensionsPane.<Network, SecondaryVoltageControl>editor(SecondaryVoltageControl.class, "detail.extension.secondaryVoltageControl", null,
                (extension, form) -> extension.getControlZones().forEach(zone -> {
                    PilotPoint pilotPoint = zone.getPilotPoint();
                    form.section("detail.extension.secondaryVoltageControl.zone", zone.getName())
                            .text("detail.extension.secondaryVoltageControl.pilotPoint", () -> String.join(", ", pilotPoint.getBusbarSectionsOrBusesIds()))
                            .doubleField("detail.extension.secondaryVoltageControl.targetV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                                    pilotPoint::getTargetV, pilotPoint::setTargetV);
                    zone.getControlUnits().forEach(unit -> form
                            .text("detail.extension.secondaryVoltageControl.controlUnit", unit::getId)
                            .booleanField("detail.extension.activePowerControl.participate", unit::isParticipate, unit::setParticipate));
                }));
    }

    /**
     * Coupling impedances are editable, couplings aren't added nor removed: not offered in the add menu.
     */
    public static ExtensionsPane.Editor<Network> lineCouplings() {
        return ExtensionsPane.<Network, LineCouplings>editor(LineCouplings.class, "detail.extension.lineCouplings", null,
                (extension, form) -> extension.getMutualCouplings().forEach(coupling -> {
                    form.section(null)
                            .text("detail.extension.lineCouplings.line1", () -> coupling.getLine1().getId() + " " + coupling.getLine1Segment())
                            .text("detail.extension.lineCouplings.line2", () -> coupling.getLine2().getId() + " " + coupling.getLine2Segment())
                            .doubleField("desktop.common.column.r", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, coupling::getR, coupling::setR)
                            .doubleField("desktop.common.column.x", DetailForm.OHM, GuiParameters.Quantity.IMPEDANCE, coupling::getX, coupling::setX);
                }));
    }

    public static ExtensionsPane.Editor<Substation> entsoeArea() {
        return ExtensionsPane.<Substation, EntsoeArea>editor(EntsoeArea.class, "detail.extension.entsoeArea",
                substation -> substation.newExtension(EntsoeAreaAdder.class)
                        .withCode(substation.getCountry().flatMap(country -> EntsoeGeographicalCode.forCountry(country).stream().findFirst())
                                .orElse(EntsoeGeographicalCode.values()[0]))
                        .add(),
                (extension, form) -> form
                        .choiceField("detail.extension.entsoeCategory.code", DetailSections.values(EntsoeGeographicalCode.class),
                                DetailSections.enumFormat(), extension::getCode, extension::setCode));
    }

    /**
     * Its coordinates are immutable: edited in a {@link CoordinatesPane} replacing the extension. Added as a straight
     * route between the two ends' substations when they have positions, empty otherwise.
     */
    @SuppressWarnings("unchecked")
    public static <I extends Identifiable<I>> ExtensionsPane.Editor<I> linePosition() {
        return ExtensionsPane.<I, LinePosition<I>>editor(LinePosition.class, "detail.extension.linePosition",
                line -> line.newExtension(LinePositionAdder.class).withCoordinates(PositionEdits.straightRoute(line).orElse(List.of())).add(),
                (extension, form) -> {
                    CoordinatesPane pane = new CoordinatesPane(extension.getExtendable(), form);
                    form.node("detail.extension.linePosition.coordinates", pane, null, pane::refresh);
                });
    }

    /**
     * Read-only, and not offered in the add menu: it designates terminals, usually set by a load flow.
     */
    public static ExtensionsPane.Editor<Network> referenceTerminals() {
        return ExtensionsPane.<Network, ReferenceTerminals>editor(ReferenceTerminals.class, "detail.extension.referenceTerminals", null,
                (extension, form) -> form.text("detail.extension.terminals", () -> terminals(extension.getReferenceTerminals())));
    }

    /**
     * Read-only, and not offered in the add menu: computed per topology view by a state estimator.
     */
    public static ExtensionsPane.Editor<VoltageLevel> observabilityArea() {
        return ExtensionsPane.<VoltageLevel, ObservabilityArea>editor(ObservabilityArea.class, "detail.extension.observabilityArea", null,
                (extension, form) -> {
                    form.text("detail.extension.observabilityArea.consistent", () -> String.valueOf(extension.isConsistentWithTopology()));
                    extension.getObservabilityAreas().stream()
                            .sorted(Comparator.comparingInt(ObservabilityArea.AreaCharacteristics::getAreaNumber))
                            .forEach(area -> form.section("detail.extension.observabilityArea.area", area.getAreaNumber())
                                    .text("desktop.common.column.status", () -> String.valueOf(area.getStatus()))
                                    .text("detail.extension.terminals", () -> terminals(area.getTerminals())));
                });
    }

    /**
     * Read-only, and not offered in the add menu, as are the other CGMES extensions: they keep the CGMES identifiers
     * and metadata a CGMES export writes back, which editing would make inconsistent.
     */
    public static ExtensionsPane.Editor<Network> cgmesMetadataModels() {
        return ExtensionsPane.<Network, CgmesMetadataModels>editor(CgmesMetadataModels.class, "detail.extension.cgmesMetadataModels", null,
                (extension, form) -> extension.getSortedModels().forEach(model -> form.section(null)
                        .text("detail.extension.cgmesMetadataModels.subset", () -> String.valueOf(model.getSubset()))
                        .text("desktop.common.column.id", model::getId)
                        .text("detail.extension.cgmesMetadataModels.description", model::getDescription)
                        .text("detail.extension.cgmesMetadataModels.version", () -> String.valueOf(model.getVersion()))
                        .text("detail.extension.cgmesMetadataModels.modelingAuthoritySet", model::getModelingAuthoritySet)
                        .text("detail.extension.cgmesMetadataModels.profiles", () -> String.join("\n", model.getProfiles()))
                        .text("detail.extension.cgmesMetadataModels.dependentOn", () -> String.join("\n", model.getDependentOn()))
                        .text("detail.extension.cgmesMetadataModels.supersedes", () -> String.join("\n", model.getSupersedes()))));
    }

    public static ExtensionsPane.Editor<Network> cimCharacteristics() {
        return ExtensionsPane.<Network, CimCharacteristics>editor(CimCharacteristics.class, "detail.extension.cimCharacteristics", null,
                (extension, form) -> form
                        .text("detail.extension.cimCharacteristics.topologyKind", () -> String.valueOf(extension.getTopologyKind()))
                        .text("detail.extension.cimCharacteristics.cimVersion", () -> String.valueOf(extension.getCimVersion())));
    }

    public static ExtensionsPane.Editor<Network> baseVoltageMapping() {
        return ExtensionsPane.<Network, BaseVoltageMapping>editor(BaseVoltageMapping.class, "detail.extension.baseVoltageMapping", null,
                (extension, form) -> form.text("detail.extension.baseVoltageMapping.baseVoltages", () -> lines(
                        extension.baseVoltagesByNominalVoltageMap().values().stream()
                                .map(source -> source.getNominalV() + " " + DetailForm.KV + ": " + source.getId() + " (" + source.getSource() + ")"),
                        extension.getBaseVoltages().size())));
    }

    @SuppressWarnings("unchecked")
    public static <C extends Connectable<C>> ExtensionsPane.Editor<C> cgmesTapChangers() {
        return ExtensionsPane.<C, CgmesTapChangers<C>>editor(CgmesTapChangers.class, "detail.extension.cgmesTapChangers", null,
                (extension, form) -> extension.getTapChangers().stream()
                        .sorted(Comparator.comparing(CgmesTapChanger::getId))
                        .forEach(tapChanger -> form.section(null)
                                .text("desktop.common.column.id", tapChanger::getId)
                                .text("desktop.common.column.type", tapChanger::getType)
                                .text("detail.extension.cgmesTapChangers.combinedTapChangerId", tapChanger::getCombinedTapChangerId)
                                .text("detail.extension.cgmesTapChangers.hidden", () -> String.valueOf(tapChanger.isHidden()))
                                .text("detail.extension.cgmesTapChangers.step", () -> tapChanger.getStep().isPresent()
                                        ? String.valueOf(tapChanger.getStep().getAsInt()) : null)
                                .text("detail.extension.cgmesTapChangers.controlId", tapChanger::getControlId)));
    }

    public static ExtensionsPane.Editor<TieLine> cgmesLineBoundaryNode() {
        return ExtensionsPane.<TieLine, CgmesLineBoundaryNode>editor(CgmesLineBoundaryNode.class, "detail.extension.cgmesBoundaryNode", null,
                (extension, form) -> form
                        .text("detail.extension.cgmesBoundaryNode.hvdc", () -> String.valueOf(extension.isHvdc()))
                        .text("detail.extension.cgmesBoundaryNode.eic", () -> extension.getLineEnergyIdentificationCodeEic().orElse(null)));
    }

    public static ExtensionsPane.Editor<BoundaryLine> cgmesBoundaryLineBoundaryNode() {
        return ExtensionsPane.<BoundaryLine, CgmesBoundaryLineBoundaryNode>editor(CgmesBoundaryLineBoundaryNode.class,
                "detail.extension.cgmesBoundaryNode", null,
                (extension, form) -> form
                        .text("detail.extension.cgmesBoundaryNode.hvdc", () -> String.valueOf(extension.isHvdc()))
                        .text("detail.extension.cgmesBoundaryNode.eic", () -> extension.getLineEnergyIdentificationCodeEic().orElse(null)));
    }

    private static String terminals(Collection<Terminal> terminals) {
        return lines(terminals.stream().map(TerminalPickerDialog::terminalLabel).sorted(), terminals.size());
    }

    // one per line, a long list cut short for the form to stay readable
    private static String lines(Stream<String> items, int count) {
        String shown = items.limit(MAX_LINES).collect(Collectors.joining("\n"));
        return count > MAX_LINES ? shown + "\n… (" + count + ")" : shown;
    }

    /**
     * Read-only, and not offered in the add menu: it designates a terminal.
     */
    public static ExtensionsPane.Editor<VoltageLevel> slackTerminal() {
        return ExtensionsPane.<VoltageLevel, SlackTerminal>editor(SlackTerminal.class, "detail.extension.slackTerminal", null,
                (extension, form) -> form.text("detail.extension.slackTerminal.terminal",
                        () -> extension.getTerminal() == null ? null : TerminalPickerDialog.terminalLabel(extension.getTerminal())));
    }
}
