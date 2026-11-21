"""Play icon (512x512) and feature graphic (1024x500), drawn from the launcher icon's own vector path
(app/android/src/main/res/drawable/ic_launcher_foreground.xml, background #435E91). Run: python store/make_graphics.py"""
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

HERE = Path(__file__).parent
xml = (HERE.parent / "app/android/src/main/res/drawable/ic_launcher_foreground.xml").read_text(encoding="utf-8")
path = re.search(r'pathData="([^"]+)"', xml).group(1)
BG = "#435E91"
# the 108-unit adaptive canvas; the visible icon is its middle 72 units (18..90)
icon = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="{{s}}" height="{{s}}"><rect x="0" y="0" width="108" height="108" fill="{BG}"/><path fill="#FFFFFF" fill-rule="evenodd" d="{path}"/></svg>'

page_icon = f'<html><body style="margin:0">{icon.format(s=512)}</body></html>'
page_feature = f'''<html><body style="margin:0"><div id="f" style="width:1024px;height:500px;background:{BG};display:flex;align-items:center;gap:56px;padding-left:96px;box-sizing:border-box;font-family:'Segoe UI',Roboto,sans-serif;color:#fff">
<div style="border-radius:48px;overflow:hidden;width:260px;height:260px">{icon.format(s=260)}</div>
<div><div style="font-size:96px;font-weight:600;line-height:1">QR</div>
<div style="font-size:32px;margin-top:20px;opacity:.92;max-width:640px;line-height:1.25">See where a link goes before you open it</div></div></div></body></html>'''

with sync_playwright() as p:
    b = p.chromium.launch(headless=True)
    pg = b.new_page(viewport={"width": 512, "height": 512})
    pg.set_content(page_icon)
    pg.screenshot(path=str(HERE / "icon-512.png"), clip={"x": 0, "y": 0, "width": 512, "height": 512})
    pg = b.new_page(viewport={"width": 1024, "height": 500})
    pg.set_content(page_feature)
    pg.screenshot(path=str(HERE / "feature-1024x500.png"), clip={"x": 0, "y": 0, "width": 1024, "height": 500})
    b.close()
print("ok")
