/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

document.addEventListener('click', function (event) {
    if (event.target.closest('.sld-top-feeder')    ||
        event.target.closest('.sld-bottom-feeder')) {
        controller.onFeederTopBottomClick(event.target.parentElement.id);
    }
    if (event.target.closest('.sld-breaker')       ||
        event.target.closest('.sld-disconnector')  ||
        event.target.closest('.sld-load-break-switch')) {
        controller.onSwitchClick(event.target.parentElement.id);
    }
}, false);
