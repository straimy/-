#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('.') if Path('src/App.tsx').is_file() else Path('launcher')
APP = ROOT / 'src/App.tsx'
if not APP.is_file():
    raise SystemExit(f'launcher source missing: {APP}')

text = APP.read_text(encoding='utf-8')
old = '(["home","accounts","servers","library","logs","news","settings"] as Page[])'
new = '(["home","accounts","news","settings"] as Page[])'
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('primary navigation anchor not found')

# Diagnostics remains reachable from Settings; remove no implementation code so support tools
# stay available without cluttering the main player navigation.
if 'onClick={()=>setPage("logs")}>DIAGNOSTICS</button>' not in text:
    raise SystemExit('settings diagnostics entry missing')
if '(["home","accounts","news","settings"] as Page[])' not in text:
    raise SystemExit('simplified primary navigation missing')

APP.write_text(text, encoding='utf-8')
print('Applied GGO launcher Stage99 surface simplification')
print(' - primary nav: Home / Account / News / Settings')
print(' - diagnostics retained inside Settings')
print(' - Servers / Library / Diagnostics removed from permanent sidebar')
