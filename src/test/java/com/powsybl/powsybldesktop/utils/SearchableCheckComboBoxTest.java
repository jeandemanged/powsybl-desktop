/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import com.powsybl.iidm.network.Country;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SearchableCheckComboBoxTest extends AbstractHeadlessApplicationTest {

    private SearchableCheckComboBox<Country> comboBox;

    @Override
    public void start(Stage stage) {
        comboBox = new SearchableCheckComboBox<>(List.of(Country.values()), Labels.countryFormat());
        comboBox.setTitle("None");
        stage.setScene(new Scene(new StackPane(comboBox), 300, 100));
        stage.show();
    }

    @Test
    void showsCheckedCodesInItemsOrderOrTitle() {
        assertEquals("None", comboBox.getText());
        interact(() -> comboBox.setChecked(List.of(Country.FR, Country.BE)));
        assertEquals(List.of(Country.BE, Country.FR), comboBox.getCheckedItems());
        assertEquals("BE, FR", comboBox.getText());
    }

    @Test
    void setCheckedIsASingleChange() {
        List<List<Country>> changes = new ArrayList<>();
        comboBox.getCheckedItems().addListener((ListChangeListener<Country>) change -> changes.add(List.copyOf(comboBox.getCheckedItems())));
        interact(() -> comboBox.setChecked(List.of(Country.FR, Country.BE)));
        interact(() -> comboBox.setChecked(List.of(Country.BE, Country.FR)));
        assertEquals(List.of(List.of(Country.BE, Country.FR)), changes);
    }

    @Test
    void searchFiltersOnCodeOrNameAndEnterChecksTheOnlyMatch() {
        interact(comboBox::show);
        TextField searchField = lookup(node -> node instanceof TextField).query();
        ListView<Country> listView = lookup(node -> node instanceof ListView).query();

        interact(() -> searchField.setText("germ"));
        assertEquals(List.of(Country.DE), listView.getItems());
        interact(() -> searchField.setText("fr france"));
        assertEquals(List.of(Country.FR), listView.getItems());
        interact(() -> searchField.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                false, false, false, false)));
        assertEquals(List.of(Country.FR), comboBox.getCheckedItems());
    }
}
