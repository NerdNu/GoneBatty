package io.totemo.gonebatty;

import java.util.List;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

// ----------------------------------------------------------------------------
/**
 * Custom drops for PvE Rev 18.
 *
 * This is a very specific implementation of those gameplay features. A more
 * general implementation will come later.
 *
 * The current features are:
 * <ul>
 * <li>Overworld mobs drop "Essence of Flight" (named, enchanted ghast tear).
 * </li>
 * <li>Mobs have a chance to drop their heads.</li>
 * </ul>
 */
public class GoneBatty extends JavaPlugin implements Listener {
    /**
     * Configuration wrapper instance.
     */
    public static Configuration CONFIG = new Configuration();

    /**
     * This plugin, accessible as, effectively, a singleton.
     */
    public static GoneBatty PLUGIN;

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        PLUGIN = this;

        saveDefaultConfig();
        CONFIG.reload();

        getServer().getPluginManager().registerEvents(this, this);
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender,
     *      org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(getName())) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                CONFIG.reload();
                sender.sendMessage(ChatColor.GOLD + getName() + " configuration reloaded.");
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * On creature spawn, tag natural spawns with a metadata value indicating
     * that they *are* naturally spawned.
     *
     * Also tag CUSTOM spawns the same way, so that mobs spawned by ItsATrap and
     * other plugins are eligible to drop heads and Essence of Flight.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == SpawnReason.NATURAL ||
            event.getSpawnReason() == SpawnReason.BREEDING ||
            event.getSpawnReason() == SpawnReason.EGG ||
            event.getSpawnReason() == SpawnReason.SILVERFISH_BLOCK ||
            event.getSpawnReason() == SpawnReason.CUSTOM ||
            event.getSpawnReason() == SpawnReason.MOUNT ||
            event.getSpawnReason() == SpawnReason.JOCKEY ||
            event.getSpawnReason() == SpawnReason.LIGHTNING ||
            event.getSpawnReason() == SpawnReason.TRAP ||
            event.getSpawnReason() == SpawnReason.BUILD_IRONGOLEM ||
            event.getSpawnReason() == SpawnReason.BUILD_SNOWMAN ||
            (CONFIG.DEBUG_ALLOW_SPAWN_EGGS && event.getSpawnReason() == SpawnReason.SPAWNER_EGG)) {
            event.getEntity().setMetadata(NATURAL_KEY, new FixedMetadataValue(this, Boolean.TRUE));
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Tag mobs hurt by players.
     *
     * Only those mobs hurt recently by players will have special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        String playerName = "";
        if (isEligibleMob(entity)) {
            int lootingLevel = 0;
            boolean isPlayerAttack = false;
            if (event.getDamager() instanceof Player) {
                isPlayerAttack = true;
                Player player = (Player) event.getDamager();
                playerName = player.getName();
                lootingLevel = player.getEquipment().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
            } else if (event.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) event.getDamager();
                if (projectile.getShooter() instanceof Player) {
                    playerName = ((Player) projectile.getShooter()).getName();
                    isPlayerAttack = true;
                }
            }

            // Tag mobs hurt by players with the damage time stamp.
            if (isPlayerAttack) {
                entity.setMetadata(PLAYER_NAME_KEY, new FixedMetadataValue(this, playerName));
                entity.setMetadata(PLAYER_DAMAGE_TIME_KEY, new FixedMetadataValue(this, new Long(entity.getWorld().getFullTime())));
                entity.setMetadata(PLAYER_LOOTING_LEVEL_KEY, new FixedMetadataValue(this, lootingLevel));
            }
        }
    } // onEntityDamageByEntity

    // ------------------------------------------------------------------------
    /**
     * On hostile mob death, do special drops if a player hurt the mob recently.
     *
     * Special drops are allowed in the end, even though the end grinder is
     * cheap. The drop chance is scaled by mob type.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (isEligibleMob(entity)) {
            int lootingLevel = getLootingLevelMeta(entity);
            boolean specialDrops = false;
            Long damageTime = getPlayerDamageTime(entity);
            if (damageTime != null) {
                Location loc = entity.getLocation();
                if (loc.getWorld().getFullTime() - damageTime < PLAYER_DAMAGE_TICKS) {
                    specialDrops = true;
                }
            }

            doCustomDrops(event.getEntity(), specialDrops, lootingLevel);
        }
    } // onEntityDeath

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified creature is a natural spawn.
     *
     * @param creature the creature.
     * @return true if the specified creature is a natural spawn.
     */
    protected boolean isNaturalSpawn(Entity creature) {
        return creature.hasMetadata(NATURAL_KEY);
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity type is eligible for custom drops.
     *
     * @parma entity the entity.
     * @return true if the specified entity is eligible for custom drops.
     */
    protected boolean isEligibleMob(Entity entity) {
        return entity instanceof EnderDragon ||
               (isNaturalSpawn(entity) &&
                entity instanceof LivingEntity &&
                entity.getType() != EntityType.ARMOR_STAND);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the name of the player who killed a mob from metadata on the mob.
     *
     * This metadata is added when a player damages a mob. It is the level of
     * the Looting enchant on the weapon that did the damage, or 0 if there was
     * no such enchant.
     *
     * @param entity the damaged entity.
     * @return the name of the player who killed a mob from metadata on the mob.
     */
    protected String getPlayerNameMeta(Entity entity) {
        List<MetadataValue> name = entity.getMetadata(PLAYER_NAME_KEY);
        return (name.size() > 0) ? name.get(0).asString() : "";
    }

    // ------------------------------------------------------------------------
    /**
     * Return the world time when a player damaged the specified entity, if
     * stored as a PLAYER_DAMAGE_TIME_KEY metadata value, or null if that didn't
     * happen.
     *
     * @param entity the entity (mob).
     * @return the damage time stamp as Long, or null.
     */
    protected Long getPlayerDamageTime(Entity entity) {
        List<MetadataValue> playerDamageTime = entity.getMetadata(PLAYER_DAMAGE_TIME_KEY);
        if (playerDamageTime.size() > 0) {
            MetadataValue value = playerDamageTime.get(0);
            if (value.value() instanceof Long) {
                return (Long) value.value();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the looting level metadata value from a mob.
     *
     * This metadata is added when a player damages a mob. It is the level of
     * the Looting enchant on the weapon that did the damage, or 0 if there was
     * no such enchant.
     *
     * @param entity the damaged entity.
     * @return the level of the Looting enchant, or 0 if not so enchanted.
     */
    protected int getLootingLevelMeta(Entity entity) {
        List<MetadataValue> lootingLevel = entity.getMetadata(PLAYER_LOOTING_LEVEL_KEY);
        return (lootingLevel.size() > 0) ? lootingLevel.get(0).asInt() : 0;
    }

    // ------------------------------------------------------------------------
    /**
     * Return multiplicative factor to apply to the base drop chance according
     * to a given looting level.
     *
     * @param lootingLevel the looting level of the weapon.
     * @return a factor to be multiplied by the base drop chance to compute the
     *         actual drop chance.
     */
    protected double adjustedChance(int lootingLevel) {
        return Math.pow(CONFIG.LOOTING_BASE, lootingLevel);
    }

    // ------------------------------------------------------------------------
    /**
     * Do custom drops.
     *
     * @param entity the dropping entity (mob).
     * @param special if true, low-probability, special drops are possible;
     *        otherwise, the drops are custom but mundane.
     * @param lootingLevel the level of looting on the weapon ([0,3]).
     */
    protected void doCustomDrops(Entity entity, boolean special, int lootingLevel) {
        if (special) {
            String playerName = getPlayerNameMeta(entity);
            Location loc = entity.getLocation();
            World world = loc.getWorld();

            if (CONFIG.ESSENCE_OF_FLIGHT_ENABLED) {
                double eofChance = CONFIG.ESSENCE_OF_FLIGHT_CHANCE *
                                   CONFIG.getWorldDropScale(world) *
                                   CONFIG.getEssenceOfFlightDropScale(entity) *
                                   adjustedChance(lootingLevel);
                if (CONFIG.DEBUG_CHANCE) {
                    getLogger().info("Essence of Flight chance: " + eofChance);
                }

                if (_random.nextDouble() < eofChance) {
                    world.dropItemNaturally(loc, CONFIG.ESSENCE_OF_FLIGHT);
                    if (CONFIG.DEBUG_DROPS) {
                        getLogger().info(playerName + " killed " + CONFIG.getCreatureTypeString(entity) +
                                         " which dropped Essence of Flight at " + Util.formatLocation(loc));
                    }
                }
            }

            if (CONFIG.HEAD_ENABLED) {
                double headChance = CONFIG.HEAD_CHANCE *
                                    CONFIG.getWorldDropScale(world) *
                                    CONFIG.getHeadDropScale(entity) *
                                    adjustedChance(lootingLevel);
                if (CONFIG.DEBUG_CHANCE) {
                    getLogger().info("Head drop chance: " + headChance);
                }

                if (_random.nextDouble() < headChance) {
                    String type = CONFIG.getCreatureTypeString(entity);
                    ItemStack head = CONFIG.HEAD_ITEMS.get(type);
                    if (head != null) {
                        world.dropItemNaturally(loc, head);
                        if (CONFIG.DEBUG_DROPS) {
                            getLogger().info(playerName + " killed " + type +
                                             " which dropped mob head at " + Util.formatLocation(loc));
                        }
                    }
                }
            }
        }
    } // doCustomDrops

    // ------------------------------------------------------------------------
    /**
     * Plugin name; used to generate unique String keys.
     */
    protected static final String PLUGIN_NAME = "GoneBatty";

    /**
     * Metadata used to tag naturally spawned mobs.
     */
    protected static final String NATURAL_KEY = PLUGIN_NAME + "_NaturalSpawn";

    /**
     * Metadata name used for metadata stored on mobs to record the name of the
     * player who most recently damaged a mob.
     *
     * This is used to include more informative debug logging of drops.
     */
    protected static final String PLAYER_NAME_KEY = PLUGIN_NAME + "_PlayerName";

    /**
     * Metadata name used for metadata stored on mobs to record last damage time
     * (Long) by a player.
     */
    protected static final String PLAYER_DAMAGE_TIME_KEY = PLUGIN_NAME + "_PlayerDamageTime";

    /**
     * Metadata name used for metadata stored on mobs to record looting
     * enchantment level of Looting weapon used by a player.
     */
    protected static final String PLAYER_LOOTING_LEVEL_KEY = PLUGIN_NAME + "_PlayerLootingLevel";

    /**
     * Time in ticks (1/20ths of a second) for which player attack damage
     * "sticks" to a mob. The time between the last player damage on a mob and
     * its death must be less than this for it to drop special stuff.
     */
    protected static final int PLAYER_DAMAGE_TICKS = 100;

    /**
     * Random number generator.
     */
    protected Random _random = new Random();

} // class GoneBatty