/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class VtAlters extends JavaPlugin {

    private AltarManager altarManager;
    private WandManager wandManager;
    private DataManager dataManager;
    private LanguageManager languageManager;
    private ErrorHandler errorHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        this.errorHandler = new ErrorHandler(this);
        this.dataManager = new DataManager(this);
        this.languageManager = new LanguageManager(this);
        this.wandManager = new WandManager(this);
        this.altarManager = new AltarManager(this);
        
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);
        
        AltarCommand altarCommand = new AltarCommand(this);
        PluginCommand command = getCommand("vtalters");
        if (command != null) {
            command.setExecutor(altarCommand);
            command.setTabCompleter(altarCommand);
        }

        getLogger().info("VtAlters has been enabled!");
    }

    @Override
    public void onDisable() {
        if (altarManager != null) {
            altarManager.shutdown();
        }
        getLogger().info("VtAlters has been disabled.");
    }
    
    public void reloadPlugin() {
        if (altarManager != null) {
            altarManager.shutdown();
        }
        HandlerList.unregisterAll(this);
        
        reloadConfig();
        
        errorHandler.clearErrors();
        dataManager.reloadConfig();
        
        this.languageManager = new LanguageManager(this);
        this.wandManager = new WandManager(this);
        this.altarManager = new AltarManager(this);
        
        getServer().getPluginManager().registerEvents(new AltarListener(this), this);

        AltarCommand altarCommand = new AltarCommand(this);
        PluginCommand command = getCommand("vtalters");
        if (command != null) {
            command.setExecutor(altarCommand);
            command.setTabCompleter(altarCommand);
        }
        
        getLogger().info("VtAlters reloaded!");
    }
    
    public AltarManager getAltarManager() { return this.altarManager; }
    public WandManager getWandManager() { return this.wandManager; }
    public DataManager getDataManager() { return this.dataManager; }
    public LanguageManager getLanguageManager() { return this.languageManager; }
    public ErrorHandler getErrorHandler() { return this.errorHandler; }
}
