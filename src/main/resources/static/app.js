/* Snip frontend — talks to the Spring Boot API on the same origin. */
(function () {
  "use strict";

  var API = "/api/v1/urls";
  var LS_KEY = "snip_recent_v1";

  var $ = function (id) { return document.getElementById(id); };
  var urlInput = $("url-input"), shortenBtn = $("shorten-btn"),
      aliasToggle = $("alias-toggle"), aliasRow = $("alias-row"), aliasInput = $("alias-input"),
      statusBox = $("shorten-status"), resultCard = $("result-card"),
      resultLink = $("result-link"), copyBtn = $("copy-btn"),
      resultOriginal = $("result-original"), resultStatsBtn = $("result-stats-btn"),
      resultClicks = $("result-clicks"), healthPill = $("health-pill"),
      healthText = $("health-text"), recentSection = $("recent-section"),
      recentList = $("recent-list"), statLinks = $("stat-links");

  $("alias-prefix").textContent = location.host + "/";

  /* ---------- helpers ---------- */

  function showStatus(kind, html) {
    statusBox.className = "status status-" + kind;
    statusBox.innerHTML = html;
  }
  function hideStatus() { statusBox.className = "status hidden"; statusBox.innerHTML = ""; }

  function spinnerHtml(text) {
    return '<span class="spinner"></span><span>' + text + "</span>";
  }

  // fetch with a hard timeout; rejects on timeout or network error
  function fetchTimeout(url, opts, ms) {
    opts = opts || {};
    var ctrl = new AbortController();
    var t = setTimeout(function () { ctrl.abort(); }, ms || 12000);
    opts.signal = ctrl.signal;
    return fetch(url, opts).finally(function () { clearTimeout(t); });
  }

  // Retry loop for Render free-tier cold starts (service sleeps when idle).
  // onAttempt(n) is called before each attempt so the UI can explain the wait.
  function fetchAwake(url, opts, onAttempt) {
    var attempts = 6;
    function attempt(n) {
      if (onAttempt) onAttempt(n);
      return fetchTimeout(url, opts, 12000).catch(function (err) {
        if (n < attempts) return attempt(n + 1);
        throw err;
      });
    }
    return attempt(1);
  }

  function apiErrorMessage(res) {
    return res.json().then(function (body) {
      return body && body.message ? body.message : "Request failed (" + res.status + ")";
    }).catch(function () { return "Request failed (" + res.status + ")"; });
  }

  function copyText(text, btn, doneLabel) {
    function done() {
      var orig = btn.textContent;
      btn.textContent = doneLabel || "Copied!";
      btn.classList.add("copied");
      setTimeout(function () { btn.textContent = orig; btn.classList.remove("copied"); }, 1600);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () { fallback(); });
    } else { fallback(); }
    function fallback() {
      var ta = document.createElement("textarea");
      ta.value = text; ta.style.position = "fixed"; ta.style.opacity = "0";
      document.body.appendChild(ta); ta.select();
      try { document.execCommand("copy"); done(); } catch (e) { /* noop */ }
      document.body.removeChild(ta);
    }
  }

  function loadRecent() {
    try { return JSON.parse(localStorage.getItem(LS_KEY) || "[]"); }
    catch (e) { return []; }
  }
  function saveRecent(items) {
    try { localStorage.setItem(LS_KEY, JSON.stringify(items.slice(0, 25))); } catch (e) { /* noop */ }
  }
  function rememberLink(entry) {
    var items = loadRecent().filter(function (x) { return x.code !== entry.code; });
    items.unshift(entry);
    saveRecent(items);
    renderRecent();
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function truncate(s, n) { return s.length > n ? s.slice(0, n - 1) + "…" : s; }

  /* ---------- health + counters ---------- */

  function setHealth(state, text) {
    healthPill.className = "health-pill health-" + state;
    healthText.textContent = text;
  }

  function checkHealth() {
    setHealth("wake", "Waking up…");
    fetchAwake("/actuator/health", {}, function (n) {
      if (n > 1) setHealth("wake", "Waking up… (attempt " + n + ")");
    }).then(function (res) {
      if (res.ok) { setHealth("ok", "Operational"); }
      else { setHealth("unknown", "Unreachable"); }
    }).catch(function () { setHealth("unknown", "Unreachable"); });
  }

  function loadCounters() {
    fetchTimeout(API + "?page=0&size=1", {}, 12000).then(function (res) {
      if (!res.ok) return;
      return res.json();
    }).then(function (page) {
      if (page && typeof page.totalElements === "number") {
        statLinks.textContent = page.totalElements.toLocaleString("en-IN");
      }
    }).catch(function () { /* counters are best-effort */ });
  }

  /* ---------- shorten ---------- */

  function validUrl(raw) {
    var t = raw.trim();
    if (!t) return "Please paste a URL first.";
    var withScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(t) ? t : "https://" + t;
    try {
      var u = new URL(withScheme);
      if (u.protocol !== "http:" && u.protocol !== "https:") return "Only http and https links can be shortened.";
      if (u.host === location.host) return "That link already points to this service.";
      return null;
    } catch (e) { return "That doesn't look like a valid URL."; }
  }

  function validAlias(raw) {
    var t = raw.trim();
    if (!t) return null;
    if (!/^[A-Za-z0-9_-]{3,32}$/.test(t)) return "Alias must be 3–32 characters: letters, numbers, - or _ .";
    return null;
  }

  var lastResultCode = null;

  function doShorten() {
    hideStatus();
    resultCard.classList.add("hidden");
    resultClicks.classList.add("hidden");

    var urlErr = validUrl(urlInput.value);
    if (urlErr) { showStatus("error", escapeHtml(urlErr)); urlInput.focus(); return; }
    var aliasErr = validAlias(aliasInput.value);
    if (aliasErr) { showStatus("error", escapeHtml(aliasErr)); aliasInput.focus(); return; }

    var raw = urlInput.value.trim();
    var url = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(raw) ? raw : "https://" + raw;
    var body = { url: url };
    var alias = aliasInput.value.trim();
    if (alias) body.customAlias = alias;

    shortenBtn.disabled = true;
    showStatus("wake", spinnerHtml("Working…"));

    fetchAwake(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    }, function (n) {
      if (n > 1) showStatus("wake", spinnerHtml("Waking up the server (free tier sleeps when idle)… attempt " + n));
    }).then(function (res) {
      if (res.status === 201) return res.json().then(function (data) { onShortened(data); });
      if (res.status === 429) throw new Error("Rate limit hit — max 20 links per minute. Wait a few seconds and retry.");
      return apiErrorMessage(res).then(function (msg) { throw new Error(msg); });
    }).catch(function (err) {
      var msg = err.name === "AbortError" || /failed to fetch|networkerror/i.test(err.message)
        ? "Couldn't reach the server. It may be waking up — try again in a few seconds."
        : err.message;
      showStatus("error", escapeHtml(msg));
    }).finally(function () { shortenBtn.disabled = false; });
  }

  function onShortened(data) {
    hideStatus();
    lastResultCode = data.code;
    resultLink.href = data.shortUrl;
    resultLink.textContent = data.shortUrl.replace(/^https?:\/\//, "");
    resultOriginal.textContent = truncate(data.originalUrl, 60);
    resultOriginal.title = data.originalUrl;
    resultCard.classList.remove("hidden");
    rememberLink({ code: data.code, shortUrl: data.shortUrl, originalUrl: data.originalUrl, createdAt: Date.now() });
    loadCounters();
  }

  shortenBtn.addEventListener("click", doShorten);
  urlInput.addEventListener("keydown", function (e) { if (e.key === "Enter") doShorten(); });
  aliasInput.addEventListener("keydown", function (e) { if (e.key === "Enter") doShorten(); });
  aliasToggle.addEventListener("click", function () {
    aliasRow.classList.toggle("hidden");
    if (!aliasRow.classList.contains("hidden")) aliasInput.focus();
  });
  copyBtn.addEventListener("click", function () { copyText(resultLink.href, copyBtn); });

  resultStatsBtn.addEventListener("click", function () {
    if (!lastResultCode) return;
    resultStatsBtn.textContent = "Loading…";
    fetchTimeout(API + "/" + encodeURIComponent(lastResultCode) + "/stats", {}, 12000)
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (stats) {
        resultStatsBtn.textContent = "View stats";
        if (stats) {
          resultClicks.textContent = "👁 " + stats.clicks + (stats.clicks === 1 ? " click" : " clicks");
          resultClicks.classList.remove("hidden");
        }
      })
      .catch(function () { resultStatsBtn.textContent = "View stats"; });
  });

  /* ---------- recent links ---------- */

  function renderRecent() {
    var items = loadRecent();
    if (!items.length) { recentSection.classList.add("hidden"); return; }
    recentSection.classList.remove("hidden");
    recentList.innerHTML = "";
    items.forEach(function (item) {
      var div = document.createElement("div");
      div.className = "recent-item";
      div.innerHTML =
        '<div class="recent-main">' +
          '<a class="recent-short" href="' + escapeHtml(item.shortUrl) + '" target="_blank" rel="noopener">' +
            escapeHtml(item.shortUrl.replace(/^https?:\/\//, "")) + "</a>" +
          '<span class="recent-orig" title="' + escapeHtml(item.originalUrl) + '">' +
            escapeHtml(truncate(item.originalUrl, 70)) + "</span>" +
        "</div>" +
        '<span class="recent-clicks" data-clicks>…</span>' +
        '<div class="recent-actions">' +
          '<button class="icon-btn" data-act="copy">Copy</button>' +
          '<button class="icon-btn" data-act="stats">Stats</button>' +
          '<button class="icon-btn danger" data-act="del">Delete</button>' +
        "</div>";
      var clicksEl = div.querySelector("[data-clicks]");
      function refreshClicks() {
        clicksEl.textContent = "…";
        fetchTimeout(API + "/" + encodeURIComponent(item.code) + "/stats", {}, 12000)
          .then(function (res) { return res.ok ? res.json() : null; })
          .then(function (s) {
            clicksEl.textContent = s ? ("👁 " + s.clicks + (s.clicks === 1 ? " click" : " clicks")) : "gone";
          })
          .catch(function () { clicksEl.textContent = "—"; });
      }
      refreshClicks();
      div.querySelector('[data-act="copy"]').addEventListener("click", function (e) {
        copyText(item.shortUrl, e.target);
      });
      div.querySelector('[data-act="stats"]').addEventListener("click", refreshClicks);
      div.querySelector('[data-act="del"]').addEventListener("click", function () {
        if (!confirm("Delete this short link? It will stop redirecting.")) return;
        fetchTimeout(API + "/" + encodeURIComponent(item.code), { method: "DELETE" }, 12000)
          .finally(function () {
            saveRecent(loadRecent().filter(function (x) { return x.code !== item.code; }));
            renderRecent();
            loadCounters();
          });
      });
      recentList.appendChild(div);
    });
  }

  /* ---------- init ---------- */
  checkHealth();
  loadCounters();
  renderRecent();
})();
