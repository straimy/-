import fs from "node:fs";

const path = new URL("../src/App.tsx", import.meta.url);
let src = fs.readFileSync(path, "utf8");

const oldInstallLabel = '  const installLabel=gameInstalled?(updateAvailable?t.updateGame:t.repair):t.install;';
const newInstallLabel = '  const installLabel=gameInstalled?(updateAvailable?t.updateGame:t.play):t.install;';
if (!src.includes(oldInstallLabel)) {
  throw new Error("App.tsx installLabel contract changed; update release UI transform");
}
src = src.replace(oldInstallLabel, newInstallLabel);

const oldHomeActions = '<div className="home-actions"><button className="play-button" disabled={busy||!gameInstalled||updateAvailable} onClick={()=>void launch(false)}>{busy?t.preparing:t.play}</button><button className="training-button" disabled={busy||!gameInstalled} onClick={()=>void launch(true)}>{t.training}<small>{t.trainingHint}</small></button></div>';
const newHomeActions = '<div className="home-actions"><button className="play-button" disabled={busy||checkingGame} onClick={()=>void (gameInstalled&&!updateAvailable?launch(false):installGame())}>{busy?t.preparing:installLabel}</button><button className="training-button" disabled={busy||!gameInstalled||updateAvailable} onClick={()=>void launch(true)}>{t.training}<small>{t.trainingHint}</small></button></div>';
if (!src.includes(oldHomeActions)) {
  throw new Error("App.tsx home action contract changed; update release UI transform");
}
src = src.replace(oldHomeActions, newHomeActions);

const oldInstallCard = '<div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div>{(!gameInstalled||updateAvailable)&&<button disabled={busy} onClick={()=>void installGame()}>{installLabel}</button>}{gameInstalled&&!updateAvailable&&<button className="repair-link" disabled={busy} onClick={()=>void repairGame()}>{t.repair}</button>}</div>';
const newInstallCard = '<div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div></div>';
if (!src.includes(oldInstallCard)) {
  throw new Error("App.tsx install card contract changed; update release UI transform");
}
src = src.replace(oldInstallCard, newInstallCard);

fs.writeFileSync(path, src);
console.log("Release UI state machine applied: INSTALL/UPDATE/PLAY ONLINE share one primary button; repair remains in Diagnostics.");
