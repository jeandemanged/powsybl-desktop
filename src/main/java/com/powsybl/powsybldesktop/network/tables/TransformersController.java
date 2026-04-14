/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.TransformerNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all two- and three-windings transformers in the network, one row per transformer. A
 * transformer belongs to a single substation (IIDM requires it) shown as a leftmost column, unlike lines
 * which can span two substations. VoltageLevel/RatedU/RatedS/Connected/CC/SC/P/Q/I are per-side (RatedS has a
 * single value for two-windings transformers), so each of those columns stacks 2 (two-windings) or 3
 * (three-windings) values in a single cell. RatedU and RatedS are editable in place, NaN shown as "-". The ratio
 * and phase tap changer columns stack one spinner per side too, but unlike RatedU/RatedS a side can simply lack
 * that tap changer type - its slot is left blank - and a two-windings transformer's single tap changer (not
 * per-side) is wrapped in a one-element list rather than stacking two. Clicking a substation or voltage level
 * navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TransformersController extends AbstractEquipmentTableController<Identifiable<?>> {

    private static final StringConverter<RatioTapChanger.RegulationMode> RATIO_REGULATION_MODE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(RatioTapChanger.RegulationMode value) {
            return value == null ? "" : ratioRegulationModeLabel(value);
        }

        @Override
        public RatioTapChanger.RegulationMode fromString(String text) {
            return Arrays.stream(RatioTapChanger.RegulationMode.values())
                    .filter(mode -> toString(mode).equals(text))
                    .findFirst().orElseThrow();
        }
    };

    private static final StringConverter<PhaseTapChanger.RegulationMode> PHASE_REGULATION_MODE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(PhaseTapChanger.RegulationMode value) {
            return value == null ? "" : phaseRegulationModeLabel(value);
        }

        @Override
        public PhaseTapChanger.RegulationMode fromString(String text) {
            return Arrays.stream(PhaseTapChanger.RegulationMode.values())
                    .filter(mode -> toString(mode).equals(text))
                    .findFirst().orElseThrow();
        }
    };

    @FXML
    public TableView<Identifiable<?>> transformersTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> substationColumn;
    @FXML
    TableColumn<Identifiable<?>, String> nameColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> voltageLevelColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratedUColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratedSColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> connectedColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> connectedComponentColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> synchronousComponentColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> rColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> xColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> gColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> bColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerRegulatingColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerRegulationModeColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerRegulationValueColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerTargetDeadbandColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> ratioTapChangerSolvedTapColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerRegulatingColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerRegulationModeColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerRegulationValueColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerTargetDeadbandColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> phaseTapChangerSolvedTapColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> pColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> qColumn;
    @FXML
    TableColumn<Identifiable<?>, Identifiable<?>> iColumn;
    @FXML
    TableColumn<Identifiable<?>, Boolean> patlIViolationColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn, TransformersController::substationOf, this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));

        TableColumnSupport.configureMultiSidedContainerColumn(voltageLevelColumn,
                transformer -> terminalsOf(transformer).stream().<Container<?>>map(Terminal::getVoltageLevel).toList(), this::containerCell);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(ratedUColumn, TransformersController::ratedUsOf, TransformersController::setRatedU);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(ratedSColumn, TransformersController::ratedSsOf, TransformersController::setRatedS);
        TableColumnSupport.configureMultiSidedConnectedColumn(connectedColumn,
                TransformersController::terminalsOf, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureMultiSidedComponentColumn(connectedComponentColumn,
                transformer -> terminalsOf(transformer).stream().map(terminal -> terminal.getBusView().getBus()).toList(), Bus::getConnectedComponent);
        TableColumnSupport.configureMultiSidedComponentColumn(synchronousComponentColumn,
                transformer -> terminalsOf(transformer).stream().map(terminal -> terminal.getBusView().getBus()).toList(), Bus::getSynchronousComponent);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(rColumn, TransformersController::rsOf, TransformersController::setR, 2);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(xColumn, TransformersController::xsOf, TransformersController::setX, 2);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(gColumn, TransformersController::gsOf, TransformersController::setG, 6);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(bColumn, TransformersController::bsOf, TransformersController::setB, 6);
        TableColumnSupport.configureMultiSidedTapChangerColumn(ratioTapChangerColumn, Identifiable::getNameOrId, TransformersController::ratioTapChangersOf);
        TableColumnSupport.configureMultiSidedTapChangerBooleanColumn(ratioTapChangerRegulatingColumn, TransformersController::ratioTapChangersOf,
                tapChanger -> tapChanger.isRegulating(), (tapChanger, regulating) -> tapChanger.setRegulating(regulating));
        TableColumnSupport.configureMultiSidedTapChangerChoiceColumn(ratioTapChangerRegulationModeColumn, TransformersController::ratioTapChangersOf,
                tapChanger -> ((RatioTapChanger) tapChanger).getRegulationMode(),
                (tapChanger, mode) -> ((RatioTapChanger) tapChanger).setRegulationMode(mode),
                List.of(RatioTapChanger.RegulationMode.values()), RATIO_REGULATION_MODE_FORMAT);
        TableColumnSupport.configureMultiSidedTapChangerDoubleColumn(ratioTapChangerRegulationValueColumn, TransformersController::ratioTapChangersOf,
                tapChanger -> ((RatioTapChanger) tapChanger).getRegulationValue(),
                (tapChanger, value) -> ((RatioTapChanger) tapChanger).setRegulationValue(value));
        TableColumnSupport.configureMultiSidedTapChangerDoubleColumn(ratioTapChangerTargetDeadbandColumn, TransformersController::ratioTapChangersOf,
                tapChanger -> tapChanger.getTargetDeadband(), (tapChanger, value) -> tapChanger.setTargetDeadband(value));
        TableColumnSupport.configureMultiSidedTapChangerNullableIntColumn(ratioTapChangerSolvedTapColumn, TransformersController::ratioTapChangersOf,
                tapChanger -> tapChanger.getSolvedTapPosition());

        TableColumnSupport.configureMultiSidedTapChangerColumn(phaseTapChangerColumn, Identifiable::getNameOrId, TransformersController::phaseTapChangersOf);
        TableColumnSupport.configureMultiSidedTapChangerBooleanColumn(phaseTapChangerRegulatingColumn, TransformersController::phaseTapChangersOf,
                tapChanger -> tapChanger.isRegulating(), (tapChanger, regulating) -> tapChanger.setRegulating(regulating));
        TableColumnSupport.configureMultiSidedTapChangerChoiceColumn(phaseTapChangerRegulationModeColumn, TransformersController::phaseTapChangersOf,
                tapChanger -> ((PhaseTapChanger) tapChanger).getRegulationMode(),
                (tapChanger, mode) -> ((PhaseTapChanger) tapChanger).setRegulationMode(mode),
                List.of(PhaseTapChanger.RegulationMode.values()), PHASE_REGULATION_MODE_FORMAT);
        TableColumnSupport.configureMultiSidedTapChangerDoubleColumn(phaseTapChangerRegulationValueColumn, TransformersController::phaseTapChangersOf,
                tapChanger -> ((PhaseTapChanger) tapChanger).getRegulationValue(),
                (tapChanger, value) -> ((PhaseTapChanger) tapChanger).setRegulationValue(value));
        TableColumnSupport.configureMultiSidedTapChangerDoubleColumn(phaseTapChangerTargetDeadbandColumn, TransformersController::phaseTapChangersOf,
                tapChanger -> tapChanger.getTargetDeadband(), (tapChanger, value) -> tapChanger.setTargetDeadband(value));
        TableColumnSupport.configureMultiSidedTapChangerNullableIntColumn(phaseTapChangerSolvedTapColumn, TransformersController::phaseTapChangersOf,
                tapChanger -> tapChanger.getSolvedTapPosition());
        TableColumnSupport.configureMultiSidedDoubleColumn(pColumn,
                transformer -> terminalsOf(transformer).stream().map(Terminal::getP).toList());
        TableColumnSupport.configureMultiSidedDoubleColumn(qColumn,
                transformer -> terminalsOf(transformer).stream().map(Terminal::getQ).toList());
        TableColumnSupport.configureMultiSidedDoubleColumn(iColumn,
                transformer -> terminalsOf(transformer).stream().map(Terminal::getI).toList());
        TableColumnSupport.configureOverloadColumn(patlIViolationColumn, TransformersController::isOverloaded);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.parameters", false,
                        rColumn, xColumn, gColumn, bColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("transformers.columnGroup.ratioTapChanger", false,
                        ratioTapChangerColumn, ratioTapChangerRegulatingColumn, ratioTapChangerRegulationModeColumn,
                        ratioTapChangerRegulationValueColumn, ratioTapChangerTargetDeadbandColumn, ratioTapChangerSolvedTapColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("transformers.columnGroup.phaseTapChanger", false,
                        phaseTapChangerColumn, phaseTapChangerRegulatingColumn, phaseTapChangerRegulationModeColumn,
                        phaseTapChangerRegulationValueColumn, phaseTapChangerTargetDeadbandColumn, phaseTapChangerSolvedTapColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, patlIViolationColumn)));
    }

    private static List<Terminal> terminalsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getTerminal1(), t.getTerminal2());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getTerminal).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static List<Double> ratedUsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getRatedU1(), t.getRatedU2());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getRatedU).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setRatedU(Identifiable<?> transformer, int index, double ratedU) {
        if (transformer instanceof TwoWindingsTransformer t) {
            if (index == 0) {
                t.setRatedU1(ratedU);
            } else {
                t.setRatedU2(ratedU);
            }
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setRatedU(ratedU);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    // TwoWindingsTransformer has a single ratedS for the whole transformer, unlike ratedU which is per side -
    // wrapped in a one-element list so it still fits the multi-sided column shape.
    private static List<Double> ratedSsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getRatedS());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getRatedS).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setRatedS(Identifiable<?> transformer, int index, double ratedS) {
        if (transformer instanceof TwoWindingsTransformer t) {
            t.setRatedS(ratedS);
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setRatedS(ratedS);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    // Like ratedS, R/X/G/B are single values for the whole two-windings transformer (not per side) - wrapped
    // in a one-element list so they still fit the multi-sided column shape.
    private static List<Double> rsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getR());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getR).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setR(Identifiable<?> transformer, int index, double r) {
        if (transformer instanceof TwoWindingsTransformer t) {
            t.setR(r);
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setR(r);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static List<Double> xsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getX());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getX).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setX(Identifiable<?> transformer, int index, double x) {
        if (transformer instanceof TwoWindingsTransformer t) {
            t.setX(x);
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setX(x);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static List<Double> gsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getG());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getG).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setG(Identifiable<?> transformer, int index, double g) {
        if (transformer instanceof TwoWindingsTransformer t) {
            t.setG(g);
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setG(g);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static List<Double> bsOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return List.of(t.getB());
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream().map(ThreeWindingsTransformer.Leg::getB).toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static void setB(Identifiable<?> transformer, int index, double b) {
        if (transformer instanceof TwoWindingsTransformer t) {
            t.setB(b);
            return;
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            t.getLegs().get(index).setB(b);
            return;
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    // A TwoWindingsTransformer has a single ratio/phase tap changer for the whole equipment (not one per side),
    // unlike ratedU - wrapped in a one-element list so it still fits the multi-sided column shape.
    private static List<Optional<? extends TapChanger<?, ?, ?, ?>>> ratioTapChangersOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger = t.getOptionalRatioTapChanger();
            return List.of(tapChanger);
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream()
                    .<Optional<? extends TapChanger<?, ?, ?, ?>>>map(ThreeWindingsTransformer.Leg::getOptionalRatioTapChanger)
                    .toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static List<Optional<? extends TapChanger<?, ?, ?, ?>>> phaseTapChangersOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger = t.getOptionalPhaseTapChanger();
            return List.of(tapChanger);
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getLegs().stream()
                    .<Optional<? extends TapChanger<?, ?, ?, ?>>>map(ThreeWindingsTransformer.Leg::getOptionalPhaseTapChanger)
                    .toList();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static String ratioRegulationModeLabel(RatioTapChanger.RegulationMode mode) {
        return Messages.get(mode == RatioTapChanger.RegulationMode.VOLTAGE
                ? "transformers.tapChanger.regulationMode.voltage" : "transformers.tapChanger.regulationMode.reactivePower");
    }

    private static String phaseRegulationModeLabel(PhaseTapChanger.RegulationMode mode) {
        return Messages.get(mode == PhaseTapChanger.RegulationMode.CURRENT_LIMITER
                ? "transformers.tapChanger.regulationMode.currentLimiter" : "transformers.tapChanger.regulationMode.activePowerControl");
    }

    private static boolean isOverloaded(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return t.isOverloaded();
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.isOverloaded();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    private static Optional<Substation> substationOf(Identifiable<?> transformer) {
        if (transformer instanceof TwoWindingsTransformer t) {
            return t.getSubstation();
        }
        if (transformer instanceof ThreeWindingsTransformer t) {
            return t.getSubstation();
        }
        throw new IllegalArgumentException("Unsupported transformer type: " + transformer.getClass());
    }

    @Override
    TableView<Identifiable<?>> tableView() {
        return transformersTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.TRANSFORMER);
    }

    @Override
    Stream<Identifiable<?>> networkItems(Network network) {
        return Stream.concat(
                network.getTwoWindingsTransformerStream().map(transformer -> (Identifiable<?>) transformer),
                network.getThreeWindingsTransformerStream().map(transformer -> (Identifiable<?>) transformer));
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Identifiable<?> transformer) {
        return terminalsOf(transformer).stream().map(Terminal::getVoltageLevel).toList();
    }

    @Override
    Optional<Identifiable<?>> asOwnEntity(Identifiable<?> match) {
        return match instanceof TwoWindingsTransformer || match instanceof ThreeWindingsTransformer ? Optional.of(match) : Optional.empty();
    }

    @Override
    TableColumn<Identifiable<?>, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Identifiable<?>, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Identifiable<?>, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    // a transformer's other leg/side can be a different voltage level of the same substation - keep its
    // link clickable even when the near side is the container we're already positioned on
    @Override
    protected boolean disableContainerLinks() {
        return false;
    }

    public void goToTransformer(Identifiable<?> transformer) {
        goToItem(transformer);
    }

    @Override
    NavigationEvent ownNavigationEvent(Identifiable<?> transformer) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_TRANSFORMERS, TransformerNavigationState.create(transformer));
    }
}
