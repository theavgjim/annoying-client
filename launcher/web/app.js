"use strict";

const TOKEN = new URLSearchParams(location.search).get("t") || "";
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

let state = null;
let mode = "official";
let perf = null;
let selectedPreset = null;
let selectedPerfMods = new Set();
let searchOffset = 0;
let searchTimer = 0;
let logLines = [];

// ---- API ----------------------------------------------------------------------------------

async function api(path, body) {
  const opts = { headers: { "X-Launcher-Token": TOKEN } };
  if (body !== undefined) {
    opts.method = "POST";
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data;
}

function toast(level, title, message) {
  const el = document.createElement("div");
  el.className = `toast ${level}`;
  el.innerHTML = `<b>${esc(title)}</b><span>${esc(message || "")}</span>`;
  $("#toasts").appendChild(el);
  setTimeout(() => { el.classList.add("out"); setTimeout(() => el.remove(), 300); }, level === "error" ? 7000 : 4000);
}

async function guarded(btn, fn) {
  if (btn) btn.disabled = true;
  try { return await fn(); }
  catch (e) { toast("error", "Something went wrong", e.message); }
  finally { if (btn) btn.disabled = false; }
}

// ---- navigation ---------------------------------------------------------------------------

function show(page) {
  $$(".nav").forEach((b) => b.classList.toggle("active", b.dataset.page === page));
  $$(".page").forEach((p) => p.classList.toggle("active", p.id === `page-${page}`));
  if (page === "mods") loadInstalled();
  if (page === "performance") loadPerformance();
  if (page === "console") renderConsole();
  if (page === "settings") fillSettings();
  if (page === "boosted") loadBoosted();
}
$$(".nav").forEach((b) => b.addEventListener("click", () => {
  show(b.dataset.page);
  // Clicking "Normal FPS Boosted Minecraft" starts the game right away.
  if (b.dataset.page === "boosted" && state && !state.busy && !state.running) playBoosted();
}));
$$("[data-goto]").forEach((b) => b.addEventListener("click", () => show(b.dataset.goto)));
document.addEventListener("click", (e) => {
  const open = e.target.closest("[data-open]");
  if (open) guarded(null, () => api("/api/open", { what: open.dataset.open }));
});

// ---- state --------------------------------------------------------------------------------

async function refresh() {
  try { state = await api("/api/state"); } catch (e) { return; }
  render();
}

function presetName(id) {
  return ({ max: "Max FPS", balanced: "Balanced", quality: "Quality" })[id] || "Not set";
}

function render() {
  if (!state) return;
  $("#mc-version").textContent = state.minecraftVersion;
  $("#loader-version").textContent = state.loaderVersion;
  $$(".mcv").forEach((e) => (e.textContent = state.minecraftVersion));
  $("#stat-mods").textContent = `${state.modsEnabled}`;
  $("#stat-preset").textContent = presetName(state.settings.fpsPreset);
  $("#stat-memory").textContent = `${(state.settings.memoryMB / 1024).toFixed(1)} GB`;

  const acc = $("#account");
  if (state.account) {
    acc.innerHTML = `<div class="acc-row"><div class="avatar">${esc(state.account.name[0] || "?")}</div>
      <div><div class="acc-name">${esc(state.account.name)}</div><div class="acc-sub">Microsoft account</div></div></div>
      <button class="btn ghost" id="logout">Sign out</button>`;
    $("#logout").onclick = () => guarded(null, () => api("/api/logout", {}).then(refresh));
  } else {
    acc.innerHTML = `<div class="acc-row"><div class="avatar">?</div><div><div class="acc-name">Not signed in</div>
      <div class="acc-sub">${state.settings.clientId ? "Sign in for direct launch" : "Using the Minecraft Launcher"}</div></div></div>
      <button class="btn" id="login">Sign in</button>`;
    $("#login").onclick = startLogin;
  }

  $$(".seg").forEach((s) => s.classList.toggle("active", s.dataset.mode === mode));
  const hint = $("#mode-hint");
  if (mode === "official") {
    hint.innerHTML = state.officialLauncher
      ? "Signs in through the official Minecraft Launcher, with this launcher's mods and optimizations."
      : "The official Minecraft Launcher isn't installed. Get it from minecraft.net, or use Direct.";
  } else if (!state.settings.clientId) {
    hint.innerHTML = `Direct launch needs an approved Microsoft application ID. <a data-goto2="settings">Set it up</a>`;
    $("[data-goto2]", hint).onclick = () => show("settings");
  } else {
    hint.textContent = state.account ? `Launching as ${state.account.name}.` : "Sign in with your Microsoft account to launch directly.";
  }
  if (mode === "direct" && state.testAccount) {
    hint.textContent = "Test build: launching with the test account.";
  }

  const up = state.update;
  $("#update-banner").classList.toggle("hidden", !up);
  if (up) {
    $("#update-title").textContent = "Annoying Client " + up.version + " is available";
    $("#update-notes").textContent = (up.notes || "") + " You have " + state.version + ".";
  }
  $("#version-line").textContent = "You have " + state.version + (up ? ". Version " + up.version + " is available." : state.updateError ? ". " + state.updateError : ".");

  const bplay = $("#boosted-play");
  bplay.classList.toggle("running", state.running);
  bplay.disabled = state.busy || state.running;
  $("span", bplay).textContent = state.running ? "RUNNING" : state.busy ? "WORKING..." : "PLAY";
  $("#boosted-hint").textContent = state.account
    ? "Starts right here as " + state.account.name + "."
    : state.testAccount ? "Test build: starts with the test account." : "Starts right here. Sign in with your Microsoft account first (bottom left).";
  $$(".mc-version-copy").forEach((e) => (e.textContent = $("#mc-version").textContent));

  const play = $("#play-btn");
  play.classList.toggle("running", state.running);
  play.disabled = state.busy && !state.running;
  $("span", play).textContent = state.running ? "RUNNING" : state.busy ? "WORKING..." : "PLAY";
  $("#stop-game").classList.toggle("hidden", !state.running);
  if (!state.busy && !state.running) setProgress(null);
}

function setProgress(p) {
  // Shown on the Play page and on the Normal FPS Boosted page.
  const boxes = [
    { box: $("#progress"), stage: $("#progress-stage"), detail: $("#progress-detail"), fill: $("#progress-fill") },
    { box: $("#boosted-progress"), stage: $("#boosted-progress .progress-stage"), detail: $("#boosted-progress .progress-detail"), fill: $("#boosted-progress .fill") },
  ];
  let detail = "";
  if (p && p.totalBytes > 0) detail = `${(p.bytes / 1048576).toFixed(1)} / ${(p.totalBytes / 1048576).toFixed(1)} MB`;
  else if (p && p.total > 0) detail = `${p.done} / ${p.total}`;
  for (const b of boxes) {
    if (!p) { b.box.classList.add("hidden"); continue; }
    b.box.classList.remove("hidden");
    b.stage.textContent = p.stage || "Working";
    b.detail.textContent = detail;
    b.fill.style.width = `${Math.round(Math.min(1, Math.max(0, p.fraction || 0)) * 100)}%`;
  }
}

// ---- play ---------------------------------------------------------------------------------

$$(".seg").forEach((s) => s.addEventListener("click", () => { mode = s.dataset.mode; render(); }));
$("#play-btn").addEventListener("click", async () => {
  if (state?.running) { show("console"); return; }
  if (mode === "direct" && !state.account && !state.testAccount) {
    if (!state.settings.clientId) {
      show("settings");
      toast("info", "Direct launch needs setup", "Add an approved Microsoft application ID, or play via the Minecraft Launcher.");
    } else {
      startLogin();
    }
    return;
  }
  setProgress({ stage: "Starting", fraction: 0 });
  await guarded($("#play-btn"), () => api("/api/play", { mode }));
});

// ---- normal FPS boosted minecraft (no Annoying Client) --------------------------------------

async function loadBoosted() {
  try {
    const info = await api("/api/plain");
    const mods = info.mods || [];
    $("#boosted-profile").textContent = info.profile || "Annoying Client";
    $("#boosted-count").textContent = "(" + mods.length + ")";
    const box = $("#boosted-mods");
    box.innerHTML = "";
    if (!mods.length) {
      box.innerHTML = '<span class="empty">Only Minecraft and Fabric: this profile has no other mods yet.</span>';
    }
    for (const m of mods) {
      const chip = document.createElement("span");
      chip.className = "chip";
      const icon = m.icon ? document.createElement("img") : document.createElement("i");
      if (m.icon) { icon.src = m.icon; icon.alt = ""; icon.loading = "lazy"; }
      chip.append(icon, document.createTextNode(m.name || m.file));
      chip.title = (m.name || m.file) + (m.version ? " " + m.version : "");
      box.appendChild(chip);
    }
  } catch (e) {
    toast("error", "Could not list the mods", e.message);
  }
}

async function playBoosted() {
  if (state?.running) { toast("info", "Minecraft is already running", "Close it first to start Normal FPS Boosted."); return; }
  // Started directly by Annoying Client, so it needs the Microsoft sign-in (like Direct play).
  if (!state.account && !state.testAccount) {
    if (!state.settings.clientId) {
      show("settings");
      toast("info", "Sign-in needs setup", "Normal FPS Boosted starts the game from Annoying Client, so it needs your Microsoft account. Add your approved Microsoft application ID here, then sign in.");
    } else {
      toast("info", "Sign in first", "Normal FPS Boosted starts the game from Annoying Client with your Microsoft account.");
      startLogin();
    }
    return;
  }
  setProgress({ stage: "Starting", fraction: 0 });
  await guarded($("#boosted-play"), () => api("/api/play", { plain: true }));
}
$("#boosted-play").addEventListener("click", playBoosted);

// ---- updates ------------------------------------------------------------------------------

$("#update-now").onclick = () => guarded($("#update-now"), async () => {
  setProgress({ stage: "Downloading the update", fraction: 0 });
  await api("/api/update/apply", {});
});
$("#check-update").onclick = () => guarded($("#check-update"), async () => {
  const res = await api("/api/update/check", {});
  toast(res.update ? "info" : "success", res.update ? "Update available: " + res.update.version : "You have the latest version", res.current);
  await refresh();
});

// ---- profiles -----------------------------------------------------------------------------

let profiles = null;
let chosenTemplate = "optimized";

async function loadProfiles() {
  try { profiles = await api("/api/profiles"); } catch (e) { return; }
  const sel = $("#profile-select");
  sel.innerHTML = profiles.profiles.map((p) => '<option value="' + esc(p.id) + '"' + (p.id === profiles.active ? " selected" : "") + ">" + esc(p.name) + "</option>").join("");
  $("#profile-delete").classList.toggle("hidden", profiles.active === "default");
}
$("#profile-select").onchange = (e) => guarded(null, async () => {
  await api("/api/profiles/select", { id: e.target.value });
  toast("success", "Profile switched", e.target.selectedOptions[0].textContent);
  await refresh();
}).finally(loadProfiles);

$("#profile-new").onclick = () => {
  const box = $("#templates");
  box.innerHTML = profiles.templates.map((t) => '<div class="template' + (t.id === chosenTemplate ? " active" : "") + '" data-tpl="' + esc(t.id) + '"><b>' +
    esc(t.name) + "</b><span>" + esc(t.description) + "</span></div>").join("");
  $$("[data-tpl]", box).forEach((el) => (el.onclick = () => {
    chosenTemplate = el.dataset.tpl;
    $$("[data-tpl]", box).forEach((x) => x.classList.toggle("active", x === el));
  }));
  $("#profile-modal").classList.remove("hidden");
  $("#new-profile-name").focus();
};
$("#profile-cancel").onclick = () => $("#profile-modal").classList.add("hidden");
$("#profile-create").onclick = () => guarded($("#profile-create"), async () => {
  const p = await api("/api/profiles/create", { name: $("#new-profile-name").value, template: chosenTemplate });
  $("#profile-modal").classList.add("hidden");
  toast("success", "Profile created", chosenTemplate === "optimized" ? p.name + ": downloading mods..." : p.name);
  await loadProfiles();
  await refresh();
  show("play");
});
$("#profile-rename").onclick = () => {
  $("#rename-input").value = $("#profile-select").selectedOptions[0]?.textContent || "";
  $("#rename-modal").classList.remove("hidden");
};
$("#rename-cancel").onclick = () => $("#rename-modal").classList.add("hidden");
$("#rename-save").onclick = () => guarded($("#rename-save"), async () => {
  await api("/api/profiles/rename", { id: profiles.active, name: $("#rename-input").value });
  $("#rename-modal").classList.add("hidden");
  await loadProfiles();
});
$("#profile-delete").onclick = () => {
  const name = $("#profile-select").selectedOptions[0]?.textContent || "";
  $("#remove-text").textContent = '"' + name + '" and everything in its folder (mods, settings, worlds and screenshots) will be deleted.';
  $("#remove-modal").classList.remove("hidden");
};
$("#remove-cancel").onclick = () => $("#remove-modal").classList.add("hidden");
$("#remove-confirm").onclick = () => guarded($("#remove-confirm"), async () => {
  await api("/api/profiles/delete", { id: profiles.active });
  $("#remove-modal").classList.add("hidden");
  toast("success", "Profile deleted", "");
  await loadProfiles();
  await refresh();
});

// ---- sign-in ------------------------------------------------------------------------------

async function startLogin() {
  try {
    const login = await api("/api/login/start", {});
    $("#login-code").textContent = login.userCode;
    $("#login-url").textContent = (login.verificationUri || "").replace(/^https?:\/\//, "");
    $("#login-modal").classList.remove("hidden");
  } catch (e) {
    toast("error", "Can't sign in yet", e.message);
    if (!state.settings.clientId) show("settings");
  }
}
$("#copy-code").onclick = () => navigator.clipboard.writeText($("#login-code").textContent).then(() => toast("success", "Code copied", ""));
$("#login-close").onclick = () => $("#login-modal").classList.add("hidden");

// ---- mods ---------------------------------------------------------------------------------

$$(".tab").forEach((t) => t.addEventListener("click", () => {
  $$(".tab").forEach((x) => x.classList.toggle("active", x === t));
  $("#mods-installed").classList.toggle("hidden", t.dataset.tab !== "installed");
  $("#mods-browse").classList.toggle("hidden", t.dataset.tab !== "browse");
  if (t.dataset.tab === "browse" && !$("#search-results").children.length) search(true);
  if (t.dataset.tab === "installed") loadInstalled();
}));

function iconHTML(url, name) {
  return url ? `<div class="icon" style="background-image:url('${esc(url)}')"></div>` : `<div class="icon">${esc((name || "?")[0])}</div>`;
}

function renderInstalled(mods) {
  const list = $("#installed-list");
  if (!mods.length) { list.innerHTML = `<div class="empty">No mods yet. Open Browse to add some.</div>`; return; }
  list.innerHTML = mods.map((m) => `
    <div class="item ${m.enabled ? "" : "off"}" data-file="${esc(m.file)}">
      ${iconHTML(m.iconUrl, m.name)}
      <div class="item-main">
        <div class="item-title">${esc(m.name)} ${m.core ? '<span class="badge">Required</span>' : ""} ${m.update ? `<span class="badge amber">Update: ${esc(m.update)}</span>` : ""}</div>
        <div class="item-sub">${esc(m.version || "")}${m.description ? " - " + esc(m.description) : ""}</div>
      </div>
      <label class="switch" title="${m.core ? "Required" : "Enable or disable"}"><input type="checkbox" ${m.enabled ? "checked" : ""} ${m.core ? "disabled" : ""}><span></span></label>
      ${m.core ? "" : `<button class="icon-btn" title="Remove"><svg viewBox="0 0 24 24"><path d="M5 7h14M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" fill="none" stroke-width="2"/></svg></button>`}
    </div>`).join("");
  $$(".item", list).forEach((row) => {
    const file = row.dataset.file;
    const sw = $("input", row);
    sw.onchange = () => guarded(null, async () => { await api("/api/mods/toggle", { file, enabled: sw.checked }); loadInstalled(); });
    const del = $(".icon-btn", row);
    if (del) del.onclick = () => guarded(del, async () => {
      if (!confirm(`Remove ${file}?`)) return;
      await api("/api/mods/remove", { file });
      toast("success", "Mod removed", file);
      loadInstalled();
    });
  });
  const updates = mods.filter((m) => m.update).length;
  $("#update-all").classList.toggle("hidden", updates === 0);
  $("#update-all").textContent = `Update all (${updates})`;
}

async function loadInstalled() {
  try { renderInstalled(await api("/api/mods")); } catch (e) { toast("error", "Can't read mods", e.message); }
}
$("#check-updates").onclick = () => guarded($("#check-updates"), async () => {
  const mods = await api("/api/mods/updates");
  renderInstalled(mods);
  const n = mods.filter((m) => m.update).length;
  toast(n ? "info" : "success", n ? `${n} update(s) available` : "Everything is up to date", "");
});
$("#update-all").onclick = () => guarded($("#update-all"), async () => {
  const names = await api("/api/mods/update-all", {});
  toast("success", "Mods updated", names.join(", "));
  loadInstalled();
});

function fmtNum(n) { return n >= 1e6 ? (n / 1e6).toFixed(1) + "M" : n >= 1e3 ? (n / 1e3).toFixed(0) + "k" : String(n); }

async function search(reset) {
  if (reset) searchOffset = 0;
  const q = $("#search-input").value.trim();
  const sort = $("#search-sort").value;
  try {
    const res = await api(`/api/mods/search?q=${encodeURIComponent(q)}&sort=${sort}&offset=${searchOffset}`);
    const html = res.hits.map((h) => `
      <div class="mod-card">
        ${iconHTML(h.icon_url, h.title)}
        <div class="item-main">
          <div class="item-title">${esc(h.title)}</div>
          <div class="mod-desc">${esc(h.description)}</div>
          <div class="mod-meta"><span>${fmtNum(h.downloads)} downloads &middot; ${esc(h.author)}</span>
            <button class="btn ${h.installed ? "" : "primary"}" data-install="${esc(h.project_id)}" ${h.installed ? "disabled" : ""}>${h.installed ? "Installed" : "Install"}</button></div>
        </div>
      </div>`).join("");
    if (reset) $("#search-results").innerHTML = html || `<div class="empty">No mods found.</div>`;
    else $("#search-results").insertAdjacentHTML("beforeend", html);
    searchOffset += res.hits.length;
    $("#search-more").classList.toggle("hidden", searchOffset >= res.total || res.hits.length === 0);
    $$("[data-install]").forEach((b) => (b.onclick = () => guarded(b, async () => {
      b.textContent = "Installing...";
      try {
        const names = await api("/api/mods/install", { projectId: b.dataset.install });
        b.textContent = "Installed";
        b.classList.remove("primary");
        b.disabled = true;
        b.onclick = null;
        toast("success", "Installed", names.join(", "));
      } catch (e) { b.textContent = "Install"; throw e; }
    })));
  } catch (e) {
    toast("error", "Search failed", e.message);
  }
}
$("#search-input").addEventListener("input", () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => search(true), 300); });
$("#search-sort").onchange = () => search(true);
$("#search-more").onclick = () => search(false);

// ---- performance --------------------------------------------------------------------------

const presetSpecs = (o) => [`${o.renderDistance} chunks`, o.particles === "2" ? "Minimal particles" : o.particles === "1" ? "Fewer particles" : "All particles",
  o.renderClouds === '"false"' ? "No clouds" : o.renderClouds === '"fast"' ? "Fast clouds" : "Fancy clouds", o.ao === "true" ? "Smooth lighting" : "No smooth lighting", "Unlimited FPS"];

async function loadPerformance() {
  try { perf = await api("/api/performance"); } catch (e) { toast("error", "Can't load performance options", e.message); return; }
  selectedPreset = perf.current || "balanced";
  selectedPerfMods = new Set(perf.mods.filter((m) => m.installed).map((m) => m.projectId));
  if (!perf.mods.some((m) => m.installed)) perf.mods.filter((m) => m.default).forEach((m) => selectedPerfMods.add(m.projectId));
  renderPerformance();
  const s = state.settings;
  const slider = $("#mem-slider");
  slider.max = Math.max(2048, state.totalMemoryMB - 1024);
  slider.value = s.memoryMB;
  $("#gc-select").value = s.gc;
  updateMem();
}

function renderPerformance() {
  $("#presets").innerHTML = perf.presets.map((p) => `
    <button class="preset ${p.id === selectedPreset ? "active" : ""}" data-preset="${p.id}">
      <div class="preset-name">${esc(p.name)} ${p.id === "balanced" ? '<span class="badge">Recommended</span>' : ""}</div>
      <div class="preset-desc">${esc(p.description)}</div>
      <div class="preset-specs">${presetSpecs(p.options).map((x) => `<span class="spec">${esc(x)}</span>`).join("")}</div>
    </button>`).join("");
  $$("[data-preset]").forEach((b) => (b.onclick = () => { selectedPreset = b.dataset.preset; renderPerformance(); }));
  $("#perf-mods").innerHTML = perf.mods.map((m) => `
    <div class="item">${iconHTML(m.iconUrl, m.name)}
      <div class="item-main"><div class="item-title">${esc(m.name)} ${m.installed ? '<span class="badge green">Installed</span>' : ""}</div>
      <div class="item-sub">${esc(m.description)}</div></div>
      <label class="switch"><input type="checkbox" data-perf="${m.projectId}" ${selectedPerfMods.has(m.projectId) ? "checked" : ""}><span></span></label></div>`).join("");
  $$("[data-perf]").forEach((c) => (c.onchange = () => { c.checked ? selectedPerfMods.add(c.dataset.perf) : selectedPerfMods.delete(c.dataset.perf); }));
}

function updateMem() {
  const mb = +$("#mem-slider").value;
  $("#mem-value").textContent = `${(mb / 1024).toFixed(1)} GB`;
  $("#mem-hint").textContent = `Your PC has ${(state.totalMemoryMB / 1024).toFixed(1)} GB. Recommended: ${(state.recommendedMB / 1024).toFixed(1)} GB.` +
    (mb > state.totalMemoryMB * 0.75 ? " Leave memory for Windows and other apps." : "");
}
$("#mem-slider").oninput = updateMem;

$("#apply-perf").onclick = () => guarded($("#apply-perf"), async () => {
  const s = { ...state.settings, memoryMB: +$("#mem-slider").value, gc: $("#gc-select").value };
  await api("/api/settings", s);
  const res = await api("/api/performance/apply", { preset: selectedPreset, mods: [...selectedPerfMods] });
  toast("success", "Optimized", res.installed?.length ? `Installed ${res.installed.join(", ")}` : `${presetName(selectedPreset)} preset applied`);
  await refresh();
  loadPerformance();
});

// ---- console ------------------------------------------------------------------------------

function lineClass(l) {
  if (l.startsWith("[launcher]")) return "launcher";
  if (/\b(ERROR|FATAL|Exception)\b/.test(l)) return "err";
  if (/\bWARN\b/.test(l)) return "warn";
  return "";
}
function renderConsole() {
  const f = $("#log-filter").value.toLowerCase();
  const el = $("#console");
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 30;
  el.innerHTML = logLines.filter((l) => !f || l.toLowerCase().includes(f)).slice(-2500)
    .map((l) => `<span class="${lineClass(l)}">${esc(l)}</span>`).join("\n") || '<span class="launcher">Game output appears here when you launch directly.</span>';
  if (atBottom) el.scrollTop = el.scrollHeight;
}
$("#log-filter").oninput = renderConsole;
$("#log-copy").onclick = () => navigator.clipboard.writeText(logLines.join("\n")).then(() => toast("success", "Log copied", ""));
$("#stop-game").onclick = () => guarded($("#stop-game"), () => api("/api/stop", {}));

// ---- settings -----------------------------------------------------------------------------

function fillSettings() {
  if (!state) return;
  const s = state.settings;
  $("#set-mode").value = s.launchMode;
  $("#set-width").value = s.width;
  $("#set-height").value = s.height;
  $("#set-fullscreen").checked = s.fullscreen;
  $("#set-keepopen").checked = s.keepOpen;
  $("#set-java").value = s.javaPath;
  $("#set-jvm").value = s.extraJvmArgs;
  $("#set-threads").value = s.downloadThreads;
  $("#set-client").value = s.clientId;
  $("#set-autoupdate").checked = !s.disableUpdateCheck;
  $("#data-dir").textContent = state.dataDir;
}
$("#save-settings").onclick = () => guarded($("#save-settings"), async () => {
  const s = {
    ...state.settings,
    launchMode: $("#set-mode").value, width: +$("#set-width").value, height: +$("#set-height").value,
    fullscreen: $("#set-fullscreen").checked, keepOpen: $("#set-keepopen").checked,
    javaPath: $("#set-java").value.trim(), extraJvmArgs: $("#set-jvm").value.trim(),
    downloadThreads: +$("#set-threads").value, clientId: $("#set-client").value.trim(),
    disableUpdateCheck: !$("#set-autoupdate").checked,
  };
  await api("/api/settings", s);
  mode = s.launchMode;
  await refresh();
  fillSettings();
  toast("success", "Settings saved", "");
});
$("#repair").onclick = () => guarded($("#repair"), async () => {
  if (!confirm("Delete downloaded game files so they are downloaded and verified again? Your worlds and mods are kept.")) return;
  await api("/api/repair", {});
});

// ---- live events --------------------------------------------------------------------------

function connect() {
  const es = new EventSource(`/api/events?t=${encodeURIComponent(TOKEN)}`);
  es.onmessage = (e) => {
    const ev = JSON.parse(e.data);
    switch (ev.type) {
      case "progress": setProgress(ev.data); break;
      case "restarting": $("#restart-modal").classList.remove("hidden"); break;
      case "state":
        refresh().then(() => { if (ev.data === null && state && !state.account) $("#login-modal").classList.add("hidden"); });
        if (state?.account) $("#login-modal").classList.add("hidden");
        break;
      case "toast":
        toast(ev.data.level, ev.data.title, ev.data.message);
        if (ev.data.title === "Signed in") $("#login-modal").classList.add("hidden");
        if (ev.data.title === "Minecraft is starting" && state && !state.settings.keepOpen) setTimeout(() => window.close(), 1500);
        break;
      case "log":
        logLines.push(ev.data);
        if (logLines.length > 5000) logLines = logLines.slice(-4000);
        if ($("#page-console").classList.contains("active")) renderConsole();
        break;
    }
  };
  es.onopen = () => {
    if (!$("#restart-modal").classList.contains("hidden")) {
      $("#restart-modal").classList.add("hidden");
      refresh().then(() => toast("success", "Updated", "Now running " + (state ? state.version : "the new version")));
    }
  };
  es.onerror = () => { es.close(); setTimeout(connect, 1500); };
}

(async function init() {
  await refresh();
  if (state) {
    mode = state.settings.launchMode;
    if (mode === "official" && !state.officialLauncher && state.settings.clientId) mode = "direct";
    render();
    logLines = (await api("/api/logs").catch(() => [])) || [];
    await loadProfiles();
  }
  connect();
})();
