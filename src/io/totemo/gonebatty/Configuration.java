package io.totemo.gonebatty;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

// ----------------------------------------------------------------------------
/**
 * Reads and exposes the plugin configuration.
 */
public class Configuration {
    /**
     * If true, log configuration loading.
     */
    public boolean DEBUG_CONFIG;

    /**
     * If true, log drop chances.
     */
    public boolean DEBUG_CHANCE;

    /**
     * If true, log drops.
     */
    public boolean DEBUG_DROPS;

    /**
     * If true, do debug logging of entity damage and death events.
     */
    public boolean DEBUG_EVENTS;

    /**
     * The exponential base for the drop chance multiplies calculation when the
     * player has the looting enchant.
     *
     * Given a weapon with a looting level of n, where an unenchanted weapon has
     * n=0, the drop chance of an item is that listed in the configuration
     * multiplied by Math.pow(LOOTING_BASE,n).
     *
     * For example, if LOOTING_BASE is 1.3, Looting 3 gives a drop chance
     * multiplier of ~2.2.
     */
    public double LOOTING_BASE;

    /**
     * If true, the vanilla charged creeper decapitation mechanic is extended to
     * all mobs with a configured head.
     */
    public boolean CHARGED_CREEPERS_DECAPITATE_MOBS;

    /**
     * If true, charged creepers can decapitate players.
     */
    public boolean CHARGED_CREEPERS_DECAPITATE_PLAYERS;

    /**
     * If true, players can decapitate players in PvP.
     */
    public boolean PLAYERS_DECAPITATE_PLAYERS;

    /**
     * Map from world name to the per-world drop chance multiplier.
     *
     * The multiplicative factor for world_the_end should be set very low to
     * account for the cheapness of the end grinder. If a world is not listed,
     * the factor defaults to 0 (impossible).
     */
    public HashMap<String, Double> WORLD_FACTOR = new HashMap<String, Double>();

    /**
     * If true, head drops are enabled.
     */
    public boolean HEAD_ENABLED;

    /**
     * Base chance of a head drop.
     */
    public double HEAD_CHANCE;

    /**
     * Map from EntityType.name() to scaling factor applied to the
     * looting-adjusted head drop chance.
     */
    public HashMap<String, Double> HEAD_MOB_FACTOR = new HashMap<String, Double>();

    /**
     * Map from EntityType.name() (plus custom IDs) to HEAD_ITEMS to drop, or
     * null for no head drop.
     */
    public HashMap<String, ItemStack> HEAD_ITEMS = new HashMap<String, ItemStack>();

    /**
     * If true, Essence of Flight drops are enabled.
     */
    public boolean ESSENCE_OF_FLIGHT_ENABLED;

    /**
     * Essence of Flight drop chance.
     */
    public double ESSENCE_OF_FLIGHT_CHANCE;

    /**
     * Essence of Flight drop.
     */
    public ItemStack ESSENCE_OF_FLIGHT;

    /**
     * Map from EntityType.name() to scaling factor applied to the
     * looting-adjusted essence of flight drop chance according to how
     * flight-capable a mob is.
     */
    public HashMap<String, Double> EOF_MOB_FACTOR = new HashMap<String, Double>();

    // ------------------------------------------------------------------------
    /**
     * Load the plugin configuration.
     */
    public void reload() {
        Plugin plugin = GoneBatty.PLUGIN;
        plugin.reloadConfig();

        // NOTE: reloadConfig() alters result of getConfig().
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        DEBUG_CONFIG = config.getBoolean("debug.config");
        DEBUG_CHANCE = config.getBoolean("debug.chance");
        DEBUG_DROPS = config.getBoolean("debug.drops");
        DEBUG_EVENTS = config.getBoolean("debug.events");

        LOOTING_BASE = config.getDouble("looting_base");
        CHARGED_CREEPERS_DECAPITATE_MOBS = config.getBoolean("charged_creepers_decapitate_mobs");
        CHARGED_CREEPERS_DECAPITATE_PLAYERS = config.getBoolean("charged_creepers_decapitate_players");
        PLAYERS_DECAPITATE_PLAYERS = config.getBoolean("players_decapitate_players");

        ConfigurationSection worlds = config.getConfigurationSection("world.factor");
        WORLD_FACTOR.clear();
        for (String world : worlds.getKeys(false)) {
            WORLD_FACTOR.put(world, worlds.getDouble(world));
        }

        HEAD_ENABLED = config.getBoolean("drops.head.enabled");
        HEAD_CHANCE = config.getDouble("drops.head.chance");
        ConfigurationSection headMobFactor = config.getConfigurationSection("drops.head.scale");
        HEAD_MOB_FACTOR.clear();
        for (String mobType : headMobFactor.getKeys(false)) {
            Double factor = headMobFactor.getDouble(mobType);
            HEAD_MOB_FACTOR.put(mobType, factor);
        }

        HEAD_ITEMS.clear();
        ConfigurationSection headItems = config.getConfigurationSection("drops.head.item");
        for (String mobType : headItems.getKeys(false)) {
            ItemStack head = headItems.getItemStack(mobType);
            HEAD_ITEMS.put(mobType, head);
        }

        ESSENCE_OF_FLIGHT_ENABLED = config.getBoolean("drops.essence_of_flight.enabled");
        ESSENCE_OF_FLIGHT_CHANCE = config.getDouble("drops.essence_of_flight.chance");
        ESSENCE_OF_FLIGHT = config.getItemStack("drops.essence_of_flight.item");
        ConfigurationSection mobFlightFactor = config.getConfigurationSection("drops.essence_of_flight.scale");
        EOF_MOB_FACTOR.clear();
        for (String mobType : mobFlightFactor.getKeys(false)) {
            Double factor = mobFlightFactor.getDouble(mobType);
            EOF_MOB_FACTOR.put(mobType, factor);
        }

        if (DEBUG_CONFIG) {
            logger.info("DEBUG_CHANCE: " + DEBUG_CHANCE);
            logger.info("DEBUG_DROPS: " + DEBUG_DROPS);
            logger.info("DEBUG_EVENTS: " + DEBUG_EVENTS);

            logger.info("LOOTING_BASE: " + LOOTING_BASE);
            logger.info("CHARGED_CREEPERS_DECAPITATE_MOBS: " + CHARGED_CREEPERS_DECAPITATE_MOBS);
            logger.info("CHARGED_CREEPERS_DECAPITATE_PLAYERS: " + CHARGED_CREEPERS_DECAPITATE_PLAYERS);
            logger.info("PLAYERS_DECAPITATE_PLAYERS: " + PLAYERS_DECAPITATE_PLAYERS);

            logger.info("World drop factors:");
            for (Entry<String, Double> entry : WORLD_FACTOR.entrySet()) {
                logger.info(entry.getKey() + ": " + entry.getValue());
            }

            logger.info("HEAD_ENABLED: " + HEAD_ENABLED);
            logger.info("HEAD_CHANCE: " + HEAD_CHANCE);

            // Combine the mob type keys for head drop factors and head item.
            // Then show the combined information for both.
            TreeSet<String> mobTypes = new TreeSet<String>(HEAD_MOB_FACTOR.keySet());
            mobTypes.addAll(HEAD_ITEMS.keySet());

            if (PLAYERS_DECAPITATE_PLAYERS) {
                // Force startup logging of player head drop rate.
                mobTypes.add("PLAYER");
            }

            logger.info("Mob head drop factors and items:");
            for (String mobType : mobTypes) {
                Double factor = HEAD_MOB_FACTOR.get(mobType);
                if (factor == null) {
                    factor = 1.0;
                }

                ItemStack headItem = HEAD_ITEMS.get(mobType);
                String head;
                if (headItem != null) {
                    if (headItem.getType() == Material.PLAYER_HEAD) {
                        SkullMeta meta = (SkullMeta) headItem.getItemMeta();
                        OfflinePlayer owner = meta.getOwningPlayer();
                        head = (owner != null && owner.getName() != null) ? owner.getName()
                                                                          : headItem.getType().name();
                    } else {
                        head = headItem.getType().name();
                    }
                } else {
                    // Say that players drop their own heads, if configured.
                    head = (mobType.equals("PLAYER") && PLAYERS_DECAPITATE_PLAYERS) ? "own" : "none";
                }
                logger.info(mobType + ": " + factor + " " + head);
            }

            logger.info("Essence of flight settings:");
            logger.info("ESSENCE_OF_FLIGHT_ENABLED: " + ESSENCE_OF_FLIGHT_ENABLED);
            logger.info("ESSENCE_OF_FLIGHT: " + ESSENCE_OF_FLIGHT);
            logger.info("ESSENCE_OF_FLIGHT_CHANCE: " + ESSENCE_OF_FLIGHT_CHANCE);
            logger.info("Mob EoF drop factors:");
            for (String mobType : new TreeSet<String>(EOF_MOB_FACTOR.keySet())) {
                logger.info(mobType + ": " + EOF_MOB_FACTOR.get(mobType));
            }
        }
    } // reload

    // ------------------------------------------------------------------------
    /**
     * Save the configuration.
     */
    public void save() {
        // Only the HEAD_ITEMS changed. Rest of the settings intact.
        FileConfiguration config = GoneBatty.PLUGIN.getConfig();
        ConfigurationSection headSection = config.createSection("drops.head.item");
        for (Entry<String, ItemStack> entry : HEAD_ITEMS.entrySet()) {
            headSection.set(entry.getKey(), entry.getValue());
        }
        GoneBatty.PLUGIN.saveConfig();
    }

    // ------------------------------------------------------------------------
    /**
     * Get a unique string identifier for the creature type that takes into
     * account variants.
     *
     * In 1.10, this method was non-trivial, but as of 1.11, all of the mob
     * variants that the plugin distinguishes have their own EntityType
     * constant.
     *
     * @return the creature type identifier string.
     */
    public String getCreatureTypeString(Entity creature) {
        return creature.getType().name();
    }

    // ------------------------------------------------------------------------
    /**
     * Return the scaling factor applied to all drop chances in the specified
     * world.
     *
     * @param world the world.
     * @return the scaling factor applied to all drop chances in the specified
     *         world.
     */
    public double getWorldDropScale(World world) {
        Double factor = WORLD_FACTOR.get(world.getName());
        return (factor != null) ? factor : 0.0;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the scaling factor multiplied into the looting-adjusted drop
     * chance of mob heads, according to an entity's (mob's) type.
     *
     * @param entity the killed mob.
     * @return the scaling factor multiplied into the looting-adjusted drop
     *         chance.
     */
    public double getHeadDropScale(Entity entity) {
        Double factor = HEAD_MOB_FACTOR.get(getCreatureTypeString(entity));
        return (factor != null) ? factor : 1.0;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the scaling factor multiplied into the looting-adjusted drop
     * chance of Essence of Flight, according to an entity's (mob's) type.
     *
     * @param entity the killed mob.
     * @return the scaling factor multiplied into the looting-adjusted drop
     *         chance.
     */
    public double getEssenceOfFlightDropScale(Entity entity) {
        Double factor = EOF_MOB_FACTOR.get(getCreatureTypeString(entity));
        return (factor != null) ? factor : 0.0;
    }
} // class Configuration