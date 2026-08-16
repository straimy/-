package arena.client.ui;

import arena.client.net.ArenaClientShopItem;

final class WeaponNames {
    private WeaponNames() {}
    static String russianFor(String id, String category) {
        if (id == null) id = "";
        return switch (id) {
            case "jeg:semi_auto_pistol" -> "полуавтоматический пистолет";
            case "jeg:combat_pistol" -> "боевой пистолет";
            case "jeg:revolver" -> "револьвер";
            case "jeg:custom_smg" -> "пистолет-пулемёт";
            case "jeg:vindicator_smg" -> "ПП поборника";
            case "jeg:semi_auto_rifle" -> "полуавтоматическая винтовка";
            case "jeg:assault_rifle" -> "штурмовая винтовка";
            case "jeg:burst_rifle" -> "винтовка с отсечкой";
            case "jeg:combat_rifle" -> "боевая винтовка";
            case "jeg:bolt_action_rifle" -> "винтовка с продольно-скользящим затвором";
            case "jeg:infantry_rifle" -> "пехотная винтовка";
            case "jeg:service_rifle" -> "служебная винтовка";
            case "jeg:waterpipe_shotgun" -> "самодельный дробовик";
            case "jeg:double_barrel_shotgun" -> "двуствольный дробовик";
            case "jeg:pump_shotgun" -> "помповый дробовик";
            case "jeg:repeating_shotgun" -> "многозарядный дробовик";
            case "jeg:light_machine_gun" -> "ручной пулемёт";
            case "jeg:blossom_rifle" -> "винтовка «Цветение»";
            case "jeg:holy_shotgun" -> "святой дробовик";
            case "jeg:sub_sonic_rifle" -> "дозвуковая винтовка";
            case "jeg:super_sonic_shotgun" -> "сверхзвуковой дробовик";
            case "jeg:fire_sweeper" -> "огненный карабин";
            case "gunnerarena:p9" -> "пистолет";
            case "gunnerarena:px18" -> "автоматический пистолет";
            case "gunnerarena:vkr47", "gunnerarena:arx3" -> "штурмовая винтовка";
            case "gunnerarena:vector_x" -> "пистолет-пулемёт";
            case "gunnerarena:pdw50" -> "компактный автомат";
            case "gunnerarena:spectre_dmr" -> "марксманская винтовка";
            case "gunnerarena:raven_m96" -> "снайперская винтовка";
            case "gunnerarena:titan_50" -> "крупнокалиберная винтовка";
            default -> switch (category == null ? "" : category) {
                case "PISTOL" -> "пистолет"; case "SMG" -> "пистолет-пулемёт"; case "RIFLE" -> "винтовка"; case "SHOTGUN" -> "дробовик"; case "DMR" -> "марксманская винтовка"; case "SNIPER" -> "снайперская винтовка"; case "HEAVY" -> "тяжёлое оружие"; default -> "оружие";
            };
        };
    }
    static String label(ArenaClientShopItem item) { return item.displayName() + " (" + russianFor(item.id(), item.category()) + ")"; }
}
