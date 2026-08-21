from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

preferences = r'''package arena.client.shell;

public final class GgoClientPreferences {
    public enum GraphicsPreset { LOW_END, COMPETITIVE, BALANCED, HIGH, CINEMATIC, CUSTOM }
    public enum CrosshairMode { TACTICAL, DOT, OFF }

    private static GraphicsPreset graphicsPreset = GraphicsPreset.BALANCED;
    private static CrosshairMode crosshairMode = CrosshairMode.TACTICAL;
    private static int musicVolume = 65;
    private static boolean minimapEnabled = false;

    private GgoClientPreferences() {}

    public static GraphicsPreset graphicsPreset() { return graphicsPreset; }
    public static void setGraphicsPreset(GraphicsPreset value) { if (value != null) graphicsPreset = value; }

    public static CrosshairMode crosshairMode() { return crosshairMode; }
    public static void setCrosshairMode(CrosshairMode value) { if (value != null) crosshairMode = value; }

    public static int musicVolume() { return musicVolume; }
    public static void setMusicVolume(int value) { musicVolume = Math.max(0, Math.min(100, value)); }

    public static boolean minimapEnabled() { return minimapEnabled; }
    public static void setMinimapEnabled(boolean value) { minimapEnabled = value; }
}
'''

settings = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GgoSettingsScreen extends Screen {
    private final Screen parent;

    public GgoSettingsScreen(Screen parent) {
        super(Component.literal("GGO Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = Math.max(24, this.width / 2 - 300);
        int top = 108;
        int buttonW = 270;

        addRenderableWidget(Button.builder(graphicsLabel(), b -> {
            var values = GgoClientPreferences.GraphicsPreset.values();
            int next = (GgoClientPreferences.graphicsPreset().ordinal() + 1) % values.length;
            GgoClientPreferences.setGraphicsPreset(values[next]);
            b.setMessage(graphicsLabel());
        }).bounds(left, top, buttonW, 28).build());

        addRenderableWidget(Button.builder(musicLabel(), b -> {
            int value = GgoClientPreferences.musicVolume();
            value = value <= 0 ? 100 : Math.max(0, value - 10);
            GgoClientPreferences.setMusicVolume(value);
            b.setMessage(musicLabel());
        }).bounds(left, top + 38, buttonW, 28).build());

        addRenderableWidget(Button.builder(minimapLabel(), b -> {
            GgoClientPreferences.setMinimapEnabled(!GgoClientPreferences.minimapEnabled());
            b.setMessage(minimapLabel());
        }).bounds(left, top + 76, buttonW, 28).build());

        addRenderableWidget(Button.builder(crosshairLabel(), b -> {
            var values = GgoClientPreferences.CrosshairMode.values();
            int next = (GgoClientPreferences.crosshairMode().ordinal() + 1) % values.length;
            GgoClientPreferences.setCrosshairMode(values[next]);
            b.setMessage(crosshairLabel());
        }).bounds(left, top + 114, buttonW, 28).build());

        addRenderableWidget(Button.builder(Component.literal("CONTROLS  •  E / M / N / TAB / F / R / G"), b -> {})
                .bounds(left + 300, top, buttonW, 28).build());
        addRenderableWidget(Button.builder(Component.literal("ADVANCED  •  runtime diagnostics later"), b -> {})
                .bounds(left + 300, top + 38, buttonW, 28).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose())
                .bounds(left + 300, top + 114, buttonW, 28).build());
    }

    private Component graphicsLabel() {
        return Component.literal("GRAPHICS PRESET  •  " + GgoClientPreferences.graphicsPreset().name().replace('_', ' '));
    }

    private Component musicLabel() {
        return Component.literal("MUSIC VOLUME  •  " + GgoClientPreferences.musicVolume() + "%");
    }

    private Component minimapLabel() {
        return Component.literal("MINIMAP  •  " + (GgoClientPreferences.minimapEnabled() ? "ON" : "OFF"));
    }

    private Component crosshairLabel() {
        return Component.literal("CROSSHAIR  •  " + GgoClientPreferences.crosshairMode().name());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xF207090D);
        g.fill(0, 0, this.width, 3, 0xFFC83240);
        g.drawString(this.font, "GUNGLORYONLINE", 24, 18, 0xFFF4F6F8, false);
        g.drawString(this.font, "SETTINGS", 24, 40, 0xFFD34B57, false);
        g.drawString(this.font, "GGO settings only. Minecraft / Forge technical controls stay hidden in Advanced.", 24, 68, 0xFF7E8A9A, false);
        g.drawString(this.font, "Recommended: BALANCED • Music 65% • Minimap OFF • Tactical crosshair", 24, 84, 0xFF626E7E, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }
}
'''

(JAVA / "GgoClientPreferences.java").write_text(preferences)
(JAVA / "GgoSettingsScreen.java").write_text(settings)

# Route all GGO-owned settings buttons to the first-party settings screen.
for name in ("GgoShellScreen.java", "GgoFrontEndScreen.java"):
    p = JAVA / name
    if not p.exists():
        continue
    s = p.read_text()
    s = s.replace(
        'Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.OptionsScreen(this, Minecraft.getInstance().options))',
        'Minecraft.getInstance().setScreen(new GgoSettingsScreen(this))'
    )
    p.write_text(s)

# Replace vanilla crosshair rendering with a small GGO tactical crosshair and render the
# rest of the GGO combat HUD from the same guaranteed overlay pass.
hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    old_hide = '''        var id = event.getOverlay().id();\n        if (id.equals(VanillaGuiOverlay.HOTBAR.id())'''
    new_hide = '''        var id = event.getOverlay().id();\n        if (id.equals(VanillaGuiOverlay.CROSSHAIR.id())) {\n            Minecraft mc = Minecraft.getInstance();\n            if (mc.player != null && mc.level != null && mc.screen == null) {\n                GuiGraphics g = event.getGuiGraphics();\n                int width = mc.getWindow().getGuiScaledWidth();\n                int height = mc.getWindow().getGuiScaledHeight();\n                renderCrosshair(g, width, height);\n                renderVitals(g, mc, width, height);\n                renderWeapon(g, mc, width, height);\n                renderQuickSlots(g, mc, width, height);\n                renderWorldStatus(g, mc, width);\n            }\n            event.setCanceled(true);\n            return;\n        }\n        if (id.equals(VanillaGuiOverlay.HOTBAR.id())'''
    if old_hide in s:
        s = s.replace(old_hide, new_hide, 1)

    old_post = '''    @SubscribeEvent\n    public static void renderGgoHud(RenderGuiOverlayEvent.Post event) {\n        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;\n        Minecraft mc = Minecraft.getInstance();\n        if (mc.player == null || mc.level == null || mc.screen != null) return;\n\n        GuiGraphics g = event.getGuiGraphics();\n        int width = mc.getWindow().getGuiScaledWidth();\n        int height = mc.getWindow().getGuiScaledHeight();\n\n        renderVitals(g, mc, width, height);\n        renderWeapon(g, mc, width, height);\n        renderQuickSlots(g, mc, width, height);\n        renderWorldStatus(g, mc, width);\n    }\n\n'''
    if old_post in s:
        s = s.replace(old_post, "", 1)

    anchor = '    private static void renderVitals(GuiGraphics g, Minecraft mc, int width, int height) {'
    crosshair = '''    private static void renderCrosshair(GuiGraphics g, int width, int height) {\n        var mode = GgoClientPreferences.crosshairMode();\n        if (mode == GgoClientPreferences.CrosshairMode.OFF) return;\n        int cx = width / 2;\n        int cy = height / 2;\n        if (mode == GgoClientPreferences.CrosshairMode.DOT) {\n            g.fill(cx, cy, cx + 1, cy + 1, 0xFFE7EBF0);\n            return;\n        }\n        int gap = 3;\n        int len = 5;\n        int color = 0xFFE7EBF0;\n        g.fill(cx - gap - len, cy, cx - gap, cy + 1, color);\n        g.fill(cx + gap, cy, cx + gap + len, cy + 1, color);\n        g.fill(cx, cy - gap - len, cx + 1, cy - gap, color);\n        g.fill(cx, cy + gap, cx + 1, cy + gap + len, color);\n        g.fill(cx, cy, cx + 1, cy + 1, 0xFFD34855);\n    }\n\n'''
    if anchor in s and 'private static void renderCrosshair' not in s:
        s = s.replace(anchor, crosshair + anchor, 1)
    hud.write_text(s)

print("GGO Settings / Crosshair Stage 6 applied")
print(" - first-party GGO Settings screen")
print(" - graphics preset / music / minimap / crosshair preferences")
print(" - default music 65%, minimap OFF")
print(" - removes GGO-owned routes to vanilla OptionsScreen")
print(" - vanilla crosshair replaced with GGO tactical crosshair")
