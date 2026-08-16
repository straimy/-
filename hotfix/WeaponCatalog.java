package arena.weapon;

import java.util.*;

/** GunGloryOnline v0.4 Beta server-authoritative weapon catalog. JEG values mirror 0.13.2 gun data. */
public final class WeaponCatalog {
    private final Map<String, WeaponDefinition> byId = new LinkedHashMap<>();
    public WeaponCatalog() { registerDefaults(); }
    public WeaponDefinition get(String id) { return byId.get(id); }
    public Collection<WeaponDefinition> all() { return Collections.unmodifiableCollection(byId.values()); }
    public List<WeaponDefinition> bySlot(WeaponDefinition.Slot slot) { return byId.values().stream().filter(w -> w.slot() == slot).toList(); }
    public void register(WeaponDefinition d) {
        Objects.requireNonNull(d); if (d.id()==null||d.id().isBlank()) throw new IllegalArgumentException("weapon id");
        if (d.price()<0||d.magazineSize()<0||d.startingReserve()<0) throw new IllegalArgumentException("negative weapon values"); byId.put(d.id(),d);
    }
    private void registerDefaults() {
        // Native JEG 0.13.2 values: maxAmmo/ammo type/fire mode are intentionally mirrored exactly.
        register(new WeaponDefinition("jeg:semi_auto_pistol","Semi Auto Pistol",WeaponDefinition.Category.PISTOL,WeaponDefinition.Slot.SECONDARY,300,"jeg:pistol_ammo",10,30,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:combat_pistol","Combat Pistol",WeaponDefinition.Category.PISTOL,WeaponDefinition.Slot.SECONDARY,700,"jeg:pistol_ammo",15,30,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:revolver","Revolver",WeaponDefinition.Category.PISTOL,WeaponDefinition.Slot.SECONDARY,500,"jeg:pistol_ammo",8,24,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:custom_smg","Custom SMG",WeaponDefinition.Category.SMG,WeaponDefinition.Slot.PRIMARY,850,"jeg:pistol_ammo",24,48,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:assault_rifle","Assault Rifle",WeaponDefinition.Category.RIFLE,WeaponDefinition.Slot.PRIMARY,1500,"jeg:rifle_ammo",30,60,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:burst_rifle","Burst Rifle",WeaponDefinition.Category.RIFLE,WeaponDefinition.Slot.PRIMARY,1750,"jeg:rifle_ammo",30,60,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.BURST_3),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:pump_shotgun","Pump Shotgun",WeaponDefinition.Category.SHOTGUN,WeaponDefinition.Slot.PRIMARY,1100,"jeg:shotgun_shell",6,18,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("jeg:bolt_action_rifle","Bolt Action Rifle",WeaponDefinition.Category.SNIPER,WeaponDefinition.Slot.PRIMARY,1900,"jeg:rifle_ammo",4,12,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.SNIPER));
        register(new WeaponDefinition("jeg:light_machine_gun","Light Machine Gun",WeaponDefinition.Category.HEAVY,WeaponDefinition.Slot.PRIMARY,2700,"jeg:rifle_ammo",100,200,WeaponDefinition.Weight.HEAVY,List.of(WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.NONE));

        // GunGloryOnline custom weapons.
        register(new WeaponDefinition("gunnerarena:p9","P9",WeaponDefinition.Category.PISTOL,WeaponDefinition.Slot.SECONDARY,250,"jeg:pistol_ammo",12,24,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("gunnerarena:px18","PX-18",WeaponDefinition.Category.PISTOL,WeaponDefinition.Slot.SECONDARY,900,"jeg:pistol_ammo",18,36,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.SEMI,WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("gunnerarena:vkr47","VKR-47",WeaponDefinition.Category.RIFLE,WeaponDefinition.Slot.PRIMARY,1700,"jeg:rifle_ammo",30,60,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.SEMI,WeaponDefinition.FireMode.BURST_3,WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("gunnerarena:arx3","ARX-3",WeaponDefinition.Category.RIFLE,WeaponDefinition.Slot.PRIMARY,1500,"jeg:rifle_ammo",30,60,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.BURST_3),WeaponDefinition.Scope.NONE));
        register(new WeaponDefinition("gunnerarena:vector_x","Vector-X",WeaponDefinition.Category.SMG,WeaponDefinition.Slot.PRIMARY,1300,"jeg:rifle_ammo",30,60,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.RED_DOT));
        register(new WeaponDefinition("gunnerarena:pdw50","PDW-50",WeaponDefinition.Category.SMG,WeaponDefinition.Slot.PRIMARY,1600,"jeg:rifle_ammo",50,100,WeaponDefinition.Weight.LIGHT,List.of(WeaponDefinition.FireMode.AUTO),WeaponDefinition.Scope.RED_DOT));
        register(new WeaponDefinition("gunnerarena:spectre_dmr","Spectre DMR",WeaponDefinition.Category.DMR,WeaponDefinition.Slot.PRIMARY,2400,"jeg:rifle_ammo",10,30,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.X4));
        register(new WeaponDefinition("gunnerarena:raven_m96","Raven M96",WeaponDefinition.Category.SNIPER,WeaponDefinition.Slot.PRIMARY,2800,"jeg:rifle_ammo",5,20,WeaponDefinition.Weight.MEDIUM,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.SNIPER));
        register(new WeaponDefinition("gunnerarena:titan_50","Titan .50",WeaponDefinition.Category.SNIPER,WeaponDefinition.Slot.PRIMARY,4200,"jeg:rifle_ammo",5,10,WeaponDefinition.Weight.HEAVY,List.of(WeaponDefinition.FireMode.SEMI),WeaponDefinition.Scope.SNIPER));
    }
}
