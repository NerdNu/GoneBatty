package io.totemo.gonebatty;

import java.util.List;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import nu.nerd.entitymeta.EntityMeta;

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
     * On creature spawn, tag spawner spawns with persistent metadata indicating
     * that they came from a spawner.
     *
     * GoneBatty does not allow spawner mobs to drop heads when killed by
     * players, although they do drop heads when killed by charged creepers.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityMeta.api().set(event.getEntity(), this, "spawn-reason", event.getSpawnReason().name());
    }

    // ------------------------------------------------------------------------
    /**
     * Tag mobs hurt by players.
     *
     * Only those mobs hurt recently by players will have special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        LivingEntity living = (LivingEntity) victim;
        double finalHealth = (living.getHealth() - event.getFinalDamage());
        Entity damager = event.getDamager();

        if (CONFIG.DEBUG_EVENTS) {
            getLogger().info("onEntityDamageByEntity: " + victim.getType() + " " + Util.shortUuid(victim) +
                             " final health " + finalHealth +
                             " by " + damager.getType() + " " + Util.shortUuid(damager));
        }

        if (damager instanceof Creeper) {
            // Update ChargedCreeperExplosion meta when player/mob *killed* by
            // creeper.
            Creeper creeper = (Creeper) damager;
            if (finalHealth <= 0 && creeper.isPowered() && canBeDecapitatedByChargedCreeper(victim)) {
                ChargedCreeperExplosion meta = getChargedCreeperExplosion(creeper, true);
                if (canDropVanillaHead(victim)) {
                    if (CONFIG.DEBUG_EVENTS) {
                        getLogger().info("Vanilla decapitation expected.");
                    }
                    meta.vanillaHeadDropped = true;
                } else if (meta.firstVictim == null) {
                    meta.firstVictim = victim;
                    if (CONFIG.DEBUG_EVENTS) {
                        getLogger().info("Staging non-vanilla decapitation: " + victim.getType() + " " + Util.shortUuid(victim));
                    }
                }
            }
        } else {
            if (canBeDecapitatedByPlayer(victim)) {
                String playerName = "";
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
                    victim.setMetadata(PLAYER_NAME_KEY, new FixedMetadataValue(this, playerName));
                    victim.setMetadata(PLAYER_DAMAGE_TIME_KEY, new FixedMetadataValue(this, new Long(victim.getWorld().getFullTime())));
                    victim.setMetadata(PLAYER_LOOTING_LEVEL_KEY, new FixedMetadataValue(this, lootingLevel));
                }
            }
        }
    } // onEntityDamageByEntity

    // ------------------------------------------------------------------------
    /**
     * On hostile mob death, do special drops if a player hurt the mob recently.
     *
     * Special drops are allowed in the end, even though the end grinder is
     * cheap, by scaling the drop chance per-world.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (canBeDecapitatedByPlayer(entity)) {
            if (CONFIG.DEBUG_EVENTS) {
                getLogger().info("onEntityDeath: " + entity.getType() + " " + Util.shortUuid(entity));
            }

            int lootingLevel = getLootingLevelMeta(entity);
            Long damageTime = getPlayerDamageTime(entity);
            if (damageTime != null) {
                Location loc = entity.getLocation();
                if (loc.getWorld().getFullTime() - damageTime < PLAYER_DAMAGE_TICKS) {
                    doCustomDrops(event.getEntity(), lootingLevel);
                }
            }
        }
    } // onEntityDeath

    // ------------------------------------------------------------------------
    /**
     * Handle charged creeper explosions by dropping the head of the first
     * victim to die (in a previous EntityDamageByEntityEvent).
     * 
     * The custom head drop is prevented by a prior vanilla head drop.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Creeper) {
            if (CONFIG.DEBUG_EVENTS) {
                getLogger().info("onEntityExplode: " + entity.getType() + " " + Util.shortUuid(entity));
            }

            Creeper creeper = (Creeper) entity;
            if (creeper.isPowered()) {
                ChargedCreeperExplosion meta = getChargedCreeperExplosion(creeper, false);
                if (meta != null && !meta.vanillaHeadDropped && meta.firstVictim != null) {
                    String victimName = null;
                    ItemStack droppedHead = null;

                    if (meta.firstVictim.getType() == EntityType.PLAYER) {
                        if (CONFIG.CHARGED_CREEPERS_DECAPITATE_PLAYERS) {
                            Player player = (Player) meta.firstVictim;
                            victimName = player.getName();
                            droppedHead = new ItemStack(Material.PLAYER_HEAD);
                            SkullMeta skullMeta = (SkullMeta) droppedHead.getItemMeta();
                            skullMeta.setOwningPlayer(player);
                            droppedHead.setItemMeta(skullMeta);
                        }
                    } else {
                        if (CONFIG.CHARGED_CREEPERS_DECAPITATE_MOBS) {
                            victimName = CONFIG.getCreatureTypeString(meta.firstVictim);
                            droppedHead = CONFIG.HEAD_ITEMS.get(victimName);
                        }
                    }
                    if (droppedHead != null) {
                        Location loc = meta.firstVictim.getLocation();
                        loc.getWorld().dropItem(loc, droppedHead);
                        if (CONFIG.DEBUG_DROPS) {
                            getLogger().info("Charged creeper exploded, decapitating " + victimName +
                                             " at " + Util.formatLocation(loc));
                        }
                    }
                }
            }
        }
    } // onEntityExplode

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
     * @param lootingLevel the level of looting on the weapon ([0,3]).
     */
    protected void doCustomDrops(Entity entity, int lootingLevel) {
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
                world.dropItem(loc, CONFIG.ESSENCE_OF_FLIGHT);
                if (CONFIG.DEBUG_DROPS) {
                    getLogger().info(playerName + " killed " + CONFIG.getCreatureTypeString(entity) +
                                     " which dropped Essence of Flight at " + Util.formatLocation(loc));
                }
            }
        }

        // Player-caused decapitation.
        if (CONFIG.HEAD_ENABLED || CONFIG.PLAYERS_DECAPITATE_PLAYERS) {
            double headChance = CONFIG.HEAD_CHANCE *
                                CONFIG.getHeadDropScale(entity) *
                                CONFIG.getWorldDropScale(world) *
                                adjustedChance(lootingLevel);
            if (CONFIG.DEBUG_CHANCE) {
                getLogger().info("Head drop chance: " + headChance);
            }

            if (_random.nextDouble() < headChance) {
                String victimName = null;
                ItemStack droppedHead = null;
                if (entity instanceof Player) {
                    if (CONFIG.PLAYERS_DECAPITATE_PLAYERS) {
                        Player victim = (Player) entity;
                        victimName = victim.getName();
                        droppedHead = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta skullMeta = (SkullMeta) droppedHead.getItemMeta();
                        skullMeta.setOwningPlayer(victim);
                        droppedHead.setItemMeta(skullMeta);
                    }
                } else if (CONFIG.HEAD_ENABLED) {
                    victimName = CONFIG.getCreatureTypeString(entity);
                    droppedHead = CONFIG.HEAD_ITEMS.get(victimName);
                }

                if (droppedHead != null) {
                    world.dropItem(loc, droppedHead);
                    if (CONFIG.DEBUG_DROPS) {
                        getLogger().info(playerName + " decapitated " + victimName +
                                         " at " + Util.formatLocation(loc));
                    }
                }
            } // roll the dice
        } // Player-caused decapitation.
    } // doCustomDrops

    // ------------------------------------------------------------------------
    /**
     * Returns true if the specified entity can drop its head when killed by a
     * charged creeper in vanilla Minecraft.
     * 
     * @returns true if the specified entity can drop its head when killed by a
     *          charged creeper in vanilla Minecraft.
     */
    protected boolean canDropVanillaHead(Entity entity) {
        switch (entity.getType()) {
        case SKELETON:
        case WITHER_SKELETON:
        case CREEPER:
        case ZOMBIE:
            // Not players or ender dragons in vanilla.
            return true;
        default:
            return false;
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity can be decapitated by a charged
     * creeper.
     * 
     * @param entity the entity.
     * @return true if the specified entity can be decapitated by a charged
     *         creeper.
     */
    protected boolean canBeDecapitatedByChargedCreeper(Entity entity) {
        if (entity.getType() == EntityType.PLAYER) {
            return CONFIG.CHARGED_CREEPERS_DECAPITATE_PLAYERS;
        } else {
            return (entity instanceof LivingEntity &&
                    entity.getType() != EntityType.ARMOR_STAND &&
                    CONFIG.CHARGED_CREEPERS_DECAPITATE_MOBS);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity type is eligible for custom drops.
     *
     * @param entity the entity.
     * @return true if the specified entity is eligible for custom drops.
     */
    protected boolean canBeDecapitatedByPlayer(Entity entity) {
        if (entity instanceof LivingEntity &&
            entity.getType() != EntityType.ARMOR_STAND) {
            String spawnReason = (String) EntityMeta.api().get(entity, this, "spawn-reason");
            return spawnReason == null || !spawnReason.equals("SPAWNER");
        }
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the ChargedCreeperExplosion metadata instances associated with a
     * charged creeper, creating and attaching it as necessary.
     * 
     * @param creeper the charged creeper.
     * @param create if true, create and attach the metadata if necessary;
     *        otherwise, return null if not present.
     * @return a ChargedCreeperExplosion reference; can be null if create is
     *         false.
     */
    protected ChargedCreeperExplosion getChargedCreeperExplosion(Creeper creeper, boolean create) {
        List<MetadataValue> metas = creeper.getMetadata(CHARGED_CREEPER_KEY);
        if (!metas.isEmpty()) {
            return (ChargedCreeperExplosion) metas.get(0);
        } else if (create) {
            ChargedCreeperExplosion meta = new ChargedCreeperExplosion(this);
            creeper.setMetadata(CHARGED_CREEPER_KEY, meta);
            return meta;
        } else {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    /**
     * The type of metadata associated with mobs killed by a charged creeper
     * explosion. Also applies to players if they are configured to drop heads
     * in that situation.
     */
    private static final class ChargedCreeperExplosion extends FixedMetadataValue {
        /**
         * Constructor.
         * 
         * @param owningPlugin the owning plugin.
         */
        public ChargedCreeperExplosion(Plugin owningPlugin) {
            super(owningPlugin, null);
        }

        /**
         * Convert to String for debugging.
         * 
         * @return the String form of this object.
         */
        @Override
        public String toString() {
            return vanillaHeadDropped ? "vanilla head drop"
                                      : firstVictim.getType() + " " + Util.shortUuid(firstVictim);
        }

        /**
         * Set to true if the charged creeper killed a mob that would drop its
         * head in vanilla. That suppresses the custom head drop.
         */
        boolean vanillaHeadDropped;

        /**
         * A reference to the first mob (or player) killed by a charged creeper
         * explosion. If there was no vanilla head drop, then this entity will
         * frop its head.
         */
        Entity firstVictim;
    }

    // ------------------------------------------------------------------------
    /**
     * Plugin name; used to generate unique String keys.
     */
    protected static final String PLUGIN_NAME = "GoneBatty";

    /**
     * Metadata key for looking up ChargedCreeperExplosion metadata attached to
     * exploding charged creepers.
     */
    protected static final String CHARGED_CREEPER_KEY = PLUGIN_NAME + "_ChargedCreeper";

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