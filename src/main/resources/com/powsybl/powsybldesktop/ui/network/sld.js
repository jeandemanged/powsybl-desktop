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

// Equipment outlined for a while (ContainerDiagram.highlight): a feeder followed from its other end's diagram, so
// that the user sees where they come from, or a search match. A feeder's whole cell, switches and wires down to the
// busbars, is outlined when it is the cell's only feeder, else just its symbol and label; any other node (busbar
// section, switch, transformer drawn inside) by itself. The fade is driven frame by frame, as the WebView doesn't
// reliably finish CSS transitions. Gone with the diagram when another one is injected.
var HIGHLIGHT_MS = 4000;
var HIGHLIGHT_FADE_MS = 1000;
var HIGHLIGHT_PADDING = 6;

// called by ContainerDiagram with the SVG ids of the nodes to outline
function highlightNodes() {
    var outlines = [];
    for (var i = 0; i < arguments.length; i++) {
        var node = document.getElementById(arguments[i]);
        if (!node) {
            continue;
        }
        var feeder = node.classList.contains('sld-top-feeder') || node.classList.contains('sld-bottom-feeder');
        var cell = feeder ? node.closest('.sld-extern-cell') : null;
        var target = cell && cell.querySelectorAll('.sld-top-feeder, .sld-bottom-feeder').length === 1 ? cell : node;
        // in the target's own coordinates, where the outline is added
        var box = target.getBBox();
        var outline = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
        outline.setAttribute('class', 'sld-highlight');
        outline.setAttribute('x', box.x - HIGHLIGHT_PADDING);
        outline.setAttribute('y', box.y - HIGHLIGHT_PADDING);
        outline.setAttribute('width', box.width + 2 * HIGHLIGHT_PADDING);
        outline.setAttribute('height', box.height + 2 * HIGHLIGHT_PADDING);
        outline.setAttribute('rx', HIGHLIGHT_PADDING);
        // inline, over the diagram's own stylesheet; clicks and hover go through to what it outlines
        outline.setAttribute('style', 'fill: rgba(46, 125, 50, 0.1); stroke: #2e7d32; stroke-width: 3px; pointer-events: none');
        target.appendChild(outline);
        outlines.push(outline);
    }
    if (outlines.length > 0) {
        setTimeout(function () {
            var fadeStart = Date.now();
            var fade = function () {
                var opacity = 1 - (Date.now() - fadeStart) / HIGHLIGHT_FADE_MS;
                outlines.forEach(function (outline) {
                    if (opacity > 0) {
                        outline.style.opacity = opacity;
                    } else {
                        outline.remove();
                    }
                });
                if (opacity > 0) {
                    requestAnimationFrame(fade);
                }
            };
            requestAnimationFrame(fade);
        }, HIGHLIGHT_MS - HIGHLIGHT_FADE_MS);
    }
}
