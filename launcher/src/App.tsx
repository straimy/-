import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";

type BootstrapInfo = {
  launcherVersion: string;
  gameVersion: string;
  channel: string;
  runtime: string;
  server: string;
};

const nav = ["Главная", "Новости", "Настройки"] as const;

export default function App() {
  const [page, setPage] = useState<(typeof nav)[number]>("Главная");
  const [status, setStatus] = useState("Готово к первичной настройке");
  const [info, setInfo] = useState<BootstrapInfo>({
    launcherVersion: "0.1.0",
    gameVersion: "v0.4 Beta",
    channel: "beta",
    runtime: "minecraft-forge",
    server: "31.77.232.254:24842"
  });

  async function refreshBootstrapInfo() {
    try {
      const next = await invoke<BootstrapInfo>("bootstrap_info");
      setInfo(next);
      setStatus("Launcher core отвечает");
    } catch {
      setStatus("UI preview: backend недоступен");
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
              <button className="playButton" onClick={refreshBootstrapInfo}>ИГРАТЬ</button>
              <div className="serverCard"><span className="dot" /><div><small>СЕРВЕР</small><strong>{info.server}</strong></div></div>
            </div>
            <div className="newsCard"><small>ПОСЛЕДНЕЕ ОБНОВЛЕНИЕ</small><strong>GunGloryOnline v0.4 Beta</strong><p>Финальный weapon pass подготовлен. Launcher bootstrap использует отдельный runtime-layer.</p></div>
          </section>
        )}

        {page === "Новости" && (
          <section className="panel"><p className="eyebrow">НОВОСТИ</p><h2>Лента обновлений</h2><article><time>16.08.2026</time><strong>Launcher development started</strong><p>Первый отдельный клиент GunGloryOnline: updater, runtime adapters и собственная identity-модель.</p></article></section>
        )}

        {page === "Настройки" && (
          <section className="panel"><p className="eyebrow">НАСТРОЙКИ</p><h2>Клиент</h2><div className="setting"><span>RAM</span><b>4 GB</b></div><div className="setting"><span>Разрешение</span><b>1920 × 1080</b></div><div className="setting"><span>Fullscreen</span><b>Выкл.</b></div><div className="setting"><span>Runtime</span><b>{info.runtime}</b></div><p className="muted">Поля пока являются UI-заглушками; сохранение настроек будет подключено отдельным core-сервисом.</p></section>
        )}

        <footer className="statusbar"><div><span className="statusDot" /><span>{status}</span></div><span>Launcher {info.launcherVersion}</span></footer>
      </section>
    </main>
  );
}
