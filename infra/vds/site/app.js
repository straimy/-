const API = "/api/v1";
const params = new URLSearchParams(location.search);
const activationCode = (params.get("code") || "").trim().toUpperCase();
let accessToken = "";
let player = null;

const $ = (id) => document.getElementById(id);
const statusEl = $("status");
const activationBox = $("activationBox");
const activationCodeEl = $("activationCode");
const loginForm = $("loginForm");
const registerForm = $("registerForm");
const authTabs = $("authTabs");
const profilePanel = $("profilePanel");
const profileName = $("profileName");
const profileId = $("profileId");
const approveButton = $("approveButton");
const skinFile = $("skinFile");
const skinPreview = $("skinPreview");
const skinPreviewImage = $("skinPreviewImage");
const skinHash = $("skinHash");
const downloadVersion = $("downloadVersion");
const downloadState = $("downloadState");
const downloadWindows = $("downloadWindows");
const downloadLinux = $("downloadLinux");

function setStatus(message, bad = false) {
  statusEl.textContent = message;
  statusEl.classList.toggle("bad", bad);
}

function enableDownload(anchor, url, fileName) {
  if (!anchor || !url) return;
  anchor.href = url;
  anchor.removeAttribute("aria-disabled");
  anchor.setAttribute("download", fileName || "");
}

async function loadPublicDownloads() {
  if (!downloadState) return;
  try {
    const response = await fetch(`/downloads.json?t=${Date.now()}`, { cache: "no-store" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    if (!data || data.schemaVersion !== 1 || !data.version) throw new Error("invalid downloads metadata");
    downloadVersion.textContent = `Launcher ${data.version}`;
    enableDownload(downloadWindows, data.windows?.url, data.windows?.fileName);
    enableDownload(downloadLinux, data.linux?.url, data.linux?.fileName);
    downloadState.textContent = "Latest signed build";
  } catch {
    downloadState.textContent = "Download build is being prepared";
  }
}

[downloadWindows, downloadLinux].forEach((anchor) => {
  anchor?.addEventListener("click", (event) => {
    if (anchor.getAttribute("aria-disabled") === "true") event.preventDefault();
  });
});

function showTab(name) {
  loginForm.classList.toggle("hidden", name !== "login");
  registerForm.classList.toggle("hidden", name !== "register");
  [...authTabs.querySelectorAll("button")].forEach((button) => button.classList.toggle("active", button.dataset.tab === name));
}

async function request(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(`${API}${path}`, { ...options, headers });
  let data = null;
  try { data = await response.json(); } catch { data = null; }
  if (!response.ok) throw new Error(data?.detail || `HTTP ${response.status}`);
  return data;
}

async function uploadRequest(path, formData) {
  const response = await fetch(`${API}${path}`, {
    method: "POST",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    body: formData
  });
  let data = null;
  try { data = await response.json(); } catch { data = null; }
  if (!response.ok) throw new Error(data?.detail || `HTTP ${response.status}`);
  return data;
}

function applySession(data) {
  accessToken = data.access_token || "";
  player = data.player || null;
  if (!accessToken || !player) throw new Error("Invalid account response");
  authTabs.classList.add("hidden");
  loginForm.classList.add("hidden");
  registerForm.classList.add("hidden");
  profilePanel.classList.remove("hidden");
  profileName.textContent = player.display_name;
  profileId.textContent = player.id;
  approveButton.classList.toggle("hidden", !activationCode);
  setStatus(activationCode ? "Signed in. Approve the launcher to continue." : "Signed in to GGO.");
  void refreshProfile();
}

function applySkinPreview(me) {
  if (me.skin_url && me.skin_hash) {
    skinPreview.classList.remove("hidden");
    skinPreviewImage.src = `${me.skin_url}?h=${encodeURIComponent(me.skin_hash)}`;
    skinHash.textContent = me.skin_hash;
  } else {
    skinPreview.classList.add("hidden");
    skinPreviewImage.removeAttribute("src");
    skinHash.textContent = "";
  }
}

async function refreshProfile() {
  if (!accessToken) return;
  try {
    const me = await request("/me");
    player = { id: me.id, display_name: me.display_name };
    profileName.textContent = me.display_name;
    profileId.textContent = me.id;
    document.querySelectorAll("[data-skin]").forEach((button) => button.classList.toggle("active", button.dataset.skin === me.skin_source));
    applySkinPreview(me);
  } catch (error) {
    setStatus(error.message, true);
  }
}

if (activationCode) {
  activationBox.classList.remove("hidden");
  activationCodeEl.textContent = activationCode;
}

authTabs.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-tab]");
  if (button) showTab(button.dataset.tab);
});

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setStatus("Signing in…");
  try {
    const data = await request("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email: $("loginEmail").value.trim(), password: $("loginPassword").value })
    });
    applySession(data);
  } catch (error) {
    setStatus(error.message, true);
  }
});

registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setStatus("Creating account…");
  try {
    const data = await request("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        display_name: $("registerName").value.trim(),
        email: $("registerEmail").value.trim(),
        password: $("registerPassword").value
      })
    });
    applySession(data);
  } catch (error) {
    setStatus(error.message, true);
  }
});

approveButton.addEventListener("click", async () => {
  if (!activationCode || !accessToken) return;
  approveButton.disabled = true;
  setStatus("Approving launcher…");
  try {
    await request("/auth/device/approve", { method: "POST", body: JSON.stringify({ user_code: activationCode }) });
    approveButton.textContent = "APPROVED ✓";
    setStatus("Launcher approved. You can return to GunGloryOnline.");
  } catch (error) {
    approveButton.disabled = false;
    setStatus(error.message, true);
  }
});

$("logoutButton").addEventListener("click", () => {
  accessToken = "";
  player = null;
  profilePanel.classList.add("hidden");
  authTabs.classList.remove("hidden");
  skinPreview.classList.add("hidden");
  showTab("login");
  setStatus("Signed out.");
});

document.querySelectorAll("[data-skin]").forEach((button) => {
  button.addEventListener("click", async () => {
    if (!accessToken) return;
    try {
      await request("/me/skin/source", { method: "PUT", body: JSON.stringify({ source: button.dataset.skin }) });
      await refreshProfile();
      setStatus(`Skin source: ${button.dataset.skin}`);
    } catch (error) {
      setStatus(error.message, true);
    }
  });
});

skinFile.addEventListener("change", async () => {
  const file = skinFile.files?.[0];
  if (!file || !accessToken) return;
  setStatus("Uploading GGO skin…");
  skinFile.disabled = true;
  try {
    if (file.type && file.type !== "image/png") throw new Error("Choose a PNG skin.");
    if (file.size > 512 * 1024) throw new Error("Skin PNG must be 512 KiB or smaller.");
    const body = new FormData();
    body.append("file", file, file.name || "skin.png");
    const uploaded = await uploadRequest("/me/skin", body);
    setStatus("GGO skin uploaded and selected.");
    applySkinPreview(uploaded);
    await refreshProfile();
  } catch (error) {
    setStatus(error.message, true);
  } finally {
    skinFile.disabled = false;
    skinFile.value = "";
  }
});

void loadPublicDownloads();
