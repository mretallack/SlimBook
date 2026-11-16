// Unread Messenger count watcher.
//
// Reads the unread-messages indicator from the page header and reports it to
// the app (Android.reportMessageCount) so the native Messenger button can show
// a badge. The header indicator only changes when the page is re-fetched (a
// pull-to-refresh), so while the feed is open this also re-fetches the home
// page now and then and reads the count from that HTML, without touching what
// is on screen.
//
// Everything is best-effort: Facebook's markup is obfuscated and changes, so
// several strategies are tried and, if none finds the indicator, nothing is
// reported (the badge keeps its last value instead of showing a wrong 0).
(function () {
    if (window.__unread_watch_done) return;
    window.__unread_watch_done = true;

    var CHECK_EVERY_MS = 90000;
    var last = null;

    function digits(t) {
        var m = (t || '').match(/\d+/);
        return m ? parseInt(m[0], 10) : 0;
    }

    // Returns the unread count, 0 when the indicator exists without a number,
    // or -1 when the page has no recognisable Messenger indicator at all.
    function read(root) {
        var el = root.querySelector(
            '#messages_jewel [data-sigil="count"], #messages_jewel .jewelCount, ' +
            '[data-sigil*="messages_jewel"] [data-sigil="count"]');
        if (el) return digits(el.textContent);
        var jewel = root.querySelector('#messages_jewel, [data-sigil*="messages_jewel"]');
        if (jewel) return digits(jewel.getAttribute('aria-label') || '');
        // header link to the inbox with a count element inside
        var links = root.querySelectorAll('a[href*="/messages"]:not([href*="/messages/t/"]):not([href*="/messages/read"])');
        for (var i = 0; i < links.length; i++) {
            var c = links[i].querySelector('strong, [data-sigil="count"], span[class*="count" i]');
            if (c && /\d/.test(c.textContent || '')) return digits(c.textContent);
            var al = links[i].getAttribute('aria-label') || '';
            if (/\d/.test(al)) return digits(al);
        }
        return -1;
    }

    function report(n) {
        if (n < 0 || n === last) return;
        last = n;
        try { Android.reportMessageCount(String(n)); } catch (e) { /* bridge missing */ }
    }

    var timer = null;
    function schedule() {
        if (timer) return;
        timer = setTimeout(function () { timer = null; report(read(document)); }, 600);
    }

    function refetch() {
        if (document.visibilityState !== 'visible') return;
        try { if (!Android.isLiveMessageCheckEnabled()) return; } catch (e) { return; }
        // only on the feed, never while typing
        var p = location.pathname;
        if (!(p === '/' || p === '/home.php' || p.indexOf('/home') === 0)) return;
        var a = document.activeElement;
        if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.isContentEditable)) return;
        fetch(location.origin + '/home.php', { credentials: 'include', cache: 'no-store' })
            .then(function (r) { return r.ok ? r.text() : ''; })
            .then(function (html) {
                if (!html) return;
                var doc = new DOMParser().parseFromString(html, 'text/html');
                report(read(doc));
            })
            .catch(function () { /* offline: keep last value */ });
    }

    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    schedule();
    setInterval(refetch, CHECK_EVERY_MS);
})();
