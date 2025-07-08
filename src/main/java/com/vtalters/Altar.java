/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Altar {

    private final String name;
    private String bossName;
    private ItemStack centralItem;
    private Map<ItemStack, Integer> requiredItems;
    private Location centerLocation;
    private List<Location> pedestalLocations;

    public Altar(String name) {
        this.name = name;
        this.requiredItems = new HashMap<>();
        this.pedestalLocations = new ArrayList<>();
    }

    public String getName() { return name; }
    public String getBossName() { return bossName; }
    public ItemStack getCentralItem() { return centralItem; }
    public Map<ItemStack, Integer> getRequiredItems() { return requiredItems; }
    public Location getCenterLocation() { return centerLocation; }
    public List<Location> getPedestalLocations() { return pedestalLocations; }

    public void setBossName(String bossName) { this.bossName = bossName; }
    public void setCentralItem(ItemStack centralItem) { this.centralItem = centralItem; }
    public void setRequiredItems(Map<ItemStack, Integer> requiredItems) { this.requiredItems = requiredItems; }
    public void setCenterLocation(Location centerLocation) { this.centerLocation = centerLocation; }
    public void setPedestalLocations(List<Location> pedestalLocations) { this.pedestalLocations = pedestalLocations; }


    public static String locationToString(Location loc) {
        if (loc == null) return null;
        return String.format("%s,%d,%d,%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static Location locationFromString(String locString) {
        if (locString == null || locString.isEmpty() || locString.equalsIgnoreCase("not_set")) return null;
        String[] parts = locString.split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}