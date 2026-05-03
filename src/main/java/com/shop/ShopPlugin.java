package com.shop;

import com.shop.commands.BalanceCommand;
import com.shop.commands.ShopCommand;
import com.shop.database.DatabaseManager;
import com.shop.economy.EconomyManager;
import com.shop.listeners.ShopListener;
import com.shop.managers.ShopManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopPlugin extends JavaPlugin {

    private static ShopPlugin instance;

    private DatabaseManager databaseManager;
    private EconomyManager  economyManager;
    private ShopManager     shopManager;
    private ShopListener    shopListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        economyManager = new EconomyManager(this);
        shopManager    = new ShopManager(this);

        shopListener = new ShopListener(this);
        getServer().getPluginManager().registerEvents(shopListener, this);

        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        BalanceCommand balanceCommand = new BalanceCommand(this);
        getCommand("balance").setExecutor(balanceCommand);
        getCommand("balance").setTabCompleter(balanceCommand);

        getLogger().info("MinecraftShop v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("MinecraftShop disabled.");
    }

    public static ShopPlugin getInstance() { return instance; }

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager  getEconomyManager()  { return economyManager; }
    public ShopManager     getShopManager()     { return shopManager; }
    public ShopListener    getShopListener()    { return shopListener; }
}
