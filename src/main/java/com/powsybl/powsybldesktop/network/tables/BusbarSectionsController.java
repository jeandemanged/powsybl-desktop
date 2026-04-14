/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.BusbarSectionNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all busbar sections in the network, with their substation and voltage level shown as leftmost
 * columns (substation is optional: a voltage level need not belong to one). Clicking the substation or voltage
 * level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BusbarSectionsController extends AbstractEquipmentTableController<BusbarSection> {

    @FXML
    public TableView<BusbarSection> busbarSectionsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<BusbarSection, Country> countryColumn;
    @FXML
    TableColumn<BusbarSection, BusbarSection> substationColumn;
    @FXML
    TableColumn<BusbarSection, BusbarSection> voltageLevelColumn;
    @FXML
    TableColumn<BusbarSection, String> nameColumn;
    @FXML
    TableColumn<BusbarSection, Double> vColumn;
    @FXML
    TableColumn<BusbarSection, Double> angleColumn;
    @FXML
    TableColumn<BusbarSection, VoltageViolation> voltageViolationColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureNullableColumn(countryColumn,
                busbarSection -> busbarSection.getTerminal().getVoltageLevel().getSubstation()
                        .map(Substation::getNullableCountry).orElse(null));
        TableColumnSupport.configureContainerColumn(substationColumn,
                busbarSection -> busbarSection.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                busbarSection -> Optional.of(busbarSection.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureNullableDoubleColumn(vColumn, BusbarSection::getV);
        TableColumnSupport.configureNullableDoubleColumn(angleColumn, BusbarSection::getAngle);
        TableColumnSupport.configureVoltageViolationColumn(voltageViolationColumn,
                BusbarSection::getV, busbarSection -> busbarSection.getTerminal().getVoltageLevel());

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        vColumn, angleColumn, voltageViolationColumn)));
    }

    @Override
    TableView<BusbarSection> tableView() {
        return busbarSectionsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.BUSBAR_SECTION);
    }

    @Override
    Stream<BusbarSection> networkItems(Network network) {
        return network.getBusbarSectionStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(BusbarSection busbarSection) {
        return List.of(busbarSection.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<BusbarSection> asOwnEntity(Identifiable<?> match) {
        return match instanceof BusbarSection busbarSection ? Optional.of(busbarSection) : Optional.empty();
    }

    @Override
    TableColumn<BusbarSection, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<BusbarSection, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<BusbarSection, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToBusbarSection(BusbarSection busbarSection) {
        goToItem(busbarSection);
    }

    @Override
    NavigationEvent ownNavigationEvent(BusbarSection busbarSection) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS, BusbarSectionNavigationState.create(busbarSection));
    }
}
