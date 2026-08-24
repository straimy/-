package arena.client.shell;

import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/**
 * GGO-native settings screen backed directly by Minecraft Options.
 *
 * The controls are not decorative: sliders and toggles update the live Options instance and
 * values are persisted on close. This keeps Minecraft's proven settings backend while removing
 * the vanilla options-screen surface from the normal GGO flow.
 */
public final class GgoSettingsScreen extends Screen {
    private enum Page { AUDIO, VIDEO, CONTROLS }

    private static final int ACCENT = 0xFFC83245;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int MUTED = 0xFF8792A3;
    private static final int PANEL = 0xE60B0E14;

    private final Screen parent;
    private final Page page;

    public GgoSettingsScreen(Screen parent) {
        this(parent, Page.AUDIO);
    }

    private GgoSettingsScreen(Screen parent, Page page) {
        super(Component.literal("GunGloryOnline Settings"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int panelW = Math.min(720, Math.max(520, width - 180));
        int panelX = width / 2 - panelW / 2;
        int tabsY = Math.max(104, height / 7);
        int tabGap = 8;
        int tabW = (panelW - tabGap * 2) / 3;

        addRenderableWidget(tab("AUDIO", Page.AUDIO, panelX, tabsY, tabW));
        addRenderableWidget(tab("VIDEO", Page.VIDEO, panelX + tabW + tabGap, tabsY, tabW));
        addRenderableWidget(tab("CONTROLS", Page.CONTROLS, panelX + (tabW + tabGap) * 2, tabsY, tabW));

        int controlX = panelX + 34;
        int controlW = panelW - 68;
        int y = tabsY + 68;
        int gap = 48;

        if (page == Page.AUDIO) {
            addRenderableWidget(volumeSlider("MASTER VOLUME", SoundSource.MASTER, controlX, y, controlW));
            addRenderableWidget(volumeSlider("MUSIC", SoundSource.MUSIC, controlX, y + gap, controlW));
            addRenderableWidget(volumeSlider("PLAYERS", SoundSource.PLAYERS, controlX, y + gap * 2, controlW));
            addRenderableWidget(volumeSlider("WEATHER", SoundSource.WEATHER, controlX, y + gap * 3, controlW));
        } else if (page == Page.VIDEO) {
            double fov = clamp01((mc.options.fov().get() - 30.0) / 80.0);
            addRenderableWidget(new GgoSlider(controlX, y, controlW, "FIELD OF VIEW", fov,
                value -> Integer.toString(30 + (int)Math.round(value * 80.0)),
                value -> mc.options.fov().set(30 + (int)Math.round(value * 80.0))));

            addRenderableWidget(new GgoSlider(controlX, y + gap, controlW, "BRIGHTNESS", clamp01(mc.options.gamma().get()),
                GgoSettingsScreen::percent,
                value -> mc.options.gamma().set(clamp01(value))));

            double render = clamp01((mc.options.renderDistance().get() - 2.0) / 30.0);
            addRenderableWidget(new GgoSlider(controlX, y + gap * 2, controlW, "RENDER DISTANCE", render,
                value -> (2 + (int)Math.round(value * 30.0)) + " CHUNKS",
                value -> mc.options.renderDistance().set(2 + (int)Math.round(value * 30.0))));

            addRenderableWidget(toggle("FULLSCREEN", mc.options.fullscreen().get(), controlX, y + gap * 3, controlW,
                value -> mc.options.fullscreen().set(value)));
        } else {
            addRenderableWidget(new GgoSlider(controlX, y, controlW, "MOUSE SENSITIVITY", clamp01(mc.options.sensitivity().get()),
                GgoSettingsScreen::percent,
                value -> mc.options.sensitivity().set(clamp01(value))));

            addRenderableWidget(toggle("INVERT Y", mc.options.invertYMouse().get(), controlX, y + gap, controlW,
                value -> mc.options.invertYMouse().set(value)));
            addRenderableWidget(toggle("RAW MOUSE INPUT", mc.options.rawMouseInput().get(), controlX, y + gap * 2, controlW,
                value -> mc.options.rawMouseInput().set(value)));
            addRenderableWidget(toggle("TOGGLE SPRINT", mc.options.toggleSprint().get(), controlX, y + gap * 3, controlW,
                value -> mc.options.toggleSprint().set(value)));
        }

        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
            .bounds(panelX, Math.min(height - 56, tabsY + 286), panelW, 30).build());
    }

    private Button tab(String label, Page target, int x, int y, int w) {
        String text = page == target ? "• " + label + " •" : label;
        return Button.builder(Component.literal(text), button -> Minecraft.getInstance().setScreen(new GgoSettingsScreen(parent, target)))
            .bounds(x, y, w, 28).build();
    }

    private GgoSlider volumeSlider(String label, SoundSource source, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        return new GgoSlider(x, y, w, label, clamp01(mc.options.getSoundSourceVolume(source)),
            GgoSettingsScreen::percent,
            value -> mc.options.setSoundCategoryVolume(source, (float)clamp01(value)));
    }

    private Button toggle(String label, boolean initial, int x, int y, int w, java.util.function.Consumer<Boolean> setter) {
        final boolean[] value = {initial};
        return Button.builder(toggleLabel(label, value[0]), button -> {
                value[0] = !value[0];
                setter.accept(value[0]);
                button.setMessage(toggleLabel(label, value[0]));
                Minecraft.getInstance().options.save();
            })
            .bounds(x, y, w, 30).build();
    }

    private static Component toggleLabel(String label, boolean value) {
        return Component.literal(label + "    " + (value ? "ON" : "OFF"));
    }

    private static String percent(double value) {
        return Math.round(clamp01(value) * 100.0) + "%";
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().options.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070B);
        g.fill(0, 0, width, 3, ACCENT);
        int glowW = Math.max(240, width / 3);
        g.fill(0, 3, glowW, height, 0x351D0710);

        int marginX = Math.max(46, width / 18);
        int top = Math.max(46, height / 14);
        int panelW = Math.min(720, Math.max(520, width - 180));
        int panelX = width / 2 - panelW / 2;
        int tabsY = Math.max(104, height / 7);
        int panelBottom = Math.min(height - 42, tabsY + 330);

        g.drawString(font, "GUN GLORY ONLINE", marginX, top, TEXT, false);
        g.drawString(font, "SETTINGS  /  " + page.name(), marginX, top + 22, ACCENT, false);
        g.fill(panelX - 18, tabsY - 18, panelX + panelW + 18, panelBottom, PANEL);
        g.fill(panelX - 18, tabsY - 18, panelX - 15, panelBottom, ACCENT);

        String hint = switch (page) {
            case AUDIO -> "Live mix controls. Changes apply immediately.";
            case VIDEO -> "Display and visibility controls for the GGO client.";
            case CONTROLS -> "Mouse and movement behavior.";
        };
        g.drawString(font, hint, panelX, tabsY - 38, MUTED, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Minimal GGO-styled slider bound to a real option value. */
    private final class GgoSlider extends AbstractSliderButton {
        private final String label;
        private final Function<Double, String> formatter;
        private final DoubleConsumer setter;

        private GgoSlider(int x, int y, int width, String label, double value,
                          Function<Double, String> formatter, DoubleConsumer setter) {
            super(x, y, width, 30, Component.empty(), clamp01(value));
            this.label = label;
            this.formatter = formatter;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + "    " + formatter.apply(value)));
        }

        @Override
        protected void applyValue() {
            setter.accept(clamp01(value));
            Minecraft.getInstance().options.save();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int trackY = y + h - 7;
            int fillW = (int)Math.round((w - 4) * clamp01(value));
            int border = isHoveredOrFocused() ? 0xFF4D5868 : 0xFF2C333E;

            g.fill(x, y, x + w, y + h, 0xFF10151C);
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, trackY, x + w, trackY + 3, 0xFF252D38);
            g.fill(x, trackY, x + fillW, trackY + 3, ACCENT);
            int knobX = x + Math.max(0, Math.min(w - 4, fillW - 2));
            g.fill(knobX, trackY - 2, knobX + 4, trackY + 5, 0xFFF2F4F7);
            g.drawCenteredString(font, getMessage(), x + w / 2, y + 8, TEXT);
        }
    }
}
