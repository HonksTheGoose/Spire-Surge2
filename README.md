# Spire Surge

An addon for **Spawn Spires** (Mob Towers, mod id `mobtowers`, tested against `mobtowers-1.0.6`).

Right-click a **lit Spire Heart** with a **ghast tear** and the area around it goes into a *surge*:

- the Heart glows **red**, and Spawn Spires' flame particles and **Seeping Grimstone** turn red inside the surge (no custom textures needed, see "How the red glow works")
- for **10 minutes** (configurable) every player within **100 blocks** (configurable) gets extra monster spawns
- those monsters wear **tiered gear** (leather up to netherite) that can be **enchanted**
- monsters killed in the surge drop **better loot**, and you can define your own **loot pool** with items and drop percentages

Everything is configurable, and the mob pool, gear and enchantments are datapack-driven so other mods can plug in.

## Requirements

- Minecraft **1.21.1** (1.21.0 should also work; **not** 1.21.2 or newer)
- NeoForge **21.1.x**
- Spawn Spires (`mobtowers`) 1.0.6
- Install on **both the server and every client** (the client needs it for the red glow).

## Getting the jar (you have to build it once)

This was written without access to Minecraft's libraries, so I could not compile it for you. Pick one:

**A. On your own PC** (needs Java 21 and internet):

    cd spire-surge
    ./gradlew build          # Windows: gradlew.bat build

The jar appears in `build/libs/`. Put it in your `mods` folder next to the Spawn Spires jar.

**B. No Java needed (GitHub):** make a free GitHub repo, upload this whole folder (including the hidden `.github` folder), open the **Actions** tab, run **Build mod jar**, then download `spire-surge-jar` from the finished run.

If the build fails, copy the error text and send it to me. It will almost certainly be a one-line fix.
If Gradle says a version does not exist, edit `neo_version` in `gradle.properties` (any NeoForge `21.1.x`) or the plugin version at the top of `build.gradle`.

## Using it

1. Light the Spire Heart as normal (a Heart is lit while its region has monsters enabled).
2. Hold a **ghast tear** and right-click the Heart. One tear is used (never in creative).
3. An action-bar message appears and the Heart turns red. Clicking again while active refreshes the timer (`repeatMode`).

If the Heart is not lit, the tear is **not** used and the click is swallowed, so you cannot accidentally toggle the Heart with a tear in hand.

## Config

`config/spiresurge-common.toml` (created on first launch). Restart after editing, or use `/reload` for the datapack parts. Highlights:

| Section | Key | Default | What it does |
|---|---|---|---|
| `[surge]` | `radius` | 100 | Blocks around the Heart |
| `[surge]` | `durationSeconds` | 600 | Surge length (10 min) |
| `[trigger]` | `triggerItem` | `minecraft:ghast_tear` | Any item id works |
| `[trigger]` | `repeatMode` | REFRESH | REFRESH / EXTEND / IGNORE when used on an active Heart |
| `[spawning]` | `attemptsPerPlayer`, `intervalTicks`, `maxMobsPerPlayer` | 2 / 20 / 24 | The "spawn rate" dials |
| `[spawning]` | `ignoreLightLevel` | true | Spawn in daylight too (see notes) |
| `[spawning]` | `mobBlacklist`, `extraMobs` | empty | Quick pool edits, `id=weight` or `#tag=weight` |
| `[gear]` | `tierMultiplier` | 1.5 | Chance of a gear set = weight x multiplier^tier, rolled **separately for every slot** (so armour pieces are mixed tiers). Higher = better gear more common |
| `[gear]` | `minTier`, `maxTier`, `dropChance` | 0 / 1000 / -1 | Tier limits; gear drop chance (-1 = vanilla) |
| `[enchanting]` | `enchantChance`, `maxEnchantsPerItem`, `levelMultiplier` | 0.65 / 2 / 1.0 | Enchant frequency and strength |
| `[enchanting]` | `enchantWhitelist`, `enchantBlacklist`, `allowOverMaxLevels` | empty / empty / false | Restrict or unlock enchants |
| `[loot]` | `scope` | INSIDE_SURGE | SURGE_SPAWNED, INSIDE_SURGE or EVERYWHERE |
| `[loot]` | `dropMultiplier`, `xpMultiplier` | 2.0 / 2.0 | Better drops and XP |
| `[loot]` | `entityBlacklist` | bosses | Never touched by the loot changes |
| `[lootPool]` | `mode` | REPLACE | OFF / ADD / REPLACE (see below) |
| `[lootPool]` | `entries`, `rolls` | see file | Your custom pool |
| `[visuals]` | `tintCore`, `coreTintColor`, `pulse` | true / FF2A2A / true | The red glow on the Heart |
| `[visuals]` | `redParticles`, `particleTintColor` | true / FF2A2A | Red Spawn Spires particles inside a surge |
| `[visuals]` | `redGrimstone`, `grimstoneTintColor` | true / FF3A3A | Red Seeping Grimstone inside a surge |

## Custom loot pool

`[lootPool] entries` is a comma separated list of `item_id=chance` or `item_id=chancexMin-Max`. Chance is a **percentage**.

    entries = "minecraft:diamond=2, minecraft:emerald=10x1-2, somemod:cool_item=5x1-3"

- `diamond=2` : 2% chance of 1 diamond
- `emerald=10x1-2` : 10% chance of 1 to 2 emeralds
- Every entry is rolled separately; `rolls` repeats the whole pool.
- Items from mods that are not installed are skipped silently.
- `mode = REPLACE` swaps the mob's normal drops for the pool (armour and weapons it wore still drop as usual). `ADD` keeps normal drops (multiplied by `dropMultiplier`) and adds the pool on top. `OFF` disables the pool.

The default mode is `REPLACE` because that is what was asked for; set `mode = "ADD"` if you also want vanilla drops.

## Datapack / other mods

Other mods (or you, with a datapack) add files in these folders. Entries from unknown mods are skipped, never an error.

**Mobs** `data/<namespace>/spiresurge/mobs/*.json`

    { "entries": [
      { "entity": "minecraft:zombie", "weight": 30 },
      { "entity": "somemod:ghoul", "weight": 10, "gear_multiplier": 1.5, "slots": ["head","chest","mainhand"] },
      { "entity": "minecraft:creeper", "weight": 14, "equip": false },
      { "entity": "#mobtowers:tower_summoned", "weight": 10 }
    ] }

`weight` 0 = never spawned by a surge, but still gets gear if it spawns naturally inside one.
The tag `#mobtowers:tower_summoned` is included by default, so mods that already register mobs for Spawn Spires work automatically.

**Gear** `data/<namespace>/spiresurge/gear/*.json`: each set has a `tier`, a `weight`, and per slot (`head`, `chest`, `legs`, `feet`, `mainhand`, `offhand`) one item id or a list to pick from. See `gear/vanilla.json`.

**Enchantments** `data/<namespace>/spiresurge/enchants/*.json`: `enchantment`, `weight`, `min_level`, `max_level`, `min_tier`. An enchantment is only applied if the item can legally carry it. See `enchants/vanilla.json`.

## How the red glow works

Spawn Spires draws the Heart's flame and the glow on its jaws from a single colour value, using a greyscale texture that is tinted per region. While a surge is active this addon sets that colour to red (and pulses it), then restores the original colour. So **no sprite is required**. If you want a different colour, change `coreTintColor` (hex RRGGBB). A little red dust is also shown around the Heart.

**Particles:** Spawn Spires' flame particles (`tower_flame`, `monster_spawn_flame`, `vanilla_spawner_flame`) are wrapped so that any spawned inside a surge radius are tinted red (`redParticles`). **Seeping Grimstone** already has a tint index in its model, so a colour handler gives it red inside the surge and Spawn Spires' normal region colour elsewhere (`redGrimstone`). Chunks near a surge re-mesh once when it starts and once when it ends. The grimstone texture is dark, so a multiply-tint gives a deep red rather than a bright one. If you want it brighter, add your own red `seeping_grimstone_active` texture in a resource pack (optional).

## Notes and limits

- With `ignoreLightLevel = true` (default) undead can spawn in daylight and burn. Most surge gear includes a helmet, which protects them. Set it to `false` for vanilla-style light rules (then the surface will only spawn mobs at night or in dark places).
- Surge mobs are normal mobs: they despawn when far from players like any other.
- Surges are saved with the world and survive restarts. Cores in unloaded chunks keep counting down.
- Mob Towers is loaded by reflection for the colour change only. If a future Spawn Spires version renames its internals, the Heart, particles or grimstone just stop turning red (a warning is logged); nothing else breaks.
