package io.totemo.gonebatty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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
     * If true, spawn eggs are treated the same as natural spawns (useful for
     * testing and debugging).
     */
    public boolean DEBUG_ALLOW_SPAWN_EGGS;

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
     * The per-world drop chance multiplier.
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
     * Scaling factor applied to the looting-adjusted head drop chance.
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
     * Scaling factor applied to the looting-adjusted essence of flight drop
     * chance according to how flight-capable a mob is.
     */
    public HashMap<String, Double> EOF_MOB_FACTOR = new HashMap<String, Double>();

    // ------------------------------------------------------------------------
    /**
     * Load the plugin configuration.
     */
    public void reload() {
        GoneBatty.PLUGIN.reloadConfig();

        DEBUG_CONFIG = GoneBatty.PLUGIN.getConfig().getBoolean("debug.config");
        DEBUG_CHANCE = GoneBatty.PLUGIN.getConfig().getBoolean("debug.chance");
        DEBUG_DROPS = GoneBatty.PLUGIN.getConfig().getBoolean("debug.drops");
        DEBUG_ALLOW_SPAWN_EGGS = GoneBatty.PLUGIN.getConfig().getBoolean("debug.allow_spawn_eggs");

        LOOTING_BASE = GoneBatty.PLUGIN.getConfig().getDouble("looting_base");

        ConfigurationSection worlds = GoneBatty.PLUGIN.getConfig().getConfigurationSection("world.factor");
        WORLD_FACTOR.clear();
        for (String world : worlds.getKeys(false)) {
            WORLD_FACTOR.put(world, worlds.getDouble(world));
        }

        HEAD_ENABLED = GoneBatty.PLUGIN.getConfig().getBoolean("drops.head.enabled");
        HEAD_CHANCE = GoneBatty.PLUGIN.getConfig().getDouble("drops.head.chance");
        ConfigurationSection headMobFactor = GoneBatty.PLUGIN.getConfig().getConfigurationSection("drops.head.scale");
        HEAD_MOB_FACTOR.clear();
        for (String mobType : headMobFactor.getKeys(false)) {
            Double factor = headMobFactor.getDouble(mobType);
            HEAD_MOB_FACTOR.put(mobType, factor);
        }

        HEAD_ITEMS.clear();
        HEAD_ITEMS.put("SKELETON", new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.SKELETON.ordinal()));
        HEAD_ITEMS.put("WITHER_SKELETON", new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.WITHER.ordinal()));
        HEAD_ITEMS.put("ZOMBIE", new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.ZOMBIE.ordinal()));
        HEAD_ITEMS.put("CREEPER", new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.CREEPER.ordinal()));
        HEAD_ITEMS.put("ENDER_DRAGON", new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.DRAGON.ordinal()));
        ConfigurationSection headOwners = GoneBatty.PLUGIN.getConfig().getConfigurationSection("drops.head.owner");
        for (String mobType : headOwners.getKeys(false)) {
            ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.PLAYER.ordinal());
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwner(headOwners.getString(mobType));
            head.setItemMeta(meta);
            HEAD_ITEMS.put(mobType, head);
        }

        ESSENCE_OF_FLIGHT_ENABLED = GoneBatty.PLUGIN.getConfig().getBoolean("drops.essence_of_flight.enabled");
        ESSENCE_OF_FLIGHT_CHANCE = GoneBatty.PLUGIN.getConfig().getDouble("drops.essence_of_flight.chance");
        ESSENCE_OF_FLIGHT = GoneBatty.PLUGIN.getConfig().getItemStack("drops.essence_of_flight.item");
        ConfigurationSection mobFlightFactor = GoneBatty.PLUGIN.getConfig().getConfigurationSection("drops.essence_of_flight.scale");
        EOF_MOB_FACTOR.clear();
        for (String mobType : mobFlightFactor.getKeys(false)) {
            Double factor = mobFlightFactor.getDouble(mobType);
            EOF_MOB_FACTOR.put(mobType, factor);
        }

        if (DEBUG_CONFIG) {
            GoneBatty.PLUGIN.getLogger().info("DEBUG_CONFIG: " + DEBUG_CONFIG);
            GoneBatty.PLUGIN.getLogger().info("LOOTING_BASE: " + LOOTING_BASE);
            GoneBatty.PLUGIN.getLogger().info("World drop factors:");
            for (Entry<String, Double> entry : WORLD_FACTOR.entrySet()) {
                GoneBatty.PLUGIN.getLogger().info(entry.getKey() + ": " + entry.getValue());
            }

            GoneBatty.PLUGIN.getLogger().info("HEAD_CHANCE: " + HEAD_CHANCE);

            // Combine the mob type keys for head drop factors and head item.
            // Then show the combined information for both.
            HashSet<String> mobTypes = new HashSet<String>(HEAD_MOB_FACTOR.keySet());
            mobTypes.addAll(HEAD_ITEMS.keySet());
            GoneBatty.PLUGIN.getLogger().info("Mob head drop factors and items:");
            for (String mobType : mobTypes) {
                Double factor = HEAD_MOB_FACTOR.get(mobType);
                if (factor == null) {
                    factor = 1.0;
                }
                ItemStack head = HEAD_ITEMS.get(mobType);
                String owner;
                if (head != null) {
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    owner = (meta.getOwner() != null) ? meta.getOwner() : "HEAD_ITEM:" + head.getDurability();
                } else {
                    owner = "none";
                }
                GoneBatty.PLUGIN.getLogger().info(mobType + ": " + factor + " " + owner);
            }

            GoneBatty.PLUGIN.getLogger().info("ESSENCE_OF_FLIGHT: " + ESSENCE_OF_FLIGHT);
            GoneBatty.PLUGIN.getLogger().info("ESSENCE_OF_FLIGHT_CHANCE: " + ESSENCE_OF_FLIGHT_CHANCE);
            GoneBatty.PLUGIN.getLogger().info("Mob EoF drop factors:");
            for (Entry<String, Double> entry : EOF_MOB_FACTOR.entrySet()) {
                GoneBatty.PLUGIN.getLogger().info(entry.getKey() + ": " + entry.getValue());
            }
        }
    } // reload

    // ------------------------------------------------------------------------
    /**
     * Get a unique string identifier for the creature type that takes into
     * account variants.
     *
     * In most cases this is just the upper case name of the EntityType enum.
     *
     * @return the creature type identifier string.
     */
    @SuppressWarnings("deprecation")
    public String getCreatureTypeString(Entity creature) {
        if (creature.getType() == EntityType.SKELETON) {
            Skeleton skeleton = (Skeleton) creature;
            switch (skeleton.getSkeletonType()) {
            case WITHER:
                return "WITHER_SKELETON";
            case STRAY:
                return "STRAY";
            case NORMAL:
            default:
                return "SKELETON";
            }
        } else if (creature.getType() == EntityType.ZOMBIE) {
            Zombie zombie = (Zombie) creature;
            return (zombie.getVillagerProfession() == Profession.HUSK) ? "HUSK" : "ZOMBIE";
        } else {
            return creature.getType().name();
        }
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