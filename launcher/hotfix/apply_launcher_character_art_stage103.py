from pathlib import Path

ROOT = Path("launcher")
APP = ROOT / "src/App.tsx"
CSS = ROOT / "src/polish.css"

app = APP.read_text()
needle = '''      {page==="home"&&<section className="home-page">\n        <div className="home-copy"><span className="eyebrow">GUNGLORYONLINE · {info.channel.toUpperCase()}</span><h1>READY TO<br/><em>DROP IN?</em></h1><p>{t.requiredRp}</p><div className="ready-row"><span className={gameInstalled&&!updateAvailable?"ready":"warn"}></span>{statusLabel}<small>{production?t.remote:t.localFallback}</small></div><div className="home-actions"><button className="play-button" disabled={busy||checkingGame} onClick={()=>void ((!gameInstalled||updateAvailable)?installGame():launch())}>{busy?t.preparing:!gameInstalled?t.install:updateAvailable?t.updateGame:t.play}</button></div></div>\n        <div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div></div>\n      </section>}'''
replacement = '''      {page==="home"&&<section className="home-page ggo-character-home">\n        <div className="home-copy"><span className="eyebrow">GUNGLORYONLINE · {info.channel.toUpperCase()}</span><h1>READY TO<br/><em>DROP IN?</em></h1><p>{t.requiredRp}</p><div className="ready-row"><span className={gameInstalled&&!updateAvailable?"ready":"warn"}></span>{statusLabel}<small>{production?t.remote:t.localFallback}</small></div><div className="home-actions"><button className="play-button" disabled={busy||checkingGame} onClick={()=>void ((!gameInstalled||updateAvailable)?installGame():launch())}>{busy?t.preparing:!gameInstalled?t.install:updateAvailable?t.updateGame:t.play}</button></div></div>\n        <div className="install-card"><div><span>{t.version}</span><b>{info.gameVersion}</b></div><div><span>Client runtime</span><b>1.20.1 · Forge 47.4.10</b></div><div><span>GGO Network</span><b>{info.server}</b></div></div>\n        <div className="ggo-character-art" aria-hidden="true">\n          <img className="ggo-character ggo-character-headphones" src="https://ggo.kvicloud.ru/images/launcher/ggo-hero-headphones.webp" alt=""/>\n          <img className="ggo-character ggo-character-lantern" src="https://ggo.kvicloud.ru/images/launcher/ggo-hero-lantern.webp" alt=""/>\n          <span className="ggo-character-vignette"/>\n        </div>\n      </section>}'''

if needle in app:
    app = app.replace(needle, replacement, 1)
elif 'className="home-page ggo-character-home"' not in app:
    raise SystemExit("Stage103 home surface not found")
APP.write_text(app)

css = CSS.read_text()
marker = "/* GGO Stage103 character art */"
block = r'''

/* GGO Stage103 character art */
.ggo-character-home{grid-template-columns:minmax(380px,.9fr) minmax(310px,.68fr);padding-right:clamp(300px,31vw,520px)}
.ggo-character-home .home-copy,.ggo-character-home .install-card{position:relative;z-index:4}
.ggo-character-home .install-card{backdrop-filter:blur(20px);background:linear-gradient(145deg,rgba(17,22,30,.88),rgba(8,11,16,.90))}
.ggo-character-art{position:absolute;z-index:1;right:-1.5%;top:0;bottom:46px;width:clamp(340px,35vw,610px);pointer-events:none;overflow:hidden;filter:drop-shadow(0 26px 42px rgba(0,0,0,.44))}
.ggo-character{position:absolute;display:block;user-select:none;-webkit-user-drag:none;will-change:opacity,transform}
.ggo-character-headphones{right:-2%;bottom:-6%;height:105%;max-width:none;opacity:1;animation:ggoHeroHeadphones 28s ease-in-out infinite}
.ggo-character-lantern{right:-31%;bottom:-1%;height:92%;max-width:none;opacity:0;animation:ggoHeroLantern 28s ease-in-out infinite}
.ggo-character-vignette{position:absolute;inset:0;background:linear-gradient(90deg,#080a0f 0%,rgba(8,10,15,.58) 10%,transparent 34%),linear-gradient(0deg,rgba(8,10,15,.75),transparent 20%);mix-blend-mode:normal}
@keyframes ggoHeroHeadphones{0%,42%{opacity:1;transform:translate3d(0,0,0) scale(1)}48%,92%{opacity:0;transform:translate3d(8px,-3px,0) scale(1.008)}96%,100%{opacity:1;transform:translate3d(0,0,0) scale(1)}}
@keyframes ggoHeroLantern{0%,42%{opacity:0;transform:translate3d(8px,1px,0) scale(1.01)}48%,92%{opacity:.96;transform:translate3d(0,-3px,0) scale(1)}96%,100%{opacity:0;transform:translate3d(8px,1px,0) scale(1.01)}}
@media(max-width:1250px){.ggo-character-home{padding-right:300px}.ggo-character-art{width:360px;right:-4%}.ggo-character-home .install-card{width:330px}.ggo-character-headphones{height:91%;right:-10%}.ggo-character-lantern{height:78%;right:-45%}}
@media(max-width:1050px){.ggo-character-home{padding-right:42px}.ggo-character-art{opacity:.30;width:330px;right:-8%}.ggo-character-home .install-card{z-index:5}.ggo-character-headphones{height:86%}.ggo-character-lantern{height:72%}}
@media(prefers-reduced-motion:reduce){.ggo-character-headphones{animation:none;opacity:1}.ggo-character-lantern{display:none}}
'''
if marker not in css:
    css += block
CSS.write_text(css)

assert 'ggo-hero-headphones.webp' in APP.read_text()
assert 'ggo-hero-lantern.webp' in APP.read_text()
assert marker in CSS.read_text()
print('Launcher Stage103 character-art surface applied')
