# GunGloryOnline UI Shell

Goal: remove the vanilla Minecraft inventory, player list and pause UX from the normal GGO player path while preserving Runtime v1 underneath.

## Input contract

### I — Inventory
Opens the first-party GGO Inventory screen. The vanilla Minecraft inventory should not be reachable from the normal GGO player flow.

Inventory layout:
- left: character preview and equipment slots;
- center-left: armor/weapon statistics and derived combat stats;
- right: grid inventory/backpack;
- dedicated Primary / Secondary / Sidearm / Armor / Backpack / Gadget slots;
- ammo, healing and utility categories;
- weight/capacity indicator;
- Quick Slots strip for fast-use items.

The UI must not expose vanilla crafting or recipe-book concepts unless a future GGO crafting system intentionally uses them.

### TAB (hold) — Social / Squad overlay
TAB does not show the vanilla server player list for normal players.

Contextual overlay:
- squad members;
- health/downed/dead state where allowed;
- voice state;
- ping/network quality;
- party leader;
- current activity/match state;
- invitations/friend presence when relevant.

Battle Royale additions:
- squad kills;
- alive/dead state;
- team placement when available;
- match phase and remaining players.

The full raw player list is admin/debug-only and should be moved behind an admin panel or non-default diagnostic bind.

### ESC — GGO Pause Hub
Normal player layout:
1. Resume
2. Inventory
3. Social
4. Activities
5. Settings
6. Leave GGO

Optional secondary actions:
- Report player
- Support
- Diagnostics (Advanced only)

Do not expose vanilla `Options`, `Advancements`, `Statistics`, `Open to LAN`, `Mods`, `Singleplayer` or `Multiplayer` screens through the normal GGO flow.

## Social

Friends do not belong inside Settings. `Social` is a first-class GGO area shared by launcher frontend and game frontend.

Social contains:
- friends;
- party/squad;
- pending invitations;
- recent players;
- blocked users;
- voice controls for the current party;
- player search.

Settings only contains Social preferences such as privacy, invitation policy, notification settings and voice defaults.

## HUD changes

The vanilla hotbar should become a GGO Quick Slots / combat belt presentation.

Suggested combat HUD:
- weapon + magazine/reserve ammo;
- health/armor;
- active gadget/med slot;
- compact Quick Slots;
- squad status when in a team;
- objective/match state;
- contextual interaction prompt.

Avoid Minecraft hearts, hunger, XP bar and vanilla slot framing in the final player-facing presentation.

## Inventory domain model

Do not make GGO inventory logic depend permanently on Minecraft slot numbering. Introduce runtime-neutral concepts:

- EquipmentSlot
- InventoryGrid
- InventoryItem
- WeaponSlot
- AmmoStack
- ArmorPiece
- Backpack
- Gadget
- Consumable
- QuickSlot
- Weight/Capacity

Runtime v1 adapts these concepts to Minecraft ItemStacks internally.

## Security / authority

Online inventory, currencies, progression and competitive equipment are server-authoritative.

The client may render predicted state, but must not be trusted to grant items, currency or permanent equipment changes.

Training may use a local sandbox inventory that is explicitly separated from online progression.

## Implementation order

### Stage 1
- intercept normal inventory key and open GGO Inventory shell;
- replace vanilla TAB player list with GGO Squad overlay;
- replace vanilla pause screen with GGO Pause Hub;
- preserve vanilla screens only behind an admin/debug escape hatch.

### Stage 2
- equipment/loadout data binding;
- real grid inventory;
- item details/stats;
- Quick Slots HUD;
- Social service integration.

### Stage 3
- drag/drop inventory interactions;
- compare equipment;
- contextual actions;
- party/voice presence;
- server-authoritative inventory persistence.

## Visual direction

Use the same GGO visual language as launcher/frontend:
- graphite/black surfaces;
- deep red accents;
- thin borders rather than chunky Minecraft panels;
- condensed readable typography;
- subtle tactical-grid / noise texture;
- item rarity indicated by restrained accent treatment, not rainbow UI;
- character/equipment screen may use a blurred/dimmed live 3D world behind it.

The target should feel closer to a tactical online RPG/shooter UI than to a reskinned Minecraft chest screen.
