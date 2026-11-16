// Layout fix: comment reaction chips overlapping following content.
//
// On the lightweight mobile Facebook layout, the small reaction summary chip
// of a comment (reaction icons + count, e.g. "49 👍") is absolutely
// positioned. When the comment container is taller/shorter than FB expects
// (larger system font, a "View previous replies" row below, nested replies)
// the chip no longer lines up with its own comment and is drawn on top of the
// next comment or the "Show previous replies" text.
//
// The fix is deliberately DOM-agnostic (FB class names are obfuscated and
// change): we look for small, absolutely positioned elements that consist of
// a short count text plus an icon, displaced from the normal flow, and only touch those that really overlap
// text/images that do not belong to the chip's own container. A chip that is
// moved back into normal flow takes up its own space, so it can no longer
// overlap anything by construction.
(function () {
    if (window.__layout_fix_done) return;
    window.__layout_fix_done = true;

    var COUNT_RE = /^\s*\d[\d.,\s]*\s*[^\s\d]{0,8}\.?\s*$/; // "49", "1,2 K", "1,2 хиљ."
    var timer = null;

    function isChipShape(el) {
        var txt = el.textContent || '';
        if (txt.length > 14) return false;
        if (el.children.length > 6) return false;
        if (!el.querySelector('img,svg,[role="img"],i')) return false;
        // count may be missing when the chip is icons-only; require either a
        // count or at least 1 icon and a tiny box (checked via rect below)
        return txt.trim() === '' || COUNT_RE.test(txt);
    }

    function displacedAncestor(el) {
        // The chip may be displaced by absolute positioning, a negative margin
        // or a transform, on itself or on a wrapper (<=2 levels up).
        var cur = el;
        for (var i = 0; i < 3 && cur && cur !== document.body; i++) {
            var cs = getComputedStyle(cur);
            if (cs.position === 'fixed' || cs.position === 'sticky') return null;
            if (cs.position === 'absolute' ||
                parseFloat(cs.marginTop) < 0 || parseFloat(cs.marginBottom) < 0 ||
                (cs.transform && cs.transform !== 'none')) return cur;
            cur = cur.parentElement;
        }
        return null;
    }

    function inChrome(el) {
        return !!el.closest('[role="banner"],[role="navigation"],[role="tablist"],header,nav');
    }

    function overlapsForeignContent(chip) {
        var r = chip.getBoundingClientRect();
        if (r.width < 6 || r.height < 6 || r.width > 220 || r.height > 60) return false;
        var own = chip.parentElement || chip;
        var pts = [
            [r.left + r.width * 0.2, r.top + r.height * 0.5],
            [r.left + r.width * 0.5, r.top + r.height * 0.5],
            [r.left + r.width * 0.8, r.top + r.height * 0.5],
            [r.left + r.width * 0.5, r.top + r.height * 0.15],
            [r.left + r.width * 0.5, r.top + r.height * 0.85]
        ];
        for (var i = 0; i < pts.length; i++) {
            var x = pts[i][0], y = pts[i][1];
            if (x < 0 || y < 0 || x > innerWidth || y > innerHeight) continue;
            var stack = document.elementsFromPoint(x, y);
            for (var k = 0; k < stack.length; k++) {
                var e = stack[k];
                if (e === chip || chip.contains(e) || e.contains(chip) || own.contains(e)) continue;
                if (e === document.body || e === document.documentElement) continue;
                // only content that actually paints something readable
                var hasText = (e.children.length === 0 && (e.textContent || '').trim().length > 0);
                if (hasText || e.tagName === 'IMG' || e.tagName === 'SVG') return true;
            }
        }
        return false;
    }

    function fix(chip) {
        chip.setAttribute('data-chip-fixed', '1');
        var s = chip.style;
        s.setProperty('position', 'relative', 'important');
        s.setProperty('top', 'auto', 'important');
        s.setProperty('bottom', 'auto', 'important');
        s.setProperty('left', 'auto', 'important');
        s.setProperty('right', 'auto', 'important');
        s.setProperty('transform', 'none', 'important');
        s.setProperty('margin-top', '2px', 'important');
        s.setProperty('margin-bottom', '4px', 'important');
        s.setProperty('display', 'flex', 'important');
        s.setProperty('align-items', 'center', 'important');
        s.setProperty('width', 'fit-content', 'important');
        console.log('LAYOUT_FIX:chip-fixed:' + (chip.textContent || '').trim().slice(0, 12));
    }

    function scan() {
        timer = null;
        if (/messenger\.com|\/messages/.test(location.href)) return;
        var band0 = -innerHeight, band1 = innerHeight * 2;
        var nodes = document.querySelectorAll('div,span');
        var budget = 400; // hard cap on candidates examined per pass
        for (var i = 0; i < nodes.length && budget > 0; i++) {
            var el = nodes[i];
            if (el.hasAttribute('data-chip-fixed')) continue;
            if ((el.textContent || '').length > 14) continue;      // cheap reject
            if (!el.firstElementChild && (el.textContent || '').trim() === '') continue;
            var r = el.getBoundingClientRect();
            if (r.bottom < band0 || r.top > band1 || r.width === 0) continue;
            budget--;
            if (!isChipShape(el)) continue;
            var abs = displacedAncestor(el);
            if (!abs || abs.hasAttribute('data-chip-fixed') || inChrome(abs)) continue;
            if (!overlapsForeignContent(abs)) continue;
            fix(abs);
        }
    }

    function schedule() {
        if (timer) return;
        timer = setTimeout(scan, 350);
    }

    window.addEventListener('scroll', schedule, { passive: true, capture: true });
    window.addEventListener('resize', schedule);
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    schedule();
})();
