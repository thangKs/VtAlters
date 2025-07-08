package com.vtalters;

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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WandManager {

    private final VtAlters plugin;
    private final ItemStack wand;
    private final Map<UUID, Location> playerSelections = new HashMap<>();

    public WandManager(VtAlters plugin) {
        this.plugin = plugin;
        this.wand = createWand();
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Altar Setup Wand");
            meta.setLore(Arrays.asList(
                    ChatColor.YELLOW + "Left/Right click a block",
                    ChatColor.YELLOW + "to select a location for the altar."
            ));
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveWand(Player player) {
        player.getInventory().addItem(this.wand.clone());
        plugin.getLanguageManager().sendMessage(player, "wand.given");
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        ItemMeta wandMeta = this.wand.getItemMeta();
        return meta != null && wandMeta != null && meta.hasDisplayName() && meta.getDisplayName().equals(wandMeta.getDisplayName());
    }

    public void setSelection(Player player, Location location) {
        Location blockLoc = location.getBlock().getLocation();
        Location oldSelection = playerSelections.get(player.getUniqueId());

        if (oldSelection == null || !oldSelection.equals(blockLoc)) {
            playerSelections.put(player.getUniqueId(), blockLoc);
            plugin.getLanguageManager().sendMessage(player, "wand.selection",
                "%x%", String.valueOf(blockLoc.getBlockX()),
                "%y%", String.valueOf(blockLoc.getBlockY()),
                "%z%", String.valueOf(blockLoc.getBlockZ()));
        }
    }

    public Location getSelection(Player player) {
        return playerSelections.get(player.getUniqueId());
    }
}
