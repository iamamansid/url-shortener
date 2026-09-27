/* Snip login page — sign in, register, and Google SSO. */
(function () {
  "use strict";

  var $ = function (id) { return document.getElementById(id); };
  var tabSignin = $("tab-signin"), tabRegister = $("tab-register"),
      signinForm = $("signin-form"), registerForm = $("register-form"),
      statusBox = $("auth-status"), ssoBlock = $("sso-block"),
      authTitle = $("auth-title");

  function showStatus(kind, text) {
    statusBox.className = "status status-" + kind;
    statusBox.textContent = text;
  }
  function hideStatus() { statusBox.className = "status hidden"; statusBox.textContent = ""; }

  function setBusy(form, busy) {
    var btn = form.querySelector('button[type="submit"]');
    if (btn) btn.disabled = busy;
  }

  /* If already signed in, go straight to the right dashboard. */
  fetch("/api/v1/auth/me").then(function (res) {
    if (!res.ok) return null;
    return res.json();
  }).then(function (me) {
    if (me && me.email) {
      location.href = me.role === "ADMIN" ? "/admin.html" : "/dashboard.html";
    }
  }).catch(function () { /* not signed in — stay */ });

  /* Google SSO button appears only when the OAuth client is configured. */
  fetch("/api/v1/auth/providers").then(function (res) {
    return res.ok ? res.json() : null;
  }).then(function (p) {
    if (p && p.googleEnabled) {
      ssoBlock.classList.remove("hidden");
      /* Cache-buster: guarantees the tap always reaches the server instead of
         a stale cached redirect from a previous deploy. */
      var btn = $("google-btn");
      if (btn) btn.href = "/oauth2/authorization/google?t=" + Date.now();
    }
  }).catch(function () { /* providers check is best-effort */ });

  tabSignin.addEventListener("click", function () {
    tabSignin.classList.add("active"); tabRegister.classList.remove("active");
    signinForm.classList.remove("hidden"); registerForm.classList.add("hidden");
    authTitle.textContent = "Welcome back"; hideStatus();
  });
  tabRegister.addEventListener("click", function () {
    tabRegister.classList.add("active"); tabSignin.classList.remove("active");
    registerForm.classList.remove("hidden"); signinForm.classList.add("hidden");
    authTitle.textContent = "Create your account"; hideStatus();
  });

  function goToDashboard(me) {
    location.href = me && me.role === "ADMIN" ? "/admin.html" : "/dashboard.html";
  }

  function signIn(email, password) {
    var body = "email=" + encodeURIComponent(email) + "&password=" + encodeURIComponent(password);
    return fetch("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body
    });
  }

  signinForm.addEventListener("submit", function (e) {
    e.preventDefault();
    hideStatus();
    var email = $("signin-email").value.trim();
    var password = $("signin-password").value;
    setBusy(signinForm, true);
    showStatus("wake", "Signing you in…");
    signIn(email, password).then(function (res) {
      if (!res.ok) throw new Error("Wrong email or password. Try again.");
      return res.json();
    }).then(function (me) { goToDashboard(me); })
      .catch(function (err) { showStatus("error", err.message); })
      .finally(function () { setBusy(signinForm, false); });
  });

  registerForm.addEventListener("submit", function (e) {
    e.preventDefault();
    hideStatus();
    var name = $("register-name").value.trim();
    var email = $("register-email").value.trim();
    var password = $("register-password").value;
    setBusy(registerForm, true);
    showStatus("wake", "Creating your account…");
    fetch("/api/v1/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email, password: password, displayName: name || undefined })
    }).then(function (res) {
      if (res.status === 201) return res.json();
      return res.json().then(function (b) {
        throw new Error((b && b.message) || "Couldn't create the account (" + res.status + ")");
      }).catch(function (err) {
        if (err.message) throw err;
        throw new Error("Couldn't create the account (" + res.status + ")");
      });
    }).then(function () {
      /* Auto sign-in with the new credentials. */
      return signIn(email, password);
    }).then(function (res) {
      if (!res.ok) throw new Error("Account created — please sign in.");
      return res.json();
    }).then(function (me) { goToDashboard(me); })
      .catch(function (err) { showStatus("error", err.message); })
      .finally(function () { setBusy(registerForm, false); });
  });
})();
