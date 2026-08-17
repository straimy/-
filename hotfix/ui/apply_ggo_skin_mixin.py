from pathlib import Path

root = Path("ga-build/client-ui")
build = root / "build.gradle"
text = build.read_text()

plugin_line = "    id 'org.spongepowered.mixin' version '0.7.+'\n"
if "org.spongepowered.mixin" not in text:
    lines = text.splitlines(keepends=True)
    forge_index = next((i for i, line in enumerate(lines) if "net.minecraftforge.gradle" in line), None)
    if forge_index is None:
        raise SystemExit("client-ui ForgeGradle plugin declaration missing")
    lines.insert(forge_index + 1, plugin_line)
    text = "".join(lines)

if "org.spongepowered:mixin:0.8.5:processor" not in text:
    text += "\n\ndependencies {\n    annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'\n}\n"

if "gungloryonline_ui.refmap.json" not in text:
    text += """

mixin {
    add sourceSets.main, 'gungloryonline_ui.refmap.json'
    config 'gungloryonline_ui.mixins.json'
}
"""

if "MixinConfigs" not in text:
    text += """

tasks.named('jar', Jar).configure {
    manifest {
        attributes(['MixinConfigs': 'gungloryonline_ui.mixins.json'])
    }
}
"""

build.write_text(text)

resources = root / "src/main/resources"
resources.mkdir(parents=True, exist_ok=True)
config_src = Path("hotfix/ui/gungloryonline_ui.mixins.json")
(resources / config_src.name).write_text(config_src.read_text())

mixin_dir = root / "src/main/java/arena/client/ui/mixin"
mixin_dir.mkdir(parents=True, exist_ok=True)
mixin_src = Path("hotfix/ui/mixin/AbstractClientPlayerMixin.java")
(mixin_dir / mixin_src.name).write_text(mixin_src.read_text())

print("GGO skin mixin configured after ForgeGradle")
