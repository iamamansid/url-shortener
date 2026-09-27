/* Snip user dashboard — the signed-in user's own links. */
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var linksBody = $("links-body"), linksStatus = $("links-status"),
      emptyState = $("empty-state"), pageInfo = $("page-info"),
      prevBtn = $("prev-btn"), nextBtn = $("next-btn"),
      urlInput = $("url-input"), shortenBtn = $("shorten-btn"),
      aliasToggle = $("alias-toggle"), aliasRow = $("alias-row"), aliasInput = $("alias-input"),
      statusBox = $("shorten-status"), userChip = $("user-chip");

  var page = 0, totalPages = 1;

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function truncate(s, n) { return s.length > n ? s.slice(0, n - 1) + "…" : s; }
  function fmtDate(iso) {
    try { return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }); }
    catch (e) { return "—"; }
  }
  function showStatus(kind, text) {
    linksStatus.className = "status status-" + kind;
    linksStatus.textContent = text;
  }

  function api(path, opts) {
    opts = opts || {};
    return fetch(path, opts).then(function (res) {
      if (res.status === 401 || res.status === 403) {
        location.href = "/login.html";
        throw new Error("signed out");
      }
      return res;
    });
  }

  /* ---------- session ---------- */

  fetch("/api/v1/auth/me").then(function (res) {
    if (!res.ok) { location.href = "/login.html"; return null; }
    return res.json();
  }).then(function (me) {
    if (!me) return;
    userChip.textContent = me.displayName || me.email;
    userChip.classList.remove("hidden");
    if (me.role === "ADMIN") $("nav-admin-link").classList.remove("hidden");
    loadLinks();
  }).catch(function () { /* redirect already handled */ });

  $("signout-btn").addEventListener("click", function () {
    fetch("/api/v1/auth/logout", { method: "POST" })
      .finally(function () { location.href = "/"; });
  });

  /* ---------- create ---------- */

  $("alias-prefix").textContent = location.host + "/";
  aliasToggle.addEventListener("click", function () {
    aliasRow.classList.toggle("hidden");
    if (!aliasRow.classList.contains("hidden")) aliasInput.focus();
  });

  function doShorten() {
    statusBox.className = "status hidden"; statusBox.textContent = "";
    var raw = urlInput.value.trim();
    if (!raw) { statusBox.className = "status status-error"; statusBox.textContent = "Please paste a URL first."; return; }
    var url = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(raw) ? raw : "https://" + raw;
    var body = { url: url };
    var alias = aliasInput.value.trim();
    if (alias) {
      if (!/^[A-Za-z0-9_-]{3,32}$/.test(alias)) {
        statusBox.className = "status status-error";
        statusBox.textContent = "Alias must be 3–32 characters: letters, numbers, - or _ .";
        return;
      }
      body.customAlias = alias;
    }
    shortenBtn.disabled = true;
    statusBox.className = "status status-wake"; statusBox.textContent = "Working…";
    api("/api/v1/urls", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    }).then(function (res) {
      if (res.status === 201) return res.json();
      return res.json().then(function (b) { throw new Error((b && b.message) || "Failed (" + res.status + ")"); });
    }).then(function () {
      statusBox.className = "status hidden"; statusBox.textContent = "";
      urlInput.value = ""; aliasInput.value = "";
      page = 0; loadLinks();
    }).catch(function (err) {
      if (err.message !== "signed out") {
        statusBox.className = "status status-error"; statusBox.textContent = err.message;
      }
    }).finally(function () { shortenBtn.disabled = false; });
  }
  shortenBtn.addEventListener("click", doShorten);
  urlInput.addEventListener("keydown", function (e) { if (e.key === "Enter") doShorten(); });

  /* ---------- list ---------- */

  function rowHtml(item) {
    var shortBare = escapeHtml(item.shortUrl.replace(/^https?:\/\//, ""));
    return '<tr>' +
      '<td><a class="mono" href="' + escapeHtml(item.shortUrl) + '" target="_blank" rel="noopener">' + shortBare + "</a></td>" +
      '<td><span class="dim" title="' + escapeHtml(item.originalUrl) + '">' + escapeHtml(truncate(item.originalUrl, 48)) + "</span></td>" +
      '<td class="num"><strong>' + item.clicks + "</strong></td>" +
      "<td>" + fmtDate(item.createdAt) + "</td>" +
      '<td class="actions-col">' +
        '<button class="icon-btn" data-act="copy">Copy</button>' +
        '<button class="icon-btn danger" data-act="del">Delete</button>' +
      "</td></tr>";
  }

  function loadLinks() {
    showStatus("wake", "Loading your links…");
    api("/api/v1/me/urls?page=" + page + "&size=15").then(function (res) {
      return res.json();
    }).then(function (data) {
      linksStatus.className = "status hidden"; linksStatus.textContent = "";
      totalPages = Math.max(data.totalPages || 1, 1);
      var items = data.items || [];
      linksBody.innerHTML = "";
      items.forEach(function (item) {
        var tr = document.createElement("tbody");
        tr.innerHTML = rowHtml(item);
        var row = tr.firstChild;
        row.querySelector('[data-act="copy"]').addEventListener("click", function (e) {
          copyText(item.shortUrl, e.target);
        });
        row.querySelector('[data-act="del"]').addEventListener("click", function () {
          if (!confirm("Delete " + item.code + "? It will stop redirecting.")) return;
          api("/api/v1/urls/" + encodeURIComponent(item.code), { method: "DELETE" })
            .then(function () { loadLinks(); })
            .catch(function (err) {
              if (err.message !== "signed out") showStatus("error", err.message);
            });
        });
        linksBody.appendChild(row);
      });
      emptyState.classList.toggle("hidden", items.length > 0);
      pageInfo.textContent = "Page " + (page + 1) + " of " + totalPages;
      prevBtn.disabled = page <= 0;
      nextBtn.disabled = page >= totalPages - 1;
    }).catch(function (err) {
      if (err.message !== "signed out") showStatus("error", "Couldn't load your links.");
    });
  }

  function copyText(text, btn) {
    var done = function () {
      var orig = btn.textContent; btn.textContent = "Copied!";
      setTimeout(function () { btn.textContent = orig; }, 1400);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, done);
    } else { done(); }
  }

  prevBtn.addEventListener("click", function () { if (page > 0) { page--; loadLinks(); } });
  nextBtn.addEventListener("click", function () { if (page < totalPages - 1) { page++; loadLinks(); } });
})();
