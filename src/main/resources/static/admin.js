/* Snip admin dashboard — site-wide analytics (admins only). */
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var statusBox = $("admin-status"), content = $("admin-content"), userChip = $("user-chip");

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function truncate(s, n) { return s.length > n ? s.slice(0, n - 1) + "…" : s; }
  function fmt(n) { return Number(n || 0).toLocaleString("en-IN"); }
  function fmtDate(iso) {
    if (!iso) return "—";
    try { return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short" }); }
    catch (e) { return "—"; }
  }
  function showStatus(kind, text) {
    statusBox.className = "status status-" + kind;
    statusBox.textContent = text;
  }

  /* ---------- auth guard ---------- */

  fetch("/api/v1/auth/me").then(function (res) {
    if (!res.ok) { location.href = "/login.html"; return null; }
    return res.json();
  }).then(function (me) {
    if (!me) return;
    if (me.role !== "ADMIN") { location.href = "/dashboard.html"; return; }
    userChip.textContent = me.displayName || me.email;
    userChip.classList.remove("hidden");
    loadStats();
  }).catch(function () { /* redirect already handled */ });

  $("signout-btn").addEventListener("click", function () {
    fetch("/api/v1/auth/logout", { method: "POST" })
      .finally(function () { location.href = "/"; });
  });

  /* ---------- charts (dependency-free SVG bars) ---------- */

  function barChart(el, series, color) {
    var max = 0;
    series.forEach(function (d) { if (d.count > max) max = d.count; });
    var W = 560, H = 150, pad = 4, n = series.length;
    var bw = (W - pad * 2) / n;
    var html = '<svg viewBox="0 0 ' + W + " " + H + '" preserveAspectRatio="none" class="bars">';
    series.forEach(function (d, i) {
      var h = max > 0 ? Math.max(2, (d.count / max) * (H - 26)) : 2;
      var x = pad + i * bw + 1;
      var y = H - 20 - h;
      html += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + Math.max(1, bw - 2).toFixed(1) +
        '" height="' + h.toFixed(1) + '" rx="2" fill="' + color + '" opacity="0.85">' +
        "<title>" + d.date + ": " + d.count + "</title></rect>";
    });
    html += "</svg>";
    var first = series[0] ? series[0].date.slice(5) : "";
    var last = series[n - 1] ? series[n - 1].date.slice(5) : "";
    html += '<div class="chart-axis"><span>' + first + "</span><span>" + last + "</span></div>";
    el.innerHTML = html;
  }

  /* ---------- tables ---------- */

  function ownerCell(email) {
    return email ? '<span class="mono">' + escapeHtml(email) + "</span>" : '<span class="dim">anonymous</span>';
  }

  function renderTopLinks(rows) {
    var tb = $("top-links-body");
    tb.innerHTML = "";
    rows.forEach(function (r) {
      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td><a class="mono" href="' + escapeHtml(r.shortUrl) + '" target="_blank" rel="noopener">' +
          escapeHtml(r.shortUrl.replace(/^https?:\/\//, "")) + "</a></td>" +
        '<td><span class="dim" title="' + escapeHtml(r.originalUrl) + '">' + escapeHtml(truncate(r.originalUrl, 44)) + "</span></td>" +
        "<td>" + ownerCell(r.ownerEmail) + "</td>" +
        '<td class="num"><strong>' + fmt(r.clicks) + "</strong></td>" +
        "<td>" + fmtDate(r.lastClickedAt) + "</td>";
      tb.appendChild(tr);
    });
    if (!rows.length) tb.innerHTML = '<tr><td colspan="5" class="dim">No links yet.</td></tr>';
  }

  function renderRecentLinks(rows) {
    var tb = $("recent-links-body");
    tb.innerHTML = "";
    rows.forEach(function (r) {
      var tr = document.createElement("tr");
      tr.innerHTML =
        '<td><a class="mono" href="' + escapeHtml(r.shortUrl) + '" target="_blank" rel="noopener">' +
          escapeHtml(r.shortUrl.replace(/^https?:\/\//, "")) + "</a></td>" +
        '<td><span class="dim" title="' + escapeHtml(r.originalUrl) + '">' + escapeHtml(truncate(r.originalUrl, 44)) + "</span></td>" +
        "<td>" + ownerCell(r.ownerEmail) + "</td>" +
        '<td class="num"><strong>' + fmt(r.clicks) + "</strong></td>" +
        "<td>" + fmtDate(r.createdAt) + "</td>";
      tb.appendChild(tr);
    });
    if (!rows.length) tb.innerHTML = '<tr><td colspan="5" class="dim">No links yet.</td></tr>';
  }

  function renderUsers(rows) {
    var tb = $("users-body");
    tb.innerHTML = "";
    rows.forEach(function (u) {
      var tr = document.createElement("tr");
      var badge = u.role === "ADMIN" ? ' <span class="role-badge">admin</span>' : "";
      var via = u.provider === "GOOGLE" ? "Google" : "Email";
      tr.innerHTML =
        "<td><span class=\"mono\">" + escapeHtml(u.email) + "</span>" + badge +
          '<br /><span class="dim">' + escapeHtml(u.displayName || "") + "</span></td>" +
        "<td>" + via + "</td>" +
        "<td>" + escapeHtml(u.role) + "</td>" +
        '<td class="num"><strong>' + fmt(u.linkCount) + "</strong></td>" +
        "<td>" + fmtDate(u.createdAt) + "</td>";
      tb.appendChild(tr);
    });
    if (!rows.length) tb.innerHTML = '<tr><td colspan="5" class="dim">No users yet.</td></tr>';
  }

  /* ---------- load ---------- */

  function loadStats() {
    showStatus("wake", "Loading analytics…");
    fetch("/api/v1/admin/stats").then(function (res) {
      if (res.status === 403) { location.href = "/dashboard.html"; throw new Error("forbidden"); }
      if (!res.ok) throw new Error("failed");
      return res.json();
    }).then(function (s) {
      statusBox.className = "status hidden"; statusBox.textContent = "";
      content.classList.remove("hidden");
      $("st-users").textContent = fmt(s.totals.users);
      $("st-links").textContent = fmt(s.totals.links);
      $("st-clicks").textContent = fmt(s.totals.clicks);
      $("st-views").textContent = fmt(s.totals.pageViews);
      barChart($("chart-views"), s.pageViewsPerDay, "#8b5cf6");
      barChart($("chart-links"), s.linksPerDay, "#3b82f6");
      barChart($("chart-clicks"), s.clicksPerDay, "#22c55e");
      renderTopLinks(s.topLinks || []);
      renderRecentLinks(s.recentLinks || []);
      renderUsers(s.recentUsers || []);
    }).catch(function (err) {
      if (err.message !== "forbidden") showStatus("error", "Couldn't load analytics. Try refreshing.");
    });
  }
})();
