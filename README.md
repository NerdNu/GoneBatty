GoneBatty
=========
PvE Rev 18 custom drops (mob heads and "Essence of Flight").

Configuration
-------------

| Setting | Description |
| :--- | :--- |
| `debug.config` | If true, log dropped items and probabilities when the configuration is reloaded. |
| `debug.chance` | If true, log the probability threshold for a given drop when it occurs. |
| `debug.drops` | If true, log dropped items. |
| `debug.allow_spawn_eggs` | If true, spawn eggs are considered equally as valid as natural spawns when determining eligibility to drop special items. |
| `world.factor.<worldname>` | The per-world scaling factor for the drop rate. |
| `drops.head.enabled` | If true, head drops are enabled. |
| `drops.head.chance` | The base head drop chance. |
| `drops.head.scale.<id>` | The mob-type-specific head drop rate factor for the mob with type `<id>`. |
|  `drops.head.owner.<id>` | The player name of the skull owner for the head dropped when the mob with identifier `<id>` is killed. |
| `drops.essence_of_flight.enabled` | If true, Essence of Flight drops are enabled. |
| `drops.essence_of_flight.chance` | The base Essence of Flight drop chance. |
| `drops.essence_of_flight.item` | The serialised item stack of Essence of Flight. |
| `drops.essence_of_flight.scale.<id>` | The mob-type-specific Essence of Flight drop rate factor for the mob with type `<id>`. |

Notes:

 * Mob type identifiers are generally EntityType.name() (uppercase), e.g. CREEPER, but in the case of wither skeletons, the identifier WITHER_SKELETON is used.
 * The probability of a particular drop is computed as the product of the base probability, the world factor for the world where the mob dies, the mob-type scale factor for the mob that died and the looting level adjustment for the weapon used.


Commands
--------

 * `/gonebatty reload` - Reload the configuration.


Permissions
-----------

 * `gonebatty.admin` - Permission to run `/gonebatty reload`.

