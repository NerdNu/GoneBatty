package io.totemo.gonebatty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
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
        if (!command.getName().equalsIgnoreCase(getName())) {
            return false;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            CONFIG.reload();
            sender.sendMessage(ChatColor.GOLD + getName() + " configuration reloaded.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("set-head")) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /gonebatty set-head <type>");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("You must be in game to use this command.");
                return true;
            }
            Player player = (Player) sender;

            String type = args[1].toUpperCase();
            if (!validateMobType(sender, type)) {
                return true;
            }

            ItemStack head = player.getEquipment().getItemInMainHand();
            if (head.getType() == Material.AIR) {
                CONFIG.HEAD_ITEMS.remove(type);
                sender.sendMessage(ChatColor.GOLD + "Head item for mob type " +
                                   ChatColor.YELLOW + type + ChatColor.GOLD + " cleared.");
                CONFIG.save();
            } else {
                if (HEAD_MATERIALS.contains(head.getType())) {
                    ItemStack single = head.clone();
                    single.setAmount(1);
                    CONFIG.HEAD_ITEMS.put(type, single);
                    sender.sendMessage(ChatColor.GOLD + "You set the head for mob type " +
                                       ChatColor.YELLOW + type + ChatColor.GOLD + ".");
                    CONFIG.save();
                } else {
                    sender.sendMessage(ChatColor.RED + "The item in your hand is not a head!");
                }
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("get-head")) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /gonebatty get-head <type>");
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage("You must be in game to use this command.");
                return true;
            }
            Player player = (Player) sender;

            String type = args[1].toUpperCase();
            if (!validateMobType(sender, type)) {
                return true;
            }

            // If the item doesn't fit in the player's inventory, drop it.
            ItemStack item = CONFIG.HEAD_ITEMS.get(type);
            if (item == null) {
                sender.sendMessage(ChatColor.RED + "Type " + type + " does not have a head drop configured.");
                return true;
            } else {
                Location loc = player.getLocation();
                HashMap<Integer, ItemStack> didntFit = player.getInventory().addItem(item.clone());
                for (ItemStack drop : didntFit.values()) {
                    loc.getWorld().dropItemNaturally(loc, drop);
                }
                if (didntFit.size() == 0) {
                    sender.sendMessage(ChatColor.YELLOW + type + ChatColor.GOLD + " head given!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + type + ChatColor.GOLD + " dropped near you! (Your inventory is full.)");
                }
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list-heads")) {
            TreeSet<String> mobTypes = new TreeSet<String>(
                Stream.of(EntityType.values())
                    .filter(t -> t != EntityType.ARMOR_STAND && t.isAlive())
                    .map(Object::toString)
                    .toList());
            sender.sendMessage(ChatColor.GOLD + "Configurable mob types: " +
                               mobTypes.stream()
                                   .map(t -> (CONFIG.HEAD_ITEMS.get(t) == null ? ChatColor.RED : ChatColor.GREEN) + t)
                                   .collect(Collectors.joining(ChatColor.GRAY + ", ")));
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax. Try \"/gonebatty help\" for help.");
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
        if (CONFIG.DEBUG_EVENTS) {
            Entity entity = event.getEntity();
            getLogger().info("onCreatureSpawn: " + entity.getType() + " " + Util.shortUuid(entity) +
                             " " + event.getSpawnReason());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Tag mobs hurt by players.
     *
     * Only those mobs hurt recently by players will have special drops.
     *
     * Note that {@link #onEntityExplode(EntityExplodeEvent)} is called AFTER
     * the {@link #onEntityDeath(EntityDeathEvent)} event of the mobs dying in
     * the explosion. onEntityExplode() is where we drop the custom heads,
     * whereas in onEntityDeath() we simply replace vanilla head drops with
     * corresponding custom ones. Need to be careful about vanilla mobs wearing
     * vanilla heads.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
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
                    // Tag the vanilla mob with this meta so we can replace
                    // the vanilla head drop with the corresponding custom head.
                    victim.setMetadata(CHARGED_CREEPER_KEY, meta);
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
            if (canBeDecapitated(victim)) {
                String playerName = "";
                boolean isPlayerAttack = false;
                if (event.getDamager() instanceof Player) {
                    isPlayerAttack = true;
                    Player player = (Player) event.getDamager();
                    playerName = player.getName();
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
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (canBeDecapitated(entity)) {
            if (CONFIG.DEBUG_EVENTS) {
                getLogger().info("onEntityDeath: " + entity.getType() + " " + Util.shortUuid(entity));
            }

            // Replace vanilla head drops when a charged creeper blows up a mob.
            if (replaceVanillaHeadDrop(entity, event.getDrops())) {
                // The mob dropped a vanilla head due to a charged creeper
                // explosion, so therefore don't do player-caused decapitation,
                // because you might end up with more than one dropped head.
                return;
            }

            // The code that follows calculates drops when the player caused
            // the mob death. It is not run when the mob died due to charged
            // creeper explosion.
            Long damageTime = getPlayerDamageTime(entity);
            if (damageTime != null) {
                Location loc = entity.getLocation();
                if (loc.getWorld().getFullTime() - damageTime < PLAYER_DAMAGE_TICKS) {
                    // Calculate looting based on what the killing player is
                    // holding in his hands at the time the entity dies.
                    Player player = Bukkit.getPlayerExact(getPlayerNameMeta(entity));
                    if (player == null) {
                        return;
                    }
                    int lootingLevel = Math.max(player.getEquipment().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS),
                                                player.getEquipment().getItemInOffHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS));
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
     *
     * Testing in 1.13 reveals that a head only drops if it was an instakill
     * from the explosion; death from fall damage triggered by the charged
     * creeper explosion throwing the mob off a ledge does not produce a head
     * drop.
     *
     * Also, deaths from mobs that do not have a vanilla head drop do not
     * prevent the vanilla head drop.
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
     * Look at the drops of a mob killed by a charged creeper explosion, and if
     * a vanilla mob head was dropped, replace that with the corresponding
     * GoneBatty-custom head.
     *
     * For example, if a charged creeper explodes, killing a skeleton, Mojang's
     * vanilla code would add a skeleton skull to the skeleton's death drops.
     * This function would replace that with the configured head drop for
     * SKELETON, which probably is still a SKELETON_SKULL, but perhaps has a
     * custom name or lore.
     *
     * Complicating matters is that if the aforementioned SKELETON was wearing a
     * skeleton skull, then that skull should be dropped "as is" without being
     * replaced by the custom item. Testing this scenario in 1.19.2, it seems
     * that creeper explosions don't cause armour slot items to drop unless the
     * item was picked up (even when the armour drop chances are set to 100%).
     * Testing using a wither skeleton that has picked up and is wearing a
     * wither skeleton skull, it is apparent that two item stacks of one wither
     * skull each get added to the drops list by vanilla code. However, the code
     * deals with the possibility that two vanilla head drops have been stacked
     * together as one item stack.
     *
     * @param entity the mob that just died.
     * @param drops  the drops from the EntityDeathEvent.
     * @return true if a vanilla head drop was replaced with a custom head;
     *         otherwise false.
     */
    protected boolean replaceVanillaHeadDrop(Entity mob, List<ItemStack> drops) {
        // Did the mob die due to charged creeper explosion.
        if (!mob.hasMetadata(CHARGED_CREEPER_KEY)) {
            return false;
        }

        // Does the mob have a replaceable head?
        final Material vanillaHeadMaterial = VANILLA_HEAD_MATERIALS.get(mob.getType());
        if (vanillaHeadMaterial == null) {
            return false;
        }

        // Is there a configured custom head for this mob?
        final ItemStack replacementHead = CONFIG.HEAD_ITEMS.get(mob.getType().name());
        if (replacementHead == null) {
            return false;
        }

        // Remove one vanilla head from the drops so it can be replaced.
        final ItemStack vanillaHead = new ItemStack(vanillaHeadMaterial);
        ItemStack replacedDrop = null;
        Iterator<ItemStack> dropsIt = drops.iterator();
        while (dropsIt.hasNext()) {
            ItemStack drop = dropsIt.next();
            if (drop.isSimilar(vanillaHead)) {
                dropsIt.remove();
                replacedDrop = drop;
                break;
            }
        }

        // Did the drops include a head that can be replaced?
        if (replacedDrop == null) {
            return false;
        }

        // Is there more than one item in the replaced drop stack?
        if (replacedDrop.getAmount() > 1) {
            // Remove one item from that stack and re-add the rest to drops.
            replacedDrop.setAmount(replacedDrop.getAmount() - 1);
            drops.add(replacedDrop);
        }

        // Add the replaced head to the drops.
        drops.add(replacementHead.clone());
        return true;
    } // replaceVanillaHeadDrop

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
     * @param entity       the dropping entity (mob).
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
     * Check that the specified entity type, as an upper-cased string, can be
     * configured to drop heads.
     *
     * @param sender the command sender, sent error messages.
     * @param type   the EntityType as a String; must be upper-cased by the
     *               caller.
     * @return true if the specified EntityType can drop heads, otherwise false.
     */
    protected boolean validateMobType(CommandSender sender, String type) {
        EntityType entityType = null;
        try {
            entityType = EntityType.valueOf(type);
            if (entityType.isAlive() && entityType != EntityType.ARMOR_STAND) {
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + entityType.toString() + " can't drop heads!");
            }
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + type + " is not a valid mob type!");
        }
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Returns true if the specified entity can drop its head when killed by a
     * charged creeper in vanilla Minecraft.
     *
     * @returns true if the specified entity can drop its head when killed by a
     *          charged creeper in vanilla Minecraft.
     */
    protected boolean canDropVanillaHead(Entity entity) {
        return VANILLA_HEAD_MATERIALS.containsKey(entity.getType());
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
     * Return true if the specified entity is eligible for custom drops.
     *
     * @param entity the entity.
     * @return true if the specified entity is eligible for custom drops.
     */
    protected boolean canBeDecapitated(Entity entity) {
        if (entity instanceof LivingEntity &&
            entity.getType() != EntityType.ARMOR_STAND) {
            String spawnReason = (String) EntityMeta.api().get(entity, this, "spawn-reason");
            return spawnReason == null || !NO_DECAPITATION_SPAWN_REASONS.contains(spawnReason);
        }
        return false;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the ChargedCreeperExplosion metadata instance associated with a
     * charged creeper, creating and attaching it as necessary.
     *
     * @param creeper the charged creeper.
     * @param create  if true, create and attach the metadata if necessary;
     *                otherwise, return null if not present.
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
         * explosion in the event of a non-vanilla decapitation. Unused for
         * vanilla decapitations.
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
     * Set of SpawnReasons as Strings that deny a decapitation head drop.
     */
    protected static final HashSet<String> NO_DECAPITATION_SPAWN_REASONS = new HashSet<>();
    static {
        NO_DECAPITATION_SPAWN_REASONS.add(SpawnReason.SPAWNER.name());
        NO_DECAPITATION_SPAWN_REASONS.add(SpawnReason.DROWNED.name());
        NO_DECAPITATION_SPAWN_REASONS.add(SpawnReason.SLIME_SPLIT.name());
    }

    /**
     * Valid head materials that can be used in the /gonebatty head command.
     */
    protected static HashSet<Material> HEAD_MATERIALS = new HashSet<>();
    static {
        HEAD_MATERIALS.add(Material.PLAYER_HEAD);
        HEAD_MATERIALS.add(Material.CREEPER_HEAD);
        HEAD_MATERIALS.add(Material.DRAGON_HEAD);
        HEAD_MATERIALS.add(Material.ZOMBIE_HEAD);
        HEAD_MATERIALS.add(Material.SKELETON_SKULL);
        HEAD_MATERIALS.add(Material.WITHER_SKELETON_SKULL);
        HEAD_MATERIALS.add(Material.PIGLIN_HEAD);
    }

    /**
     * Map from mob EntityType to the Material of the head it drops in a charged
     * creeper explosion due to vanilla Minecraft code only.
     */
    protected static HashMap<EntityType, Material> VANILLA_HEAD_MATERIALS = new HashMap<>();
    static {
        VANILLA_HEAD_MATERIALS.put(EntityType.CREEPER, Material.CREEPER_HEAD);
        VANILLA_HEAD_MATERIALS.put(EntityType.SKELETON, Material.SKELETON_SKULL);
        VANILLA_HEAD_MATERIALS.put(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL);
        VANILLA_HEAD_MATERIALS.put(EntityType.ZOMBIE, Material.ZOMBIE_HEAD);
        VANILLA_HEAD_MATERIALS.put(EntityType.PIGLIN, Material.PIGLIN_HEAD);
    }

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
