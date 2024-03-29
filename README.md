GoneBatty
=========
PvE Rev 18 custom drops (mob heads and "Essence of Flight").


Commands
--------

 * `/gonebatty reload` - Reload the configuration.
 * `/gonebatty set-head <type>` - Set the head of the mob with the specified entity type, which is one of the constants listed [here](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/EntityType.html). Use an empty hand to clear the specified entry.
 * `/gonebatty get-head <type>` - Get the head for the specified mob type.
 * `/gonebatty list-heads` - List all the head types, colour coded green (item configured) or red (no configured item).



Configuration
-------------

| Setting | Description |
| :--- | :--- |
| `debug.config` | If true, log dropped items and probabilities when the configuration is reloaded. |
| `debug.chance` | If true, log the probability threshold for a given drop when it occurs. |
| `debug.drops` | If true, log dropped items. |
| `debug.events` | If true, provide additional debug detail in event handlers. |
| `charged_creepers_decapitate_mobs` | If true, charged creepers decapitate mobs that would not drop a head in vanilla. Only a single head is dropped per explosion. If a variety of mobs are caught in the same explosion, the ones that would drop their heads in vanilla take precedence over the non-vanilla head drops, e.g. a skeleton head prevents a cow head from dropping. |
| `charged_creepers_decapitate_players` | If true, players are considered equivalent to mobs for the purposes of being decapitated by charged creepers. |
| `players_decapitate_players` | If true, players have a chance to decapitate other players, and that chance is improved by the looting enchantment. |
| `world.factor.<worldname>` | The per-world scaling factor for the drop rate. |
| `drops.head.enabled` | If true, head drops are enabled. |
| `drops.head.chance` | The base head drop chance. |
| `drops.head.scale.<type>` | The mob-type-specific head drop rate factor for the mob with type `<type>`. |
| `drops.head.item.<type>` | The serialised item stack of the head dropped when the mob with identifier `<type>` is killed. (This is set by `/gonebatty set-head <type>`.) |
| `drops.essence_of_flight.enabled` | If true, Essence of Flight drops are enabled. |
| `drops.essence_of_flight.chance` | The base Essence of Flight drop chance. |
| `drops.essence_of_flight.item` | The serialised item stack of Essence of Flight. |
| `drops.essence_of_flight.scale.<type>` | The mob-type-specific Essence of Flight drop rate factor for the mob with type `<type>`. |

Notes:

 * Mob type identifiers are upper cased [EntityType](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/EntityType.html) constants, e.g. `CREEPER`, `WITHER_SKELETON`, `PLAYER`.
 * The probability of a particular drop is computed as the product of the base probability, the world factor for the world where the mob dies, the mob-type scale factor for the mob that died and the looting level adjustment for the weapon used.


Permissions
-----------

 * `gonebatty.admin` - Permission to run `/gonebatty` commands.


Dependencies
------------
This plugin depends on [EntityMeta](https://nerdnu.github.io/EntityMeta/).
