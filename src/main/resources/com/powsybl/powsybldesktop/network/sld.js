/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

// A busbar is a thin line, hard to point at: a wider transparent line over each, in its group, catches the pointer
// for it. Added again whenever ContainerDiagram injects a new diagram.
function widenBusbars() {
    var lines = document.querySelectorAll('.sld-busbar-section > line');
    for (var i = 0; i < lines.length; i++) {
        var hitArea = lines[i].cloneNode(false);
        hitArea.setAttribute('style', 'stroke: transparent; stroke-width: 12px; pointer-events: stroke');
        lines[i].parentNode.insertBefore(hitArea, lines[i].nextSibling);
    }
}

document.addEventListener('DOMContentLoaded', function () {
    new MutationObserver(widenBusbars).observe(document.getElementById('svgContainer'), {childList: true});
}, false);

// what a click on target does, or null if nothing: the same lookup sets the pointer cursor, so that it shows exactly
// over what can be clicked
function clickAction(target) {
    if (target.closest('.sld-top-feeder') || target.closest('.sld-bottom-feeder')) {
        return function () { controller.onFeederTopBottomClick(target.parentElement.id); };
    }
    if (target.closest('.sld-breaker') || target.closest('.sld-disconnector') || target.closest('.sld-load-break-switch')) {
        return function () { controller.onSwitchClick(target.parentElement.id); };
    }
    // e.g. a busbar or a transformer drawn inside the diagram: its node is one of the clicked element's ancestors
    for (var element = target; element; element = element.parentElement) {
        if (element.id && controller.isClickableEquipment(element.id)) {
            var id = element.id;
            return function () { controller.onEquipmentClick(id); };
        }
    }
    return null;
}

document.addEventListener('click', function (event) {
    var action = clickAction(event.target);
    if (action) {
        action();
    }
}, false);

document.addEventListener('mouseover', function (event) {
    document.body.style.cursor = clickAction(event.target) ? 'pointer' : '';
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
