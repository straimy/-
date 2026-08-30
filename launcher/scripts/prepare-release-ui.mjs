import fs from "node:fs";

const path = new URL("../src/App.tsx", import.meta.url);
let src = fs.readFileSync(path, "utf8");

// Legacy release helper kept for older workflows. It must never restore the old split
// PLAY ONLINE / TRAINING launcher surface. Mode selection belongs to the GGO client.
for (const [oldText, newText] of [
  ['play:"PLAY ONLINE"', 'play:"PLAY"'],
  ['play:"ИГРАТЬ ОНЛАЙН"', 'play:"ИГРАТЬ"'],
  ['play:"ГРАТИ ОНЛАЙН"', 'play:"ГРАТИ"'],
]) {
  src = src.replace(oldText, newText);
}

const oldInstallLabel = '  const installLabel=gameInstalled?(updateAvailable?t.updateGame:t.repair):t.install;';
const newInstallLabel = '  const installLabel=gameInstalled?(updateAvailable?t.updateGame:t.play):t.install;';
if (src.includes(oldInstallLabel)) src = src.replace(oldInstallLabel, newInstallLabel);

const oldHomeActions = '<div className="home-actions"><button className="play-button" disabled={busy||!gameInstalled||updateAvailable} onClick={()=>void launch(false)}>{busy?t.preparing:t.play}</button><button className="training-button" disabled={busy||!gameInstalled} onClick={()=>void launch(true)}>{t.training}<small>{t.trainingHint}</small></button></div>';
const newHomeActions = '<div className="home-actions"><button className="play-button" disabled={busy||checkingGame} onClick={()=>void ((!gameInstalled||updateAvailable)?installGame():launch())}>{busy?t.preparing:!gameInstalled?t.install:updateAvailable?t.updateGame:t.play}</button></div>';
if (src.includes(oldHomeActions)) src = src.replace(oldHomeActions, newHomeActions);

const oldInstallCard = '<div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div>{(!gameInstalled||updateAvailable)&&<button disabled={busy} onClick={()=>void installGame()}>{installLabel}</button>}{gameInstalled&&!updateAvailable&&<button className="repair-link" disabled={busy} onClick={()=>void repairGame()}>{t.repair}</button>}</div>';
const newInstallCard = '<div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div></div>';
if (src.includes(oldInstallCard)) src = src.replace(oldInstallCard, newInstallCard);

// If Stage 76 already ran, keep it as-is. Otherwise fail if the resulting surface still has
// an external Training action or Repair on Home.
if (src.includes('onClick={()=>void launch(true)}')) {
  throw new Error("release UI still exposes external Training; mode choice must stay in GGO Client");
}
if (src.includes('className="repair-link"')) {
  throw new Error("release UI still exposes Repair on Home");
}
if (!src.includes('?t.install:updateAvailable?t.updateGame:t.play')) {
  throw new Error("release UI is missing the INSTALL / UPDATE / PLAY state machine");
}

fs.writeFileSync(path, src);
console.log("Release UI state machine verified: one INSTALL / UPDATE / PLAY action; Training stays in GGO Client; Repair stays in Diagnostics.");
