/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (C) 2025 thangks
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.vtalters;

import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AltarManager {

    private final VtAlters plugin;
    private final LanguageManager lang;
    private final Map<Location, Altar> altarMap = new HashMap<>();
    private final Map<Location, Item> placedItemsDisplay = new HashMap<>();
    private final Map<Location, UUID> itemPlacers = new HashMap<>();
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final Map<Location, BukkitTask> peripheralParticleTasks = new HashMap<>();
    private static final double ARMOR_STAND_HEAD_OFFSET = 0.75;

    private final Set<Location> allAltarBlocks = new HashSet<>();
    private final Set<Altar> summoningAltars = new HashSet<>();

    public AltarManager(VtAlters plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        loadAltars();
        startReadyAltarEffectTask();
    }

    @SuppressWarnings("unchecked")
    public void loadAltars() {
        altarMap.clear();
        allAltarBlocks.clear();

        ConfigurationSection altarsSection = plugin.getDataManager().getConfig().getConfigurationSection("altars");
        if (altarsSection == null) {
            return;
        }

        for (String altarName : altarsSection.getKeys(false)) {
            ConfigurationSection currentAltarSection = altarsSection.getConfigurationSection(altarName);
            if (currentAltarSection == null) continue;

            Altar altar = new Altar(altarName);
            Location centerLocation = Altar.locationFromString(currentAltarSection.getString("center"));
            if (centerLocation == null && !Objects.equals(currentAltarSection.getString("center", "not_set"), "not_set")) {
                plugin.getErrorHandler().logError("Invalid center location for altar '" + altarName + "' in altars.yml. It might be a typo or a world that no longer exists.", "Altar Data Error");
            }
            altar.setCenterLocation(centerLocation);
            if (centerLocation != null) {
                allAltarBlocks.add(centerLocation.getBlock().getLocation());
            }

            altar.setBossName(currentAltarSection.getString("boss-name", "DefaultBoss"));

            if (currentAltarSection.isConfigurationSection("central-item")) {
                try {
                    altar.setCentralItem(ItemStack.deserialize(currentAltarSection.getConfigurationSection("central-item").getValues(true)));
                } catch (Exception e) {
                    plugin.getErrorHandler().logError("Failed to deserialize central-item for altar '" + altarName + "'. Check altars.yml. Error: " + e.getMessage(), "Altar Data Error");
                }
            }

            Map<ItemStack, Integer> requiredItems = new HashMap<>();
            List<Map<?, ?>> itemsList = currentAltarSection.getMapList("required-items");
            for (Map<?, ?> itemMap : itemsList) {
                if (itemMap.containsKey("item") && itemMap.containsKey("amount")) {
                    try {
                        ItemStack item = ItemStack.deserialize((Map<String, Object>) itemMap.get("item"));
                        int amount = (Integer) itemMap.get("amount");

                        Optional<ItemStack> existingKey = requiredItems.keySet().stream().filter(k -> k.isSimilar(item)).findFirst();
                        if (existingKey.isPresent()) {
                            requiredItems.put(existingKey.get(), requiredItems.get(existingKey.get()) + amount);
                        } else {
                            requiredItems.put(item, amount);
                        }
                    } catch (Exception e) {
                        plugin.getErrorHandler().logError("Failed to deserialize a required-item for altar '" + altarName + "'. Check altars.yml. Error: " + e.getMessage(), "Altar Data Error");
                    }
                }
            }
            altar.setRequiredItems(requiredItems);

            List<Location> pedestalLocations = new ArrayList<>();
            for (String locString : currentAltarSection.getStringList("pedestal-locations")) {
                Location loc = Altar.locationFromString(locString);
                if (loc != null) {
                    pedestalLocations.add(loc);
                    allAltarBlocks.add(loc.getBlock().getLocation());
                } else {
                    plugin.getErrorHandler().logError("Invalid pedestal location '" + locString + "' for altar '" + altarName + "' in altars.yml.", "Altar Data Error");
                }
            }
            altar.setPedestalLocations(pedestalLocations);

            if (altar.getCenterLocation() != null) {
                altarMap.put(altar.getCenterLocation().getBlock().getLocation(), altar);
            }
        }
    }

    private void placeItem(Player player, Location loc, ItemStack item) {
        Location blockLocation = loc.getBlock().getLocation();
        double pedestalHeight = plugin.getConfig().getDouble("effects.heights.pedestal", 1.2);
        Location displayLoc = blockLocation.clone().add(0.5, pedestalHeight - 0.2, 0.5);
        ItemStack singleItem = item.clone();
        singleItem.setAmount(1);

        if (placedItemsDisplay.containsKey(blockLocation)) {
            retrieveItem(player, blockLocation);
        }

        Item droppedItem = blockLocation.getWorld().dropItem(displayLoc, singleItem);
        droppedItem.setPickupDelay(Integer.MAX_VALUE);
        droppedItem.setGravity(false);
        droppedItem.setVelocity(new Vector(0, 0, 0));
        placedItemsDisplay.put(blockLocation, droppedItem);
        itemPlacers.put(blockLocation, player.getUniqueId());

        Altar altar = getAltarAt(loc);
        if (altar != null) {
            boolean isRequired = altar.getRequiredItems().keySet().stream()
                                      .anyMatch(requiredItem -> requiredItem.isSimilar(item));
            if (isRequired) {
                startPeripheralParticle(loc);
            }
        }

        player.getInventory().getItemInMainHand().setAmount(item.getAmount() - 1);
        loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f);
    }

    private void retrieveItem(Player player, Location loc) {
        Location blockLocation = loc.getBlock().getLocation();
        Item droppedItem = placedItemsDisplay.get(blockLocation);
        if (droppedItem == null) return;
        
        boolean preventTheft = plugin.getConfig().getBoolean("altar.prevent-item-theft", true);
        if (preventTheft && itemPlacers.containsKey(blockLocation)) {
            UUID placerUUID = itemPlacers.get(blockLocation);
            if (!player.getUniqueId().equals(placerUUID)) {
                lang.sendMessage(player, "altar-interaction.not-your-item");
                return;
            }
        }

        ItemStack item = droppedItem.getItemStack();
        if (player != null && item != null) {
            player.getInventory().addItem(item);
        }
        
        droppedItem.remove();
        placedItemsDisplay.remove(blockLocation);
        itemPlacers.remove(blockLocation);
        stopPeripheralParticle(blockLocation);
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
    }

    private void startPeripheralParticle(Location loc) {
        Location blockLocation = loc.getBlock().getLocation();
        stopPeripheralParticle(blockLocation);
        
        double pedestalHeight = plugin.getConfig().getDouble("effects.heights.pedestal", 1.2);
        
        Particle particle = getParticle("effects.particles.pedestal-ready", "END_ROD");
        if (particle == null) return;
        
        BukkitTask task = new BukkitRunnable() {
            private double angle = 0;
            private final double radius = 0.8;

            @Override
            public void run() {
                angle += Math.PI / 16;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                Location particleLoc = blockLocation.clone().add(0.5 + x, pedestalHeight, 0.5 + z);
                loc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        peripheralParticleTasks.put(blockLocation, task);
    }

    private void stopPeripheralParticle(Location loc) {
        Location blockLocation = loc.getBlock().getLocation();
        if (peripheralParticleTasks.containsKey(blockLocation)) {
            peripheralParticleTasks.get(blockLocation).cancel();
            peripheralParticleTasks.remove(blockLocation);
        }
    }

    private void startReadyAltarEffectTask() {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Particle centralParticle = getParticle("effects.particles.altar-ready", "SOUL_FIRE_FLAME");
                if (centralParticle == null) {
                    this.cancel();
                    return;
                }
                double readyHeight = plugin.getConfig().getDouble("effects.heights.ready-particle", 1.2);
                for (Altar altar : altarMap.values()) {
                    if (isAltarReady(altar) && !summoningAltars.contains(altar)) {
                        Location center = altar.getCenterLocation();
                        if (center != null) {
                            center.getWorld().spawnParticle(centralParticle, center.clone().add(0.5, readyHeight, 0.5), 5, 0.3, 0.3, 0.3, 0.01);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        activeTasks.add(task);
    }

    private void spawnSummoningRings(Location center) {
        Particle ringParticle = getParticle("effects.particles.ritual-ring", "SOUL_FIRE_FLAME");
        if (ringParticle == null) return;
        
        Location itemCenter = center.clone().add(0.5, 1.0, 0.5);
        double ringYOffset = plugin.getConfig().getDouble("effects.heights.ritual-ring-offset", 0.0);
        
        double radius = 0.8;
        int durationTicks = 40;

        final Vector x_axis = new Vector(1, 0, 0);
        final Vector y_axis = new Vector(0, 1, 0);
        final Vector z_axis = new Vector(0, 0, 1);

        final double system_rotation_angle = Math.PI / 4.0;
        final double cos_sys = Math.cos(system_rotation_angle);
        final double sin_sys = Math.sin(system_rotation_angle);

        BukkitTask animationTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks > durationTicks) {
                    this.cancel();
                    return;
                }

                Location particleCenter = itemCenter.clone().add(0, ringYOffset, 0);
                double particle_flow_angle = (double) ticks / 20.0 * 2 * Math.PI; 

                if (ticks % 25 == 0) {
                    playSoundFromConfig(particleCenter, "effects.sounds.ritual-ambient-loop");
                }

                for (double t = 0; t < 2 * Math.PI; t += Math.PI / 16) {
                    double cos_t = Math.cos(t + particle_flow_angle);
                    double sin_t = Math.sin(t + particle_flow_angle);

                    Vector p1 = x_axis.clone().multiply(radius * cos_t).add(y_axis.clone().multiply(radius * sin_t));
                    Vector p2 = z_axis.clone().multiply(radius * cos_t).add(y_axis.clone().multiply(radius * sin_t));

                    double p1x_rot = p1.getX() * cos_sys - p1.getZ() * sin_sys;
                    double p1z_rot = p1.getX() * sin_sys + p1.getZ() * cos_sys;
                    p1.setX(p1x_rot);
                    p1.setZ(p1z_rot);

                    double p2x_rot = p2.getX() * cos_sys - p2.getZ() * sin_sys;
                    double p2z_rot = p2.getX() * sin_sys + p2.getZ() * cos_sys;
                    p2.setX(p2x_rot);
                    p2.setZ(p2z_rot);

                    particleCenter.getWorld().spawnParticle(ringParticle, particleCenter.clone().add(p1), 1, 0, 0, 0, 0);
                    particleCenter.getWorld().spawnParticle(ringParticle, particleCenter.clone().add(p2), 1, 0, 0, 0, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        activeTasks.add(animationTask);
    }

    private ArmorStand createVisualArmorStand(Location loc, ItemStack stack) {
        return loc.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            armorStand.setVisible(false);
            armorStand.setSmall(true);
            if (armorStand.getEquipment() != null) {
                armorStand.getEquipment().setHelmet(stack.clone());
            }
        });
    }

    private void startSummoningAnimation(Altar altar, Player player, ItemStack centralItem) {
        Location center = altar.getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        summoningAltars.add(altar);
        lang.sendMessage(player, "altar-interaction.ritual-start");
        playSoundFromConfig(center, "effects.sounds.ritual-start");
        
        spawnSummoningRings(center);
        
        Location finalConvergencePoint = center.clone().add(0.5, 5, 0.5);
        Location orbitPoint = center.clone().add(0.5, 4, 0.5);
        double orbitRadius = 2.0;

        List<Item> ceremonyItems = new ArrayList<>(placedItemsDisplay.values());
        placedItemsDisplay.clear();
        itemPlacers.clear();

        altar.getPedestalLocations().stream()
            .filter(Objects::nonNull)
            .forEach(this::stopPeripheralParticle);
        
        Item centerDisplayItem = center.getWorld().dropItem(center.clone().add(0.5, 1.0, 0.5), centralItem);
        centerDisplayItem.setPickupDelay(Integer.MAX_VALUE);
        centerDisplayItem.setGravity(false);
        centerDisplayItem.setVelocity(new Vector(0, 0, 0));
        ceremonyItems.add(centerDisplayItem);

        new BukkitRunnable() {
            private final long PRE_ANIMATION_DELAY = 40L;
            private final long SPIRAL_TO_ORBIT_DURATION = 14L;
            private final long ORBIT_HOLD_DURATION = 60L;
            private final long CONVERGE_DURATION = 5L;

            private long ticks = 0;
            private boolean hasFinished = false;
            private Map<ArmorStand, Location> flyingEntities = null;
            private Map<ArmorStand, Location> orbitLocations = null;

            @Override
            public void run() {
                if (hasFinished) {
                    this.cancel();
                    return;
                }

                if (ticks < PRE_ANIMATION_DELAY) {
                    if (ticks == 0) center.getWorld().playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.0f);
                    ticks++;
                    return;
                }

                if (flyingEntities == null) {
                    playSoundFromConfig(center, "effects.sounds.ritual-items-fly");
                    flyingEntities = new HashMap<>();
                    orbitLocations = new HashMap<>();
                    int i = 0;
                    double angleIncrement = 360.0 / ceremonyItems.size();

                    for (Item item : ceremonyItems) {
                        if (item.isValid()) {
                            Location itemVisualLocation = item.getLocation().clone();
                            ItemStack stack = item.getItemStack().clone();
                            Location armorStandSpawnLocation = itemVisualLocation.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0);
                            
                            ArmorStand visualArmorStand = createVisualArmorStand(armorStandSpawnLocation, stack);
                            flyingEntities.put(visualArmorStand, itemVisualLocation);

                            double angle = Math.toRadians(i * angleIncrement);
                            double x = orbitPoint.getX() + orbitRadius * Math.cos(angle);
                            double z = orbitPoint.getZ() + orbitRadius * Math.sin(angle);
                            orbitLocations.put(visualArmorStand, new Location(center.getWorld(), x, orbitPoint.getY(), z));
                            
                            item.remove();
                            i++;
                        }
                    }
                }

                long currentStageTicks = ticks - PRE_ANIMATION_DELAY;
                if (currentStageTicks < SPIRAL_TO_ORBIT_DURATION) {
                    double progress = (double) currentStageTicks / SPIRAL_TO_ORBIT_DURATION;
                    for (Map.Entry<ArmorStand, Location> entry : flyingEntities.entrySet()) {
                        ArmorStand as = entry.getKey();
                        Location startLoc = entry.getValue();
                        Location orbitLoc = orbitLocations.get(as);
                        if (!as.isValid() || orbitLoc == null) continue;
                        Vector direction = orbitLoc.toVector().subtract(startLoc.toVector());
                        Location newVisualPos = startLoc.clone().add(direction.multiply(progress));
                        as.teleport(newVisualPos.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, newVisualPos);
                    }
                }
                else if (currentStageTicks < SPIRAL_TO_ORBIT_DURATION + ORBIT_HOLD_DURATION) {
                    double progress = (double) (currentStageTicks - SPIRAL_TO_ORBIT_DURATION) / ORBIT_HOLD_DURATION;
                    double angleOffset = progress * 540.0;
                    int i = 0;
                    double angleIncrement = 360.0 / flyingEntities.size();
                    for (ArmorStand as : flyingEntities.keySet()) {
                        if (!as.isValid()) continue;
                         double angle = Math.toRadians(i * angleIncrement + angleOffset);
                         double x = orbitPoint.getX() + orbitRadius * Math.cos(angle);
                         double z = orbitPoint.getZ() + orbitRadius * Math.sin(angle);
                        Location newVisualPos = new Location(center.getWorld(), x, orbitPoint.getY(), z);
                        as.teleport(newVisualPos.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, newVisualPos);
                        i++;
                    }
                }
                else if (currentStageTicks < SPIRAL_TO_ORBIT_DURATION + ORBIT_HOLD_DURATION + CONVERGE_DURATION) {
                    double progress = (double) (currentStageTicks - (SPIRAL_TO_ORBIT_DURATION + ORBIT_HOLD_DURATION)) / CONVERGE_DURATION;
                    for (Map.Entry<ArmorStand, Location> entry : flyingEntities.entrySet()) {
                        ArmorStand as = entry.getKey();
                        Location orbitLoc = orbitLocations.get(as);
                        if (!as.isValid() || orbitLoc == null) continue;
                        Vector direction = finalConvergencePoint.toVector().subtract(orbitLoc.toVector());
                        Location newVisualPos = orbitLoc.clone().add(direction.multiply(progress));
                        as.teleport(newVisualPos.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        spawnTrailParticles(as, newVisualPos);
                    }
                }
                else {
                    if (hasFinished) return;
                    hasFinished = true;

                    for (ArmorStand as : flyingEntities.keySet()) {
                        if (as.isValid()) {
                            as.teleport(finalConvergencePoint.clone().subtract(0, ARMOR_STAND_HEAD_OFFSET, 0));
                        }
                    }
                    spawnConvergenceBurst(finalConvergencePoint);
                    summonBoss(altar, player);
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            flyingEntities.keySet().forEach(as -> { if (as.isValid()) as.remove(); });
                            summoningAltars.remove(altar);
                        }
                    }.runTaskLater(plugin, 1L);
                    
                    this.cancel();
                    return;
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void spawnTrailParticles(ArmorStand as, Location visualLocation) {
        Particle trail1 = getParticle("effects.particles.animation-trail", "ENCHANTMENT_TABLE");
        Particle trail2 = getParticle("effects.particles.animation-trail-secondary", "END_ROD");
        if (trail1 != null) {
            as.getWorld().spawnParticle(trail1, visualLocation.clone().add(0, 0.2, 0), 1, 0.5, 0.5, 0.5, 0);
        }
        if (trail2 != null && as.getTicksLived() % 3 == 0) {
            as.getWorld().spawnParticle(trail2, visualLocation.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0);
        }
    }

    private void spawnConvergenceBurst(Location loc) {
        playSoundFromConfig(loc, "effects.sounds.ritual-converge");
        Particle burst = getParticle("effects.particles.convergence-burst", "END_ROD");
        if (burst == null) return;
        for (int i = 0; i < 150; i++) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vector v = new Vector(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).normalize().multiply(0.4);
            loc.getWorld().spawnParticle(burst, loc, 0, v.getX(), v.getY(), v.getZ(), 1.0);
        }
    }

    private void summonBoss(Altar altar, Player player) {
        Location center = altar.getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        lang.sendMessage(player, "altar-interaction.boss-spawned");
        Location spawnLoc = center.clone().add(0.5, 3, 0.5);
        String bossName = altar.getBossName();

        if (bossName.equalsIgnoreCase("DefaultBoss")) {
            spawnLoc.getWorld().spawn(spawnLoc, Zombie.class);
            plugin.getLogger().info("Altar '" + altar.getName() + "' summoned a default Zombie.");
        } else {
            try {
                MythicBukkit.inst().getAPIHelper().spawnMythicMob(bossName, spawnLoc, 1);
            } catch (InvalidMobTypeException e) {
                plugin.getErrorHandler().logError("Invalid MythicMob name '" + bossName + "' for altar '" + altar.getName() + "'. Please check your MythicMobs files and the altar configuration.", "Altar Data Error");
                lang.sendMessage(player, "altar-interaction.error-invalid-boss", "%boss%", bossName);
                summoningAltars.remove(altar);
                return;
            }
        }

        playSoundFromConfig(center, "effects.sounds.summon-spawn");
        
        if (plugin.getConfig().getBoolean("altar.broadcast-summon.enabled", true)) {
            String broadcastMessage = lang.getMessage("altar-interaction.boss-summon-broadcast");
            String displayBossName = bossName.equalsIgnoreCase("DefaultBoss") ? "Zombie" : bossName;
            
            broadcastMessage = broadcastMessage.replace("%boss%", displayBossName)
                                               .replace("%player%", player.getName());
            
            Bukkit.broadcastMessage(broadcastMessage);
        }
    }
    
    public void handleBlockClick(Player player, Block clickedBlock) {
        if (clickedBlock == null) return;
        Location blockLocation = clickedBlock.getLocation();

        Altar altar = getAltarAt(blockLocation);
        if (altar == null) return;

        if (summoningAltars.contains(altar)) {
            lang.sendMessage(player, "altar-interaction.summoning");
            return;
        }

        if (placedItemsDisplay.containsKey(blockLocation)) {
            retrieveItem(player, blockLocation);
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) return;

        if (isSameBlock(blockLocation, altar.getCenterLocation())) {
            ItemStack centralItemTemplate = altar.getCentralItem();
            if (centralItemTemplate != null && itemInHand.isSimilar(centralItemTemplate)) {
                if (isAltarReady(altar)) {
                    ItemStack centralItemToConsume = itemInHand.clone();
                    centralItemToConsume.setAmount(1);
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                    startSummoningAnimation(altar, player, centralItemToConsume);
                } else {
                    lang.sendMessage(player, "altar-interaction.not-ready");
                }
            } else {
                 lang.sendMessage(player, "altar-interaction.wrong-item");
            }
        } else if (altar.getPedestalLocations().contains(blockLocation)) {
            placeItem(player, blockLocation, itemInHand);
        }
    }

    private boolean isAltarReady(Altar altar) {
        Map<ItemStack, Integer> required = altar.getRequiredItems();
        if (required.isEmpty()) return true;

        List<ItemStack> placedItems = placedItemsDisplay.values().stream()
                .map(Item::getItemStack)
                .collect(Collectors.toList());

        for (Map.Entry<ItemStack, Integer> requirement : required.entrySet()) {
            ItemStack requiredItem = requirement.getKey();
            int requiredAmount = requirement.getValue();
            
            long placedAmount = placedItems.stream().filter(p -> p.isSimilar(requiredItem)).count();
            
            if (placedAmount < requiredAmount) {
                return false;
            }
        }
        return true;
    }

    public Altar getAltarAt(Location loc) {
        Location blockLoc = loc.getBlock().getLocation();
        for (Altar altar : altarMap.values()) {
            if (isSameBlock(blockLoc, altar.getCenterLocation())) return altar;
            if (altar.getPedestalLocations().contains(blockLoc)) return altar;
        }
        return null;
    }

    public boolean isAltarBlock(Location loc) {
        return allAltarBlocks.contains(loc.getBlock().getLocation());
    }
    
    private boolean isSameBlock(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return false;
        if (!Objects.equals(loc1.getWorld(), loc2.getWorld())) return false;
        return loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }

    private void playSoundFromConfig(Location loc, String configPath) {
        String soundString = plugin.getConfig().getString(configPath);
        if (soundString == null || soundString.isEmpty()) {
            return;
        }
        String[] parts = soundString.split(",");
        try {
            Sound sound = Sound.valueOf(parts[0].trim().toUpperCase());
            float volume = (parts.length > 1) ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = (parts.length > 2) ? Float.parseFloat(parts[2]) : 1.0f;
            loc.getWorld().playSound(loc, sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getErrorHandler().logError("Invalid sound name in config.yml at path '" + configPath + "': " + soundString, "Configuration Error");
        }
    }
    
    private Particle getParticle(String path, String defaultValue) {
        String particleName = plugin.getConfig().getString(path, defaultValue);
        if (particleName == null || particleName.isEmpty() || particleName.equalsIgnoreCase("none")) {
            return null;
        }
        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getErrorHandler().logError("Invalid particle name in config.yml at path '" + path + "': " + particleName, "Configuration Error");
            return null;
        }
    }

    public void shutdown() {
        activeTasks.forEach(BukkitTask::cancel);
        activeTasks.clear();
        peripheralParticleTasks.values().forEach(BukkitTask::cancel);
        peripheralParticleTasks.clear();
        new ArrayList<>(placedItemsDisplay.values()).forEach(Entity::remove);
        placedItemsDisplay.clear();
        summoningAltars.clear();
        itemPlacers.clear();
    }
}
