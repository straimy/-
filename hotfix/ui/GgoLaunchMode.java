package arena.client.ui;

/** Launch mode is owned by the GGO launcher, not by vanilla Minecraft menus. */
public final class GgoLaunchMode {
    private static final String MODE = normalize(System.getProperty("ggo.launch.mode", "online"));

    private GgoLaunchMode() {}

    private static String normalize(String value) {
        return "training".equalsIgnoreCase(value) ? "training" : "online";
    }

    public static boolean isTraining() {
        return "training".equals(MODE);
    }

    public static String id() {
        return MODE;
    }
}
