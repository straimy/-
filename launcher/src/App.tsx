import { useEffect, useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type BootstrapInfo = {
  launcherVersion: string;
  gameVersion: string;
  channel: string;
  runtime: string;
  server: string;
};

type UpdatePlan = {
  gameVersion: string;
  runtime: string;
  files: Array<{ path: string; url: string; size: number; reason: string }>;
  totalBytes: number;
  checkedFiles: number;
};

type SyncReport = {
  gameVersion: string;
  updatedFiles: number;
  downloadedBytes: number;
  elapsedMs: number;
};

type UpdateProgress = {
  stage: string;
  currentFile: string;
  downloadedBytes: number;
  totalBytes: number;
  speedBytesPerSecond: number;
};

const nav = ["Главная", "Новости", "Настройки"] as const;
const STORAGE_INSTALL_DIR = "ggo.installDir";
const STORAGE_MANIFEST_URL = "ggo.manifestUrl";

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const unit = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const amount = value / Math.pow(1024, unit);
  return `${amount.toFixed(unit === 0 ? 0 : amount >= 100 ? 0 : 1)} ${units[unit]}`;
}

function stageLabel(stage: string) {
  const labels: Record<string, string> = {
    "check-complete": "Проверка завершена",
    "repair-check-complete": "Полная проверка завершена",
    downloading: "Загрузка файлов",
    complete: "Файлы готовы"
  };
  return labels[stage] ?? stage;
}

export default function App() {
  const [page, setPage] = useState<(typeof nav)[number]>("Главная");
  const [status, setStatus] = useState("Запуск launcher core…");
  const [busy, setBusy] = useState(false);
  const [plan, setPlan] = useState<UpdatePlan | null>(null);
  const [progress, setProgress] = useState<UpdateProgress | null>(null);
  const [installDir, setInstallDir] = useState(() => localStorage.getItem(STORAGE_INSTALL_DIR) ?? "");
  const [manifestUrl, setManifestUrl] = useState(() => localStorage.getItem(STORAGE_MANIFEST_URL) ?? "");
  const [info, setInfo] = useState<BootstrapInfo>({
    launcherVersion: "0.1.0",
    gameVersion: "v0.4 Beta",
    channel: "beta",
    runtime: "minecraft-forge",
    server: "31.77.232.254:24842"
  });

  const progressPercent = useMemo(() => {
    if (!progress?.totalBytes) return busy ? 2 : 0;
    return Math.min(100, Math.round((progress.downloadedBytes / progress.totalBytes) * 100));
  }, [progress, busy]);

  useEffect(() => {
    void refreshBootstrapInfo();
    let stop: (() => void) | undefined;
    void listen<UpdateProgress>("ggo-update-progress", (event) => {
      setProgress(event.payload);
      const file = event.payload.currentFile ? ` · ${event.payload.currentFile}` : "";
      setStatus(`${stageLabel(event.payload.stage)}${file}`);
    }).then((unlisten) => { stop = unlisten; });
    return () => stop?.();
  }, []);

  useEffect(() => {
    localStorage.setItem(STORAGE_INSTALL_DIR, installDir);
  }, [installDir]);

  useEffect(() => {
    localStorage.setItem(STORAGE_MANIFEST_URL, manifestUrl);
  }, [manifestUrl]);

  async function refreshBootstrapInfo() {
    try {
      const next = await invoke<BootstrapInfo>("bootstrap_info");
      setInfo(next);
      setStatus("Launcher core готов");
    } catch {
      setStatus("UI preview: backend недоступен");
    }
  }

  function ensureUpdateSettings() {
    if (!manifestUrl.trim()) {
      setStatus("Укажи URL beta manifest в настройках");
      setPage("Настройки");
      return false;
    }
    if (!installDir.trim()) {
      setStatus("Укажи папку установки в настройках");
      setPage("Настройки");
      return false;
    }
    return true;
  }

  async function checkFiles() {
    if (!ensureUpdateSettings()) return;
    setBusy(true);
    setProgress(null);
    setStatus("Проверяю файлы…");
    try {
      const next = await invoke<UpdatePlan>("check_game", { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(next);
      setStatus(next.files.length === 0 ? `Все ${next.checkedFiles} файлов актуальны` : `Нужно обновить ${next.files.length} файлов · ${formatBytes(next.totalBytes)}`);
    } catch (error) {
      setStatus(`Ошибка проверки: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  }

  async function syncFiles(repair = false) {
    if (!ensureUpdateSettings()) return;
    setBusy(true);
    setProgress(null);
    setStatus(repair ? "Полная проверка и восстановление…" : "Подготавливаю игру…");
    try {
      const command = repair ? "repair_game" : "sync_game";
      const report = await invoke<SyncReport>(command, { manifestUrl: manifestUrl.trim(), installDir: installDir.trim() });
      setPlan(null);
      setProgress({ stage: "complete", currentFile: "", downloadedBytes: report.downloadedBytes, totalBytes: report.downloadedBytes, speedBytesPerSecond: 0 });
      setStatus(report.updatedFiles === 0 ? "Файлы уже были актуальны" : `Готово · обновлено ${report.updatedFiles} файлов (${formatBytes(report.downloadedBytes)})`);
    } catch (error) {
      setStatus(`Ошибка обновления: ${String(error)}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="brandMark">GGO</div>
        <div className="nav">
          {nav.map((item) => (
            <button key={item} className={page === item ? "navButton active" : "navButton"} onClick={() => setPage(item)}>
              {item}
            </button>
          ))}
        </div>
        <div className="channel">CHANNEL / {info.channel.toUpperCase()}</div>
      </aside>

      <section className="content">
        <header className="topbar">
          <span>GUNGLORYONLINE</span>
          <a href="https://t.me/GunGloryOnline" target="_blank" rel="noreferrer">TELEGRAM ↗</a>
        </header>

        {page === "Главная" && (
          <section className="hero">
            <div className="heroNoise" />
            <div className="eyebrow">GUNGLORY RUNTIME v1</div>
            <h1>GUN<br />GLORY<br /><span>ONLINE</span></h1>
            <p className="version">{info.gameVersion} · Forge 47.4.10 · Java 17</p>
            <div className="playRow">
              <button className="playButton" disabled={busy} onClick={() => void syncFiles(false)}>{busy ? "ПОДГОТОВКА…" : "ИГРАТЬ"}</button>
              <div className="serverCard"><span className="dot" /><div><small>СЕРВЕР</small><strong>{info.server}</strong></div></div>
            </div>
            <div className="actionsRow">
              <button className="secondaryButton" disabled={busy} onClick={() => void checkFiles()}>Проверить файлы</button>
              <button className="secondaryButton" disabled={busy} onClick={() => void syncFiles(true)}>Починить игру</button>
            </div>
            {(busy || progress) && <div className="progressWrap"><div className="progressTrack"><span style={{ width: `${progressPercent}%` }} /></div><div className="progressMeta"><span>{progressPercent}% {progress?.currentFile ?? ""}</span><span>{progress?.speedBytesPerSecond ? `${formatBytes(progress.speedBytesPerSecond)}/s` : ""}</span></div></div>}
            {plan && plan.files.length > 0 && <div className="planHint">К обновлению: {plan.files.length} · {formatBytes(plan.totalBytes)}</div>}
            <div className="newsCard"><small>ПОСЛЕДНЕЕ ОБНОВЛЕНИЕ</small><strong>GunGloryOnline v0.4 Beta</strong><p>Updater проверяет SHA256 и размер, скачивает только изменившиеся файлы и заменяет их после успешной верификации.</p></div>
          </section>
        )}

        {page === "Новости" && (
          <section className="panel"><p className="eyebrow">НОВОСТИ</p><h2>Лента обновлений</h2><article><time>16.08.2026</time><strong>Launcher update engine</strong><p>Подключены manifest diff, проверка SHA256, безопасная замена файлов, прогресс и восстановление игры.</p></article></section>
        )}

        {page === "Настройки" && (
          <section className="panel"><p className="eyebrow">НАСТРОЙКИ</p><h2>Клиент</h2><div className="setting"><span>RAM</span><b>4 GB</b></div><div className="setting"><span>Разрешение</span><b>1920 × 1080</b></div><div className="setting"><span>Fullscreen</span><b>Выкл.</b></div><div className="setting"><span>Runtime</span><b>{info.runtime}</b></div><label className="field"><span>Путь установки</span><input value={installDir} onChange={(event) => setInstallDir(event.target.value)} placeholder="Например: C:\\Games\\GunGloryOnline" /></label><label className="field"><span>Beta manifest URL</span><input value={manifestUrl} onChange={(event) => setManifestUrl(event.target.value)} placeholder="https://…/beta.json" /></label><p className="muted">RAM, разрешение, fullscreen и Java будут подключены на Minecraft runtime-этапе. Путь и manifest уже используются update engine.</p></section>
        )}

        <footer className="statusbar"><div><span className={busy ? "statusDot busy" : "statusDot"} /><span>{status}</span></div><span>Launcher {info.launcherVersion}</span></footer>
      </section>
    </main>
  );
}
