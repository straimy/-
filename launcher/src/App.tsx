import { useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type BootstrapInfo = { launcherVersion: string; gameVersion: string; channel: string; runtime: string; server: string };
type UpdatePlan = { gameVersion: string; runtime: string; files: Array<{ path: string; url: string; size: number; reason: string }>; totalBytes: number; checkedFiles: number };
type SyncReport = { gameVersion: string; updatedFiles: number; downloadedBytes: number; elapsedMs: number };
type UpdateProgress = { stage: string; currentFile: string; downloadedBytes: number; totalBytes: number; speedBytesPerSecond: number };
type RuntimeCheck = { ready: boolean; minecraftVersion: string; forgeVersion: string; java: null | { path: string; version: string; major: number; compatible: boolean; source: string }; missing: string[]; versionProfile: string; gameDirectory: string };

const nav = ["Главная", "Новости", "Настройки"] as const;
const STORAGE_INSTALL_DIR = "ggo.installDir";
const STORAGE_MANIFEST_URL = "ggo.manifestUrl";
const STORAGE_JAVA = "ggo.javaPath";

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const unit = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const amount = value / Math.pow(1024, unit);
  return `${amount.toFixed(unit === 0 ? 0 : amount >= 100 ? 0 : 1)} ${units[unit]}`;
}

export default function App() {
  const [page, setPage] = useState<(typeof nav)[number]>("Главная");
  const [status, setStatus] = useState("Запуск launcher core…");
  const [busy, setBusy] = useState(false);
  const [plan, setPlan] = useState<UpdatePlan | null>(null);
  const [progress, setProgress] = useState<UpdateProgress | null>(null);
  const [runtime, setRuntime] = useState<RuntimeCheck | null>(null);
  const [installDir, setInstallDir] = useState(() => localStorage.getItem(STORAGE_INSTALL_DIR) ?? "");
  const [manifestUrl, setManifestUrl] = useState(() => localStorage.getItem(STORAGE_MANIFEST_URL) ?? "");
  const [javaPath, setJavaPath] = useState(() => localStorage.getItem(STORAGE_JAVA) ?? "");
  const [info, setInfo] = useState<BootstrapInfo>({ launcherVersion: "0.1.0", gameVersion: "v0.4 Beta", channel: "beta", runtime: "minecraft-forge", server: "31.77.232.254:24842" });

  const progressPercent = useMemo(() => !progress?.totalBytes ? (busy ? 2 : 0) : Math.min(100, Math.round((progress.downloadedBytes / progress.totalBytes) * 100)), [progress, busy]);

  useEffect(() => {
    void invoke<BootstrapInfo>("bootstrap_info").then((next) => { setInfo(next); setStatus("Launcher core готов"); }).catch(() => setStatus("UI preview: backend недоступен"));
    let stop: (() => void) | undefined;
    void listen<UpdateProgress>("ggo-update-progress", (event) => {
      setProgress(event.payload);
      const file = event.payload.currentFile ? ` · ${event.payload.currentFile}` : "";
      setStatus(`${event.payload.stage}${file}`);
    }).then((unlisten) => { stop = unlisten; });
    return () => stop?.();
  }, []);

  useEffect(() => { localStorage.setItem(STORAGE_INSTALL_DIR, installDir); }, [installDir]);
  useEffect(() => { localStorage.setItem(STORAGE_MANIFEST_URL, manifestUrl); }, [manifestUrl]);
  useEffect(() => { localStorage.setItem(STORAGE_JAVA, javaPath); }, [javaPath]);

  function ensureSettings() {
    if (!installDir.trim()) { setStatus("Укажи папку установки"); setPage("Настройки"); return false; }
    return true;
  }

  async function inspectRuntime() {
    if (!ensureSettings()) return null;
    const next = await invoke<RuntimeCheck>("check_runtime", { installDir: installDir.trim(), customJava: javaPath.trim() || null });
    setRuntime(next);
    setStatus(next.ready ? `Runtime готов · Java ${next.java?.version}` : `Runtime не готов · отсутствует ${next.missing.length}`);
    return next;
  }

  async function checkFiles() {
    if (!ensureSettings() || !manifestUrl.trim()) { setStatus("Укажи beta manifest URL"); setPage("Настройки"); return; }
    setBusy(true);
    try {
      const next = await invoke<UpdatePlan>("check_game", { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(next);
      setStatus(next.files.length === 0 ? `Все ${next.checkedFiles} файлов актуальны` : `Нужно обновить ${next.files.length} файлов · ${formatBytes(next.totalBytes)}`);
    } catch (error) { setStatus(`Ошибка проверки: ${String(error)}`); } finally { setBusy(false); }
  }

  async function syncFiles(repair = false) {
    if (!ensureSettings() || !manifestUrl.trim()) { setStatus("Укажи beta manifest URL"); setPage("Настройки"); return; }
    setBusy(true); setProgress(null); setRuntime(null);
    try {
      const report = await invoke<SyncReport>(repair ? "repair_game" : "sync_game", { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(null);
      setProgress({ stage: "complete", currentFile: "", downloadedBytes: report.downloadedBytes, totalBytes: report.downloadedBytes, speedBytesPerSecond: 0 });
      const next = await invoke<RuntimeCheck>("check_runtime", { installDir: installDir.trim(), customJava: javaPath.trim() || null });
      setRuntime(next);
      setStatus(next.ready ? "Игра и runtime готовы к авторизации" : `Файлы синхронизированы, runtime неполный: ${next.missing.length}`);
    } catch (error) { setStatus(`Ошибка: ${String(error)}`); } finally { setBusy(false); }
  }

  return <main className="shell">
    <aside className="sidebar"><div className="brandMark">GGO</div><div className="nav">{nav.map((item) => <button key={item} className={page === item ? "navButton active" : "navButton"} onClick={() => setPage(item)}>{item}</button>)}</div><div className="channel">CHANNEL / {info.channel.toUpperCase()}</div></aside>
    <section className="content">
      <header className="topbar"><span>GUNGLORYONLINE</span><a href="https://t.me/GunGloryOnline" target="_blank" rel="noreferrer">TELEGRAM ↗</a></header>
      {page === "Главная" && <section className="hero"><div className="heroNoise"/><div className="eyebrow">GUNGLORY RUNTIME v1</div><h1>GUN<br/>GLORY<br/><span>ONLINE</span></h1><p className="version">{info.gameVersion} · Forge 47.4.10 · Java 17</p><div className="playRow"><button className="playButton" disabled={busy} onClick={() => void syncFiles(false)}>{busy ? "ПОДГОТОВКА…" : "ИГРАТЬ"}</button><div className="serverCard"><span className="dot"/><div><small>СЕРВЕР</small><strong>{info.server}</strong></div></div></div><div className="actionsRow"><button className="secondaryButton" disabled={busy} onClick={() => void checkFiles()}>Проверить файлы</button><button className="secondaryButton" disabled={busy} onClick={() => void syncFiles(true)}>Починить игру</button><button className="secondaryButton" disabled={busy} onClick={() => void inspectRuntime()}>Проверить runtime</button></div>{(busy || progress) && <div className="progressWrap"><div className="progressTrack"><span style={{width:`${progressPercent}%`}}/></div><div className="progressMeta"><span>{progressPercent}% {progress?.currentFile ?? ""}</span><span>{progress?.speedBytesPerSecond ? `${formatBytes(progress.speedBytesPerSecond)}/s` : ""}</span></div></div>}{plan && plan.files.length > 0 && <div className="planHint">К обновлению: {plan.files.length} · {formatBytes(plan.totalBytes)}</div>}{runtime && <div className="newsCard"><small>RUNTIME STATUS</small><strong>{runtime.ready ? "Готов к запуску" : "Требуется подготовка"}</strong><p>Java: {runtime.java ? `${runtime.java.version} (${runtime.java.source})` : "Java 17 не найдена"}. Профиль: {runtime.versionProfile}.{runtime.missing.length > 0 ? ` Не хватает: ${runtime.missing.length}.` : ""}</p></div>}</section>}
      {page === "Новости" && <section className="panel"><p className="eyebrow">НОВОСТИ</p><h2>Лента обновлений</h2><article><time>16.08.2026</time><strong>Minecraft Runtime v1</strong><p>Добавлены Java 17 detection, Forge/Minecraft readiness check и подготовка автоподключения к серверу.</p></article></section>}
      {page === "Настройки" && <section className="panel"><p className="eyebrow">НАСТРОЙКИ</p><h2>Клиент</h2><div className="setting"><span>Runtime</span><b>{info.runtime}</b></div><label className="field"><span>Путь установки</span><input value={installDir} onChange={(e)=>setInstallDir(e.target.value)} placeholder="C:\\Games\\GunGloryOnline"/></label><label className="field"><span>Java 17 (необязательно)</span><input value={javaPath} onChange={(e)=>setJavaPath(e.target.value)} placeholder="Автопоиск через JAVA_HOME / PATH"/></label><label className="field"><span>Beta manifest URL</span><input value={manifestUrl} onChange={(e)=>setManifestUrl(e.target.value)} placeholder="https://…/beta.json"/></label><p className="muted">Если Java не указана, launcher сам ищет Java 17. Runtime проверяет Minecraft 1.20.1, Forge 47.4.10, libraries и assets.</p></section>}
      <footer className="statusbar"><div><span className={busy ? "statusDot busy" : "statusDot"}/><span>{status}</span></div><span>Launcher {info.launcherVersion}</span></footer>
    </section>
  </main>;
}
