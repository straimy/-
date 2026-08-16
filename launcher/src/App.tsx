import { useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type BootstrapInfo = { launcherVersion: string; gameVersion: string; channel: string; runtime: string; server: string };
type UpdatePlan = { gameVersion: string; runtime: string; files: Array<{ path: string; url: string; size: number; reason: string }>; totalBytes: number; checkedFiles: number };
type SyncReport = { gameVersion: string; updatedFiles: number; downloadedBytes: number; elapsedMs: number };
type UpdateProgress = { stage: string; currentFile: string; downloadedBytes: number; totalBytes: number; speedBytesPerSecond: number };
type RuntimeCheck = { ready: boolean; minecraftVersion: string; forgeVersion: string; java: null | { path: string; version: string; major: number; compatible: boolean; source: string }; missing: string[]; versionProfile: string; gameDirectory: string };
type RuntimeInstallReport = { installed: boolean; downloadedBytes: number; minecraftVersion: string; forgeVersion: string; runtime: RuntimeCheck };
type RuntimeInstallProgress = { stage: string; currentFile: string; downloadedBytes: number; totalBytes: number };
type LocalInstallReport = { version: string; installedFiles: string[]; skippedFiles: string[]; resourcePackEnabled: boolean };
type MinecraftProfile = { id: string; name: string };
type MicrosoftAuthStatus = { authenticated: boolean; expiresInSeconds: number; refreshAvailable: boolean; minecraftProfile: MinecraftProfile | null };
type LaunchResult = { started: boolean; pid: number; profileName: string; profileId: string };
type LaunchOptions = { ramMb: number; width: number; height: number; fullscreen: boolean };

const nav = ["Главная", "Новости", "Настройки"] as const;
const STORAGE_INSTALL_DIR = "ggo.installDir";
const STORAGE_MANIFEST_URL = "ggo.manifestUrl";
const STORAGE_LOCAL_PACKAGE = "ggo.localPackageZip";
const STORAGE_LOCAL_RESOURCE_PACK = "ggo.localResourcePackZip";
const STORAGE_JAVA = "ggo.javaPath";
const STORAGE_RAM = "ggo.ramMb";
const STORAGE_WIDTH = "ggo.width";
const STORAGE_HEIGHT = "ggo.height";
const STORAGE_FULLSCREEN = "ggo.fullscreen";

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const unit = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const amount = value / Math.pow(1024, unit);
  return `${amount.toFixed(unit === 0 ? 0 : amount >= 100 ? 0 : 1)} ${units[unit]}`;
}

const emptyAuth: MicrosoftAuthStatus = { authenticated: false, expiresInSeconds: 0, refreshAvailable: false, minecraftProfile: null };

export default function App() {
  const [page, setPage] = useState<(typeof nav)[number]>("Главная");
  const [status, setStatus] = useState("Запуск launcher core…");
  const [busy, setBusy] = useState(false);
  const [plan, setPlan] = useState<UpdatePlan | null>(null);
  const [progress, setProgress] = useState<UpdateProgress | null>(null);
  const [runtime, setRuntime] = useState<RuntimeCheck | null>(null);
  const [auth, setAuth] = useState<MicrosoftAuthStatus>(emptyAuth);
  const [installDir, setInstallDir] = useState(() => localStorage.getItem(STORAGE_INSTALL_DIR) ?? "");
  const [manifestUrl, setManifestUrl] = useState(() => localStorage.getItem(STORAGE_MANIFEST_URL) ?? "");
  const [localPackageZip, setLocalPackageZip] = useState(() => localStorage.getItem(STORAGE_LOCAL_PACKAGE) ?? "");
  const [localResourcePackZip, setLocalResourcePackZip] = useState(() => localStorage.getItem(STORAGE_LOCAL_RESOURCE_PACK) ?? "");
  const [javaPath, setJavaPath] = useState(() => localStorage.getItem(STORAGE_JAVA) ?? "");
  const [ramMb, setRamMb] = useState(() => Number(localStorage.getItem(STORAGE_RAM) ?? 4096));
  const [width, setWidth] = useState(() => Number(localStorage.getItem(STORAGE_WIDTH) ?? 1280));
  const [height, setHeight] = useState(() => Number(localStorage.getItem(STORAGE_HEIGHT) ?? 720));
  const [fullscreen, setFullscreen] = useState(() => localStorage.getItem(STORAGE_FULLSCREEN) === "true");
  const [info, setInfo] = useState<BootstrapInfo>({ launcherVersion: "0.1.0", gameVersion: "v29", channel: "beta", runtime: "minecraft-forge", server: "31.77.232.254:24842" });

  const progressPercent = useMemo(() => !progress?.totalBytes ? (busy ? 2 : 0) : Math.min(100, Math.round((progress.downloadedBytes / progress.totalBytes) * 100)), [progress, busy]);
  const launchOptions: LaunchOptions = { ramMb, width, height, fullscreen };

  useEffect(() => {
    void invoke<BootstrapInfo>("bootstrap_info").then((next) => { setInfo(next); setStatus("Launcher core готов"); }).catch(() => setStatus("UI preview: backend недоступен"));
    void invoke<MicrosoftAuthStatus>("microsoft_auth_status").then(setAuth).catch(() => undefined);
    if (!localStorage.getItem(STORAGE_INSTALL_DIR)) {
      void invoke<string>("default_install_dir").then((path) => setInstallDir(path)).catch(() => undefined);
    }
    let stopUpdate: (() => void) | undefined;
    let stopRuntime: (() => void) | undefined;
    void listen<UpdateProgress>("ggo-update-progress", (event) => {
      setProgress(event.payload);
      const file = event.payload.currentFile ? ` · ${event.payload.currentFile}` : "";
      setStatus(`${event.payload.stage}${file}`);
    }).then((unlisten) => { stopUpdate = unlisten; });
    void listen<RuntimeInstallProgress>("ggo-runtime-install-progress", (event) => {
      const payload = event.payload;
      setProgress({ ...payload, speedBytesPerSecond: 0 });
      const file = payload.currentFile ? ` · ${payload.currentFile}` : "";
      setStatus(`Runtime: ${payload.stage}${file}`);
    }).then((unlisten) => { stopRuntime = unlisten; });
    return () => { stopUpdate?.(); stopRuntime?.(); };
  }, []);

  useEffect(() => { if (installDir) localStorage.setItem(STORAGE_INSTALL_DIR, installDir); }, [installDir]);
  useEffect(() => { localStorage.setItem(STORAGE_MANIFEST_URL, manifestUrl); }, [manifestUrl]);
  useEffect(() => { localStorage.setItem(STORAGE_LOCAL_PACKAGE, localPackageZip); }, [localPackageZip]);
  useEffect(() => { localStorage.setItem(STORAGE_LOCAL_RESOURCE_PACK, localResourcePackZip); }, [localResourcePackZip]);
  useEffect(() => { localStorage.setItem(STORAGE_JAVA, javaPath); }, [javaPath]);
  useEffect(() => { localStorage.setItem(STORAGE_RAM, String(ramMb)); }, [ramMb]);
  useEffect(() => { localStorage.setItem(STORAGE_WIDTH, String(width)); }, [width]);
  useEffect(() => { localStorage.setItem(STORAGE_HEIGHT, String(height)); }, [height]);
  useEffect(() => { localStorage.setItem(STORAGE_FULLSCREEN, String(fullscreen)); }, [fullscreen]);

  function ensureInstallDir() {
    if (!installDir.trim()) { setStatus("Выбери папку установки"); setPage("Настройки"); return false; }
    return true;
  }

  async function pickInstallDir() {
    const selected = await invoke<string | null>("pick_install_dir");
    if (selected) setInstallDir(selected);
  }

  async function pickZip(setter: (value: string) => void) {
    const selected = await invoke<string | null>("pick_zip_file");
    if (selected) setter(selected);
  }

  async function loginMicrosoft() {
    setBusy(true);
    setStatus("Открываю вход Microsoft в системном браузере…");
    try {
      const next = await invoke<MicrosoftAuthStatus>("microsoft_login");
      setAuth(next);
      setStatus(next.minecraftProfile ? `Minecraft подключён · ${next.minecraftProfile.name}` : "Microsoft вход выполнен");
    } catch (error) { setStatus(`Ошибка входа: ${String(error)}`); }
    finally { setBusy(false); }
  }

  async function logoutMicrosoft() {
    await invoke("microsoft_logout");
    setAuth(emptyAuth);
    setStatus("Microsoft/Minecraft сессия удалена из памяти лаунчера");
  }

  async function inspectRuntime() {
    if (!ensureInstallDir()) return null;
    const next = await invoke<RuntimeCheck>("check_runtime", { installDir: installDir.trim(), customJava: javaPath.trim() || null });
    setRuntime(next);
    setStatus(next.ready ? `Runtime готов · Java ${next.java?.version}` : `Runtime не готов · отсутствует ${next.missing.length}`);
    return next;
  }

  async function installRuntime() {
    if (!ensureInstallDir()) return null;
    setProgress(null);
    setStatus("Устанавливаю GunGlory Runtime v1…");
    try {
      const report = await invoke<RuntimeInstallReport>("install_runtime", { installDir: installDir.trim(), customJava: javaPath.trim() || null });
      setRuntime(report.runtime);
      setStatus(`Runtime готов · Minecraft ${report.minecraftVersion} · Forge ${report.forgeVersion}`);
      return report.runtime;
    } catch (error) { setStatus(`Ошибка установки runtime: ${String(error)}`); return null; }
  }

  async function installLocalGgo(showMissing = true) {
    if (!ensureInstallDir()) return false;
    if (!localPackageZip.trim() || !localResourcePackZip.trim()) {
      if (showMissing) { setStatus("Выбери ZIP сборки v29 и ZIP Resource Pack v5"); setPage("Настройки"); }
      return false;
    }
    setStatus("Проверяю и устанавливаю локальную GGO v29…");
    try {
      const report = await invoke<LocalInstallReport>("install_local_ggo", { packageZip: localPackageZip.trim(), resourcePackZip: localResourcePackZip.trim(), installDir: installDir.trim() });
      setStatus(`GGO ${report.version} готова · установлено ${report.installedFiles.length} · уже актуально ${report.skippedFiles.length}${report.resourcePackEnabled ? " · RP включён" : ""}`);
      return true;
    } catch (error) { setStatus(`Ошибка локальной установки GGO: ${String(error)}`); return false; }
  }

  async function checkFiles() {
    if (!ensureInstallDir() || !manifestUrl.trim()) { setStatus("Manifest пока не задан — локальная v29 проверяется при импорте"); setPage("Настройки"); return; }
    setBusy(true);
    try {
      const next = await invoke<UpdatePlan>("check_game", { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(next);
      setStatus(next.files.length === 0 ? `Все ${next.checkedFiles} файлов актуальны` : `Нужно обновить ${next.files.length} файлов · ${formatBytes(next.totalBytes)}`);
    } catch (error) { setStatus(`Ошибка проверки: ${String(error)}`); }
    finally { setBusy(false); }
  }

  async function syncFiles(repair = false) {
    if (!ensureInstallDir()) return false;
    if (!manifestUrl.trim()) {
      if (localPackageZip.trim() && localResourcePackZip.trim()) return installLocalGgo(false);
      setStatus("Manifest URL не задан — использую уже лежащие локально игровые файлы");
      return true;
    }
    setProgress(null); setRuntime(null);
    try {
      const report = await invoke<SyncReport>(repair ? "repair_game" : "sync_game", { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(null);
      setProgress({ stage: "complete", currentFile: "", downloadedBytes: report.downloadedBytes, totalBytes: report.downloadedBytes, speedBytesPerSecond: 0 });
      return true;
    } catch (error) { setStatus(`Ошибка обновления: ${String(error)}`); return false; }
  }

  async function play() {
    if (!ensureInstallDir()) return;
    if (!auth.minecraftProfile) { setStatus("Сначала войди в Microsoft/Minecraft"); await loginMicrosoft(); return; }
    setBusy(true);
    setStatus("Подготавливаю GunGloryOnline…");
    try {
      if (!(await syncFiles(false))) return;
      let next = await invoke<RuntimeCheck>("check_runtime", { installDir: installDir.trim(), customJava: javaPath.trim() || null });
      setRuntime(next);
      if (!next.ready) {
        setStatus("Runtime отсутствует — устанавливаю автоматически…");
        const installed = await installRuntime();
        if (!installed) return;
        next = installed;
      }
      setStatus(`Запускаю GunGloryOnline · ${auth.minecraftProfile.name}…`);
      const result = await invoke<LaunchResult>("launch_minecraft", { installDir: installDir.trim(), customJava: javaPath.trim() || null, options: launchOptions });
      setStatus(`Игра запущена · PID ${result.pid} · ${result.profileName}`);
    } catch (error) { setStatus(`Ошибка запуска: ${String(error)}`); }
    finally { setBusy(false); }
  }

  return <main className="shell">
    <aside className="sidebar"><div className="brandMark">GGO</div><div className="nav">{nav.map((item) => <button key={item} className={page === item ? "navButton active" : "navButton"} onClick={() => setPage(item)}>{item}</button>)}</div><div className="channel">CHANNEL / {info.channel.toUpperCase()}</div></aside>
    <section className="content">
      <header className="topbar"><span>GUNGLORYONLINE</span><span>{auth.minecraftProfile ? `MC / ${auth.minecraftProfile.name}` : "MICROSOFT / OFFLINE"}</span><a href="https://t.me/GunGloryOnline" target="_blank" rel="noreferrer">TELEGRAM ↗</a></header>
      {page === "Главная" && <section className="hero"><div className="heroNoise"/><div className="eyebrow">GUNGLORY RUNTIME v1</div><h1>GUN<br/>GLORY<br/><span>ONLINE</span></h1><p className="version">GGO v29 · Forge 47.4.10 · Java 17</p><div className="playRow"><button className="playButton" disabled={busy} onClick={() => void play()}>{busy ? "ПОДГОТОВКА…" : "ИГРАТЬ"}</button><div className="serverCard"><span className="dot"/><div><small>СЕРВЕР</small><strong>{info.server}</strong></div></div></div><div className="actionsRow"><button className="secondaryButton" disabled={busy} onClick={() => { setBusy(true); void installLocalGgo().finally(() => setBusy(false)); }}>Установить GGO v29</button><button className="secondaryButton" disabled={busy} onClick={() => { setBusy(true); void installRuntime().finally(() => setBusy(false)); }}>Установить runtime</button><button className="secondaryButton" disabled={busy} onClick={() => void inspectRuntime()}>Проверить runtime</button>{auth.authenticated ? <button className="secondaryButton" disabled={busy} onClick={() => void logoutMicrosoft()}>Выйти {auth.minecraftProfile?.name ?? "Microsoft"}</button> : <button className="secondaryButton" disabled={busy} onClick={() => void loginMicrosoft()}>Войти Microsoft</button>}</div>{(busy || progress) && <div className="progressWrap"><div className="progressTrack"><span style={{width:`${progressPercent}%`}}/></div><div className="progressMeta"><span>{progressPercent}% {progress?.currentFile ?? ""}</span><span>{progress?.speedBytesPerSecond ? `${formatBytes(progress.speedBytesPerSecond)}/s` : ""}</span></div></div>}{plan && plan.files.length > 0 && <div className="planHint">К обновлению: {plan.files.length} · {formatBytes(plan.totalBytes)}</div>}{runtime && <div className="newsCard"><small>RUNTIME STATUS</small><strong>{runtime.ready ? "Runtime готов" : "Требуется подготовка"}</strong><p>Java: {runtime.java ? `${runtime.java.version} (${runtime.java.source})` : "Java 17 не найдена"}. Компонентов не хватает: {runtime.missing.length}.</p></div>}</section>}
      {page === "Новости" && <section className="panel"><p className="eyebrow">НОВОСТИ</p><h2>Тестовый launcher</h2><article><time>17.08.2026</time><strong>Первый устанавливаемый билд</strong><p>Нативный выбор папки и архивов, локальная GGO v29, Runtime v1 и автоматический запуск собраны в один launcher.</p></article></section>}
      {page === "Настройки" && <section className="panel"><p className="eyebrow">НАСТРОЙКИ</p><h2>Клиент</h2><div className="setting"><span>Runtime</span><b>{info.runtime}</b></div><div className="setting"><span>Minecraft</span><b>{auth.minecraftProfile ? auth.minecraftProfile.name : "Не подключён"}</b></div><label className="field"><span>RAM, MB</span><input type="number" min="1024" max="32768" value={ramMb} onChange={(e)=>setRamMb(Number(e.target.value))}/></label><label className="field"><span>Ширина</span><input type="number" min="640" max="7680" value={width} onChange={(e)=>setWidth(Number(e.target.value))}/></label><label className="field"><span>Высота</span><input type="number" min="480" max="4320" value={height} onChange={(e)=>setHeight(Number(e.target.value))}/></label><label className="field"><span>Fullscreen</span><input type="checkbox" checked={fullscreen} onChange={(e)=>setFullscreen(e.target.checked)}/></label><label className="field"><span>Папка установки</span><div style={{display:"flex",gap:8}}><input style={{flex:1}} value={installDir} onChange={(e)=>setInstallDir(e.target.value)} placeholder="GunGloryOnline game data"/><button type="button" className="secondaryButton" onClick={() => void pickInstallDir()}>Выбрать</button></div></label><label className="field"><span>GGO v29 package ZIP</span><div style={{display:"flex",gap:8}}><input style={{flex:1}} value={localPackageZip} onChange={(e)=>setLocalPackageZip(e.target.value)} placeholder="Выбери архив сборки"/><button type="button" className="secondaryButton" onClick={() => void pickZip(setLocalPackageZip)}>Обзор</button></div></label><label className="field"><span>Resource Pack v5 ZIP</span><div style={{display:"flex",gap:8}}><input style={{flex:1}} value={localResourcePackZip} onChange={(e)=>setLocalResourcePackZip(e.target.value)} placeholder="Выбери архив resource pack"/><button type="button" className="secondaryButton" onClick={() => void pickZip(setLocalResourcePackZip)}>Обзор</button></div></label><label className="field"><span>Java 17 (необязательно)</span><input value={javaPath} onChange={(e)=>setJavaPath(e.target.value)} placeholder="Автопоиск через JAVA_HOME / PATH"/></label><label className="field"><span>Beta manifest URL (необязательно)</span><input value={manifestUrl} onChange={(e)=>setManifestUrl(e.target.value)} placeholder="Позже подключим VDS/CDN"/></label><div className="actionsRow"><button className="secondaryButton" disabled={busy} onClick={() => { setBusy(true); void installLocalGgo().finally(() => setBusy(false)); }}>Проверить и установить v29</button><button className="secondaryButton" disabled={busy || !manifestUrl.trim()} onClick={() => void checkFiles()}>Проверить manifest</button><button className="secondaryButton" disabled={busy || !manifestUrl.trim()} onClick={() => { setBusy(true); void syncFiles(true).finally(() => setBusy(false)); }}>Repair</button></div><p className="muted">Пути можно выбрать системными диалогами. Без manifest URL launcher работает в локальном тестовом режиме; позже те же команды переключатся на VDS/CDN.</p></section>}
      <footer className="statusbar"><div><span className={busy ? "statusDot busy" : "statusDot"}/><span>{status}</span></div><span>Launcher {info.launcherVersion}</span></footer>
    </section>
  </main>;
}
