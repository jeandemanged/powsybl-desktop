/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import javafx.event.ActionEvent;

import java.util.function.Consumer;

/**
 * A notification's action link, kept as a bundle key rather than a resolved label so it renders
 * against whichever language is current at the time {@link NotificationView} builds it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record NotificationAction(String messageKey, Consumer<ActionEvent> handler) {
}
