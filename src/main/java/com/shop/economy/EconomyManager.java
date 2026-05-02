package com.shop.economy;

import com.shop.ShopPlugin;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyManager {

    private final ShopPlugin plugin;

    public EconomyManager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void initPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getDatabaseManager().playerExists(uuid)) {
            double starting = plugin.getConfig().getDouble("starting-balance", 1000.0);
            plugin.getDatabaseManager().setBalance(uuid, player.getName(), starting);
        }
    }

    public double getBalance(Player player) {
        return plugin.getDatabaseManager().getBalance(player.getUniqueId());
    }

    public void setBalance(Player player, double amount) {
        plugin.getDatabaseManager().setBalance(player.getUniqueId(), player.getName(), Math.max(0, amount));
    }

    public boolean has(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    public boolean withdraw(Player player, double amount) {
        double balance = getBalance(player);
        if (balance < amount) return false;
        setBalance(player, balance - amount);
        return true;
    }

    public void deposit(Player player, double amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public String format(double amount) {
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        return String.format("%s%.2f", symbol, amount);
    }
}
