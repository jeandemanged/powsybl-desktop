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
        return;
    }
    if (event.target.closest('.sld-breaker')       ||
        event.target.closest('.sld-disconnector')  ||
        event.target.closest('.sld-load-break-switch')) {
        controller.onSwitchClick(event.target.parentElement.id);
        return;
    }
    // e.g. a transformer drawn inside the diagram: its node is one of the clicked element's ancestors
    for (var element = event.target; element; element = element.parentElement) {
        if (element.id && controller.onEquipmentClick(element.id)) {
            break;
        }
    }
}, false);

// called by ContainerDiagram on a right-click, the WebView not dispatching contextmenu events to the page (its own
// context menu is disabled): x, y in CSS pixels of the viewport
function equipmentContextMenuAt(x, y) {
    for (var element = document.elementFromPoint(x, y); element; element = element.parentElement) {
        if (element.id && controller.onEquipmentContextMenu(element.id)) {
            return;
        }
    }
    controller.onBackgroundContextMenu();
}
