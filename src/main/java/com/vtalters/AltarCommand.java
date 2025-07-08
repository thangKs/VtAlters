/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

public class AltarCommand implements CommandExecutor, TabCompleter {

    private final VtAlters plugin;
    private final WandManager wandManager;
    private final LanguageManager lang;

    public AltarCommand(VtAlters plugin) {
        this.plugin = plugin;
        this.wandManager = plugin.getWandManager();
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            lang.sendRawMessage(sender, "general.not-a-player");
            return true;
        }
        Player player = (Player) sender;

        if (player.hasPermission("vtalters.admin") && plugin.getErrorHandler().hasErrors()) {
            lang.sendMessage(player, "general.plugin-has-errors", "%reasons%", plugin.getErrorHandler().getErrorReasons());
        }

        if (args.length == 0) {
            sendHelpMessage(player, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player, args, label);
                break;
            case "delete":
                handleDelete(player, args, label);
                break;
            case "list":
                handleList(player);
                break;
            case "reload":
                if (!player.hasPermission("vtalters.command.reload")) {
                    lang.sendMessage(player, "general.no-permission");
                    return true;
                }
                plugin.reloadPlugin();
                lang.sendMessage(player, "general.reloaded");
                break;
            case "wand":
                if (!player.hasPermission("vtalters.command.wand")) {
                    lang.sendMessage(player, "general.no-permission");
                    return true;
                }
                wandManager.giveWand(player);
                break;
            case "edit":
                handleEdit(player, args, label);
                break;
            default:
                sendHelpMessage(player, label);
                break;
        }
        return true;
    }

    private void handleCreate(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.create")) {
            lang.sendMessage(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " create <name>");
            return;
        }
        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-altar-exists", "%name%", altarName);
            return;
        }
        data.set("altars." + altarName + ".boss-name", "DefaultBoss");
        data.set("altars." + altarName + ".center", "not_set");
        data.set("altars." + altarName + ".central-item", null);
        data.set("altars." + altarName + ".required-items", new ArrayList<Map<String, Object>>());
        data.set("altars." + altarName + ".pedestal-locations", new ArrayList<String>());
        saveAndReload();
        
        lang.sendMessage(player, "altar-commands.created", "%name%", altarName);
    }

    private void handleDelete(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.delete")) {
            lang.sendMessage(player, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " delete <name>");
            return;
        }
        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (!data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-no-altar", "%name%", altarName);
            return;
        }
        data.set("altars." + altarName, null);
        saveAndReload();
        lang.sendMessage(player, "altar-commands.deleted", "%name%", altarName);
    }

    private void handleList(Player player) {
        if (!player.hasPermission("vtalters.command.list")) {
            lang.sendMessage(player, "general.no-permission");
            return;
        }
        FileConfiguration data = plugin.getDataManager().getConfig();
        lang.sendRawMessage(player, "altar-commands.list-header");
        if (!data.isConfigurationSection("altars") || data.getConfigurationSection("altars").getKeys(false).isEmpty()) {
            lang.sendRawMessage(player, "altar-commands.list-empty");
        } else {
            data.getConfigurationSection("altars").getKeys(false).forEach(name -> lang.sendRawMessage(player, "altar-commands.list-entry", "%name%", name));
        }
    }

    private void handleEdit(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.edit")) {
            lang.sendMessage(player, "general.no-permission");
            return;
        }
        if (args.length < 3) {
            sendHelpMessage(player, label);
            return;
        }
        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (!data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-no-altar", "%name%", altarName);
            return;
        }

        String editAction = args[2].toLowerCase();
        String pathPrefix = "altars." + altarName + ".";

        switch (editAction) {
            case "set":
                handleEditSet(player, args, data, pathPrefix, label);
                break;
            case "add":
                handleEditAdd(player, args, data, pathPrefix, altarName, label);
                break;
            case "remove":
                handleEditRemove(player, args, data, pathPrefix, altarName, label);
                break;
            default:
                sendHelpMessage(player, label);
                break;
        }
    }

    private void handleEditSet(Player player, String[] args, FileConfiguration data, String pathPrefix, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }
        String setType = args[3].toLowerCase();
        if (setType.equals("center")) {
            Location selection = wandManager.getSelection(player);
            if (selection == null) {
                lang.sendMessage(player, "wand.error-no-selection");
                return;
            }
            data.set(pathPrefix + "center", Altar.locationToString(selection.getBlock().getLocation()));
            lang.sendMessage(player, "altar-commands.center-set");
            saveAndReload();
        } else if (setType.equals("mob")) {
            if (args.length < 5) {
                lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " edit " + args[1] + " set mob <mob_name>");
                return;
            }
            String mobName = args[4];
            data.set(pathPrefix + "boss-name", mobName);
            lang.sendMessage(player, "altar-commands.boss-set", "%name%", args[1], "%mob%", mobName);
            saveAndReload();
        } else {
            sendHelpMessage(player, label);
        }
    }

    private void handleEditAdd(Player player, String[] args, FileConfiguration data, String pathPrefix, String altarName, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }
        String addType = args[3].toLowerCase();
        
        switch (addType) {
            case "itemcenter":
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand");
                    return;
                }
                
                ItemStack centralItemTemplate = itemInHand.clone();
                centralItemTemplate.setAmount(1);
                data.set(pathPrefix + "central-item", centralItemTemplate.serialize());

                String centralItemName = itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName()
                        ? itemInHand.getItemMeta().getDisplayName()
                        : itemInHand.getType().name().replace('_', ' ').toLowerCase();

                lang.sendMessage(player, "altar-commands.center-item-set", "%item_name%", centralItemName);
                saveAndReload();
                break;
                
            case "pedestal":
                Location selection = wandManager.getSelection(player);
                if (selection == null) {
                    lang.sendMessage(player, "wand.error-no-selection");
                    return;
                }
                Location centerLoc = Altar.locationFromString(data.getString(pathPrefix + "center"));
                if (centerLoc == null) {
                    lang.sendMessage(player, "altar-commands.error-must-set-center");
                    return;
                }
                double maxRadius = plugin.getConfig().getDouble("altar.max-pedestal-radius", 10.0);
                if (!selection.getWorld().equals(centerLoc.getWorld()) || selection.distance(centerLoc) > maxRadius) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-too-far", "%radius%", String.valueOf(maxRadius));
                    return;
                }
                Location blockLocation = selection.getBlock().getLocation();
                String pedestalPath = pathPrefix + "pedestal-locations";
                List<String> locations = data.getStringList(pedestalPath);
                String locString = Altar.locationToString(blockLocation);
                if (locations.contains(locString)) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-already-exists");
                    return;
                }
                locations.add(locString);
                data.set(pedestalPath, locations);
                lang.sendMessage(player, "altar-commands.pedestal-added");
                saveAndReload();
                break;
                
            case "item":
                if (args.length < 5) {
                    lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " edit " + altarName + " add item <amount>");
                    return;
                }
                ItemStack requiredItemInHand = player.getInventory().getItemInMainHand();
                if (requiredItemInHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand");
                    return;
                }
                
                String requiredItemName = requiredItemInHand.hasItemMeta() && requiredItemInHand.getItemMeta().hasDisplayName()
                        ? requiredItemInHand.getItemMeta().getDisplayName()
                        : requiredItemInHand.getType().name().replace('_', ' ').toLowerCase();
                
                try {
                    int amount = Integer.parseInt(args[4]);
                    if (amount <= 0) {
                        lang.sendMessage(player, "altar-commands.error-invalid-number");
                        return;
                    }

                    String reqPedestalPath = pathPrefix + "pedestal-locations";
                    int pedestalCount = data.getStringList(reqPedestalPath).size();

                    if (pedestalCount == 0) {
                        lang.sendMessage(player, "altar-commands.error-must-add-pedestals-first");
                        return;
                    }

                    String requiredItemPath = pathPrefix + "required-items";
                    List<Map<?, ?>> requiredItemsList = data.getMapList(requiredItemPath);
                    int currentTotalRequired = 0;
                    for (Map<?, ?> map : requiredItemsList) {
                        if (map.get("amount") instanceof Integer) {
                            currentTotalRequired += (Integer) map.get("amount");
                        }
                    }

                    if (currentTotalRequired + amount > pedestalCount) {
                        lang.sendMessage(player, "altar-commands.error-items-exceed-pedestals",
                                "%total_required%", String.valueOf(currentTotalRequired + amount),
                                "%pedestal_count%", String.valueOf(pedestalCount));
                        return;
                    }
                    
                    ItemStack requiredItemTemplate = requiredItemInHand.clone();
                    requiredItemTemplate.setAmount(1);

                    Map<String, Object> newItemEntry = new HashMap<>();
                    newItemEntry.put("item", requiredItemTemplate.serialize());
                    newItemEntry.put("amount", amount);
                    
                    requiredItemsList.add(newItemEntry);
                    data.set(requiredItemPath, requiredItemsList);

                    lang.sendMessage(player, "altar-commands.required-item-set",
                            "%amount%", String.valueOf(amount),
                            "%item_name%", requiredItemName);
                    saveAndReload();
                } catch (NumberFormatException e) {
                    lang.sendMessage(player, "altar-commands.error-invalid-number");
                }
                break;
                
            default:
                sendHelpMessage(player, label);
                break;
        }
    }


    @SuppressWarnings("unchecked")
    private void handleEditRemove(Player player, String[] args, FileConfiguration data, String pathPrefix, String altarName, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }
        String removeType = args[3].toLowerCase();

        switch(removeType) {
            case "pedestal":
                String pedestalPath = pathPrefix + "pedestal-locations";
                List<String> locations = data.getStringList(pedestalPath);

                if (args.length > 4 && args[4].equalsIgnoreCase("all")) {
                    if (locations.isEmpty()) {
                        lang.sendMessage(player, "altar-commands.error-no-pedestals-to-clear", "%name%", altarName);
                        return;
                    }
                    data.set(pedestalPath, new ArrayList<String>());
                    lang.sendMessage(player, "altar-commands.pedestals-cleared", "%name%", altarName);
                    saveAndReload();
                    return;
                }

                Location selection = wandManager.getSelection(player);
                if (selection == null) {
                    lang.sendMessage(player, "wand.error-no-selection");
                    return;
                }
                Location blockLocation = selection.getBlock().getLocation();
                String locString = Altar.locationToString(blockLocation);

                if (!locations.contains(locString)) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-not-found");
                    return;
                }
                locations.remove(locString);
                data.set(pedestalPath, locations);
                lang.sendMessage(player, "altar-commands.pedestal-removed");
                saveAndReload();
                break;

            case "item":
                String itemPath = pathPrefix + "required-items";
                List<Map<?, ?>> itemsList = data.getMapList(itemPath);

                if (args.length > 4 && args[4].equalsIgnoreCase("all")) {
                    if (itemsList.isEmpty()) {
                        lang.sendMessage(player, "altar-commands.error-no-required-items-to-remove");
                        return;
                    }
                    data.set(itemPath, new ArrayList<>());
                    lang.sendMessage(player, "altar-commands.required-items-cleared");
                    saveAndReload();
                    return;
                }

                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand");
                    return;
                }
                if (itemsList.isEmpty()) {
                    lang.sendMessage(player, "altar-commands.error-no-required-items-to-remove");
                    return;
                }
                
                int initialSize = itemsList.size();
                itemsList.removeIf(map -> {
                    Object itemObj = map.get("item");
                    if (itemObj instanceof Map) {
                        ItemStack templateItem = ItemStack.deserialize((Map<String, Object>) itemObj);
                        return templateItem.isSimilar(itemInHand);
                    }
                    return false;
                });

                if (itemsList.size() < initialSize) {
                    data.set(itemPath, itemsList);
                    lang.sendMessage(player, "altar-commands.required-item-removed");
                    saveAndReload();
                } else {
                    lang.sendMessage(player, "altar-commands.error-no-matching-item-to-remove");
                }
                break;
            
            default:
                sendHelpMessage(player, label);
                break;
        }
    }

    private void saveAndReload() {
        plugin.getDataManager().saveConfig();
        plugin.getDataManager().reloadConfig();
        plugin.getAltarManager().loadAltars();
    }

    private void sendHelpMessage(Player player, String label) {
        lang.sendRawMessage(player, "help.header");
        lang.sendRawMessage(player, "help.create", "%label%", label);
        lang.sendRawMessage(player, "help.delete", "%label%", label);
        lang.sendRawMessage(player, "help.list", "%label%", label);
        lang.sendRawMessage(player, "help.wand", "%label%", label);
        lang.sendRawMessage(player, "help.reload", "%label%", label);
        lang.sendRawMessage(player, "help.edit-header");
        lang.sendRawMessage(player, "help.edit-set-center", "%label%", label);
        lang.sendRawMessage(player, "help.edit-set-mob", "%label%", label);
        lang.sendRawMessage(player, "help.edit-add-itemcenter", "%label%", label);
        lang.sendRawMessage(player, "help.edit-add-pedestal", "%label%", label);
        lang.sendRawMessage(player, "help.edit-add-item", "%label%", label);
        lang.sendRawMessage(player, "help.edit-remove-pedestal", "%label%", label);
        lang.sendRawMessage(player, "help.edit-remove-item", "%label%", label);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("vtalters")) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "delete", "list", "wand", "reload", "edit"), new ArrayList<>());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("edit"))) {
            FileConfiguration data = plugin.getDataManager().getConfig();
            if (data.isConfigurationSection("altars")) {
                Set<String> altarNames = data.getConfigurationSection("altars").getKeys(false);
                return StringUtil.copyPartialMatches(args[1], altarNames, new ArrayList<>());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return StringUtil.copyPartialMatches(args[2], Arrays.asList("set", "add", "remove"), new ArrayList<>());
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
            return StringUtil.copyPartialMatches(args[3], Arrays.asList("center", "mob"), new ArrayList<>());
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("add")) {
            return StringUtil.copyPartialMatches(args[3], Arrays.asList("pedestal", "item", "itemcenter"), new ArrayList<>());
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("remove")) {
            return StringUtil.copyPartialMatches(args[3], Arrays.asList("pedestal", "item"), new ArrayList<>());
        }
        if (args.length == 5 && args[2].equalsIgnoreCase("remove") && 
           (args[3].equalsIgnoreCase("pedestal") || args[3].equalsIgnoreCase("item"))) {
            return StringUtil.copyPartialMatches(args[4], Collections.singletonList("all"), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
