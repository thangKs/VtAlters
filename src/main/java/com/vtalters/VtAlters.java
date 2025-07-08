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
