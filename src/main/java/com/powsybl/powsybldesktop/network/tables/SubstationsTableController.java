/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.SubstationNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all substations in the network. Clicking the name navigates to it in the substations (single-line
 * diagram) view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SubstationsTableController extends AbstractDisposableController {

    private static final List<Country> COUNTRY_CHOICES = Stream.concat(Stream.of((Country) null), Arrays.stream(Country.values())).toList();

    private static final StringConverter<Country> COUNTRY_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Country country) {
            return country == null ? "-" : country.name();
        }

        @Override
        public Country fromString(String text) {
            return "-".equals(text) ? null : Country.valueOf(text);
        }
    };

    @FXML
    public TableView<Substation> substationsTableView;
    @FXML
    private SearchBoxController searchBoxController;

    @FXML
    TableColumn<Substation, Substation> nameColumn;
    @FXML
    TableColumn<Substation, Country> countryColumn;
    @FXML
    TableColumn<Substation, String> tsoColumn;
    @FXML
    TableColumn<Substation, Substation> geographicalTagsColumn;

    private final ObservableList<Substation> substationsData = FXCollections.observableArrayList();

    private MainModel mainModel;
    List<Substation> currentSubstations = List.of();

    @FXML
    private void initialize() {
        substationsTableView.getSelectionModel().setCellSelectionEnabled(true);

        SortedList<Substation> sortedSubstations = new SortedList<>(substationsData);
        sortedSubstations.comparatorProperty().bind(substationsTableView.comparatorProperty());
        substationsTableView.setItems(sortedSubstations);
        TableAutoFitLimiter.install(substationsTableView);

        TableColumnSupport.configureContainerColumn(nameColumn, substation -> Optional.of(substation), this::containerLink);

        TableColumnSupport.configureChoiceColumn(countryColumn, Substation::getNullableCountry, Substation::setCountry, COUNTRY_CHOICES, COUNTRY_FORMAT);

        tsoColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getTso()));
        TableColumnSupport.configureEditableTextColumn(tsoColumn);
        tsoColumn.setOnEditCommit(event -> event.getRowValue().setTso(event.getNewValue()));

        geographicalTagsColumn.setSortable(false);
        geographicalTagsColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        geographicalTagsColumn.setCellFactory(col -> new GeographicalTagsCell());
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        updateSubstations();
        listenerManager.listen(this.mainModel.networkProperty(), (observable, oldValue, newValue) -> updateSubstations());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateSubstations());
        searchBoxController.bind(mainModel, this::onSearchMatch);
    }

    @Override
    public void dispose() {
        searchBoxController.dispose();
        super.dispose();
    }

    private void updateSubstations() {
        Network network = mainModel.getNetwork();
        currentSubstations = network == null ? List.of() : network.getSubstationStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        substationsData.setAll(currentSubstations);
    }

    // this table is always standalone (never embedded elsewhere), so its own row selection is always
    // worth recording in history before navigating away - unlike AbstractEquipmentTableController.containerCell
    private Hyperlink containerLink(Container<?> container, Substation item) {
        Hyperlink link = new Hyperlink(container.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            mainModel.addNavigationEvent(NavigationEvent.create(
                    NavigationType.NETWORK_TABLE_SUBSTATIONS, SubstationNavigationState.create(item)), false);
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(container)));
        });
        return link;
    }

    // Any match (a substation itself, or any equipment/container found inside one) is revealed here as its
    // owning substation's row - this table has nothing finer-grained to select.
    private void onSearchMatch(Identifiable<?> match) {
        Container<?> container = NetworkSearch.containerOf(match);
        Substation substation = container instanceof Substation s ? s
                : container instanceof VoltageLevel voltageLevel ? voltageLevel.getSubstation().orElse(null) : null;
        selectInTable(substation);
    }

    public void goToSubstation(Substation substation) {
        selectInTable(substation);
    }

    private void selectInTable(Substation substation) {
        int row = substation == null ? -1 : substationsTableView.getItems().indexOf(substation);
        if (row < 0) {
            substationsTableView.getSelectionModel().clearSelection();
        } else {
            substationsTableView.getSelectionModel().clearAndSelect(row, nameColumn);
            substationsTableView.scrollTo(row);
        }
    }

    // Displays a substation's geographical tags concatenated with ";", with a pencil button opening
    // GeographicalTagsDialog to add more (see that class for why removal isn't offered).
    private static final class GeographicalTagsCell extends TableCell<Substation, Substation> {
        private final Label tagsLabel = new Label();
        private final Button editButton = new Button();
        private final HBox box = new HBox(6, editButton, tagsLabel);

        GeographicalTagsCell() {
            editButton.setGraphic(new FontIcon("mdi2p-pencil"));
            editButton.getStyleClass().add("icon-button");
            editButton.setTooltip(new Tooltip(Messages.get("substations.geographicalTags.editTooltip")));
            editButton.setOnAction(event -> {
                Substation substation = getItem();
                if (substation != null) {
                    GeographicalTagsDialog.show(box.getScene().getWindow(), substation);
                    tagsLabel.setText(String.join(";", substation.getGeographicalTags()));
                }
            });
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Substation substation, boolean empty) {
            super.updateItem(substation, empty);
            if (empty || substation == null) {
                setGraphic(null);
            } else {
                tagsLabel.setText(String.join(";", substation.getGeographicalTags()));
                setGraphic(box);
            }
        }
    }
}
