/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

// Edges are thin lines, hard to point at: a wider transparent copy of each, in its group, catches the pointer for it.
// Added again whenever ContainerDiagram injects a new diagram.
function widenEdges() {
    var paths = document.querySelectorAll('.nad-edge-path');
    for (var i = 0; i < paths.length; i++) {
        var hitArea = paths[i].cloneNode(false);
        hitArea.removeAttribute('class');
        hitArea.setAttribute('style', 'fill: none; stroke: transparent; stroke-width: 20px; pointer-events: stroke');
        paths[i].parentNode.insertBefore(hitArea, paths[i].nextSibling);
    }
}

document.addEventListener('DOMContentLoaded', function () {
    new MutationObserver(widenEdges).observe(document.getElementById('svgContainer'), {childList: true});
}, false);

// called by ContainerDiagram on a right-click, the WebView not dispatching contextmenu events to the page (its own
// context menu is disabled): x, y in CSS pixels of the viewport. A voltage level's label is HTML in a foreignObject,
// whose id is the label's.
function equipmentContextMenuAt(x, y) {
    for (var element = document.elementFromPoint(x, y); element; element = element.parentElement) {
        if (element.id && controller.onEquipmentContextMenu(element.id)) {
            return;
        }
    }
    controller.onBackgroundContextMenu();
}
