import { useEffect, useState, type ReactNode } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type LauncherUpdateStatus = {
  configured: boolean;
  available: boolean;
  currentVersion: string;
  version: string | null;
  notes: string | null;
};

type LauncherUpdateProgress = {
  downloadedBytes: number;
  totalBytes: number | null;
};

function mb(value: number) {
  return `${(value / 1048576).toFixed(1)} MB`;
}

export default function LauncherUpdateGate({ children }: { children: ReactNode }) {
  const [update, setUpdate] = useState<LauncherUpdateStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<LauncherUpdateProgress | null>(null);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    void invoke<LauncherUpdateStatus>("check_launcher_update")
      .then(setUpdate)
      .catch(() => undefined);
    void listen<LauncherUpdateProgress>("ggo-launcher-update-progress", (event) => {
      setProgress(event.payload);
    }).then((fn) => {
      unlisten = fn;
    });
    return () => unlisten?.();
  }, []);

  async function install() {
    setBusy(true);
    setError(null);
    try {
      const started = await invoke<boolean>("install_launcher_update");
      if (!started) {
        setError("Update is no longer available.");
        setUpdate((value) => value ? { ...value, available: false } : value);
      }
    } catch (value) {
      setError(String(value));
    } finally {
      setBusy(false);
    }
  }

  return <>
    {children}
    {update?.available && <div style={{position:"fixed",right:18,bottom:46,zIndex:9999,width:360,padding:"16px 18px",border:"1px solid rgba(230,55,75,.55)",borderRadius:14,background:"rgba(12,14,18,.97)",boxShadow:"0 18px 60px rgba(0,0,0,.45)",color:"#eef1f6",fontFamily:"inherit"}}>
      <div style={{fontSize:11,letterSpacing:".16em",color:"#e64054",fontWeight:800}}>GGO LAUNCHER UPDATE</div>
      <div style={{fontSize:18,fontWeight:850,marginTop:7}}>v{update.currentVersion} → v{update.version}</div>
      <div style={{fontSize:12,lineHeight:1.5,color:"#8e9aab",marginTop:6}}>{update.notes || "A newer GunGloryOnline Launcher build is ready."}</div>
      {progress && <div style={{fontSize:11,color:"#8e9aab",marginTop:8}}>{mb(progress.downloadedBytes)}{progress.totalBytes ? ` / ${mb(progress.totalBytes)}` : ""}</div>}
      {error && <div style={{fontSize:11,color:"#ff7c8b",marginTop:8}}>{error}</div>}
      <button disabled={busy} onClick={() => void install()} style={{width:"100%",marginTop:12,border:0,borderRadius:9,padding:"11px 14px",fontWeight:850,letterSpacing:".08em",background:"#df3549",color:"white",cursor:busy?"default":"pointer",opacity:busy?.65:1}}>{busy ? "DOWNLOADING…" : "DOWNLOAD LAUNCHER UPDATE"}</button>
      <div style={{fontSize:10,color:"#697586",marginTop:8}}>The package is downloaded only from ggo.kvicloud.ru and verified by SHA-256 before opening.</div>
    </div>}
  </>;
}
