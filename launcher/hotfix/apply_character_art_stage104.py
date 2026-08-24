#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "src/App.tsx"
CSS = ROOT / "src/polish.css"

a = APP.read_text()
needle = '''      {page==="home"&&<section className="home-page">
        <div className="home-copy">'''
replacement = '''      {page==="home"&&<section className="home-page ggo-character-home">
        <div className="ggo-character-art" aria-hidden="true">
          <img className="ggo-character ggo-character-headphones" src="https://ggo.kvicloud.ru/images/launcher/ggo-hero-headphones.webp" alt=""/>
          <img className="ggo-character ggo-character-lantern" src="https://ggo.kvicloud.ru/images/launcher/ggo-hero-lantern.webp" alt=""/>
        </div>
        <div className="home-copy">'''
if needle in a:
    a = a.replace(needle, replacement, 1)
elif 'className="ggo-character-art"' not in a:
    raise SystemExit('canonical home section not found')
APP.write_text(a)

css = CSS.read_text()
marker = '/* GGO Stage104 dual character art */'
if marker not in css:
    css += r'''

/* GGO Stage104 dual character art */
.ggo-character-home{isolation:isolate}
.ggo-character-home .home-copy,.ggo-character-home .install-card{z-index:4}
.ggo-character-art{position:absolute;right:0;top:0;bottom:46px;width:min(49vw,720px);z-index:1;pointer-events:none;overflow:hidden;opacity:.98;mask-image:linear-gradient(90deg,transparent 0%,rgba(0,0,0,.42) 14%,#000 34%,#000 100%)}
.ggo-character-art:before{content:"";position:absolute;inset:8% 0 2% 13%;z-index:0;background:radial-gradient(circle at 64% 42%,rgba(229,77,134,.19),transparent 36%),radial-gradient(circle at 72% 48%,rgba(89,139,255,.12),transparent 51%);filter:blur(28px)}
.ggo-character{position:absolute;right:-1%;bottom:-1%;width:100%;height:100%;object-fit:contain;object-position:right bottom;filter:drop-shadow(0 24px 38px rgba(0,0,0,.42));will-change:opacity,transform}
.ggo-character-headphones{opacity:1;animation:ggoHeroHeadphones 28s ease-in-out infinite}
.ggo-character-lantern{opacity:0;animation:ggoHeroLantern 28s ease-in-out infinite}
@keyframes ggoHeroHeadphones{0%,40%{opacity:1;transform:translateY(0) scale(1)}47%,91%{opacity:0;transform:translateY(4px) scale(1.006)}98%,100%{opacity:1;transform:translateY(0) scale(1)}}
@keyframes ggoHeroLantern{0%,40%{opacity:0;transform:translateY(4px) scale(1.006)}47%,91%{opacity:1;transform:translateY(0) scale(1)}98%,100%{opacity:0;transform:translateY(4px) scale(1.006)}}
@media(max-width:1180px){.ggo-character-art{width:45vw;opacity:.82}.ggo-character-home .install-card{margin-right:23vw}.ggo-character-home .home-copy{max-width:48vw}}
@media(max-width:980px){.ggo-character-art{width:42vw;opacity:.38;filter:saturate(.85)}.ggo-character-home .install-card{margin-right:0}.ggo-character-home .home-copy{max-width:620px}}
@media(prefers-reduced-motion:reduce){.ggo-character-headphones{animation:none;opacity:1}.ggo-character-lantern{animation:none;opacity:0}}
'''
CSS.write_text(css)

assert 'ggo-hero-headphones.webp' in APP.read_text()
assert 'ggo-hero-lantern.webp' in APP.read_text()
assert marker in CSS.read_text()
print('Stage104 dual character art applied')
