package com.shop.commands;

import com.shop.ShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopPlugin plugin;

    public ShopCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            plugin.getShopListener().openShop(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "balance", "bal" -> cmdBalance(player);
            case "give"           -> cmdGive(player, args);
            case "add"            -> cmdAdd(player, args);
            case "remove"         -> cmdRemove(player, args);
            case "reload"         -> cmdReload(player);
            default               -> {
                msg(player, "Unknown subcommand. Usage: /shop [balance|give|add|remove|reload]", NamedTextColor.RED);
                yield true;
            }
        };
    }

    // ── Subcommands ───────────────────────────────────────────────────────────

    private boolean cmdBalance(Player player) {
        double balance = plugin.getEconomyManager().getBalance(player);
        msg(player, "Your balance: " + plugin.getEconomyManager().format(balance), NamedTextColor.GOLD);
        return true;
    }

    private boolean cmdGive(Player player, String[] args) {
        if (!player.hasPermission("shop.admin")) { noPermission(player); return true; }
        if (args.length < 3) {
            msg(player, "Usage: /shop give <player> <amount>", NamedTextColor.RED);
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { msg(player, "Player not found.", NamedTextColor.RED); return true; }
        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) { msg(player, "Invalid amount.", NamedTextColor.RED); return true; }
        if (amount <= 0) { msg(player, "Amount must be positive.", NamedTextColor.RED); return true; }

        plugin.getEconomyManager().deposit(target, amount);
        msg(player, "Gave " + plugin.getEconomyManager().format(amount) + " to " + target.getName() + ".", NamedTextColor.GREEN);
        msg(target,  "You received " + plugin.getEconomyManager().format(amount) + " from " + player.getName() + ".", NamedTextColor.GREEN);
        return true;
    }

    private boolean cmdAdd(Player player, String[] args) {
        if (!player.hasPermission("shop.admin")) { noPermission(player); return true; }
        if (args.length < 4) {
            msg(player, "Usage: /shop add <material> <buy_price> <sell_price> [category]", NamedTextColor.RED);
            return true;
        }
        String materialName = args[1].toUpperCase();
        try { Material.valueOf(materialName); }
        catch (IllegalArgumentException e) { msg(player, "Unknown material: " + materialName, NamedTextColor.RED); return true; }

        double buy, sell;
        try {
            buy  = Double.parseDouble(args[2]);
            sell = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) { msg(player, "Invalid price.", NamedTextColor.RED); return true; }

        String category = args.length > 4 ? args[4] : "General";
        String display  = materialName.replace('_', ' ');
        display = display.substring(0, 1).toUpperCase() + display.substring(1).toLowerCase();

        plugin.getDatabaseManager().addItem(materialName, display, buy, sell, category);
        plugin.getShopManager().reload();
        msg(player, "Added " + materialName + " to the shop (buy: " +
            plugin.getEconomyManager().format(buy) + ", sell: " + plugin.getEconomyManager().format(sell) + ").", NamedTextColor.GREEN);
        return true;
    }

    private boolean cmdRemove(Player player, String[] args) {
        if (!player.hasPermission("shop.admin")) { noPermission(player); return true; }
        if (args.length < 2) {
            msg(player, "Usage: /shop remove <item_id>", NamedTextColor.RED);
            return true;
        }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException e) { msg(player, "Invalid ID.", NamedTextColor.RED); return true; }

        if (plugin.getDatabaseManager().removeItem(id)) {
            plugin.getShopManager().reload();
            msg(player, "Removed item #" + id + " from the shop.", NamedTextColor.GREEN);
        } else {
            msg(player, "No item found with ID " + id + ".", NamedTextColor.RED);
        }
        return true;
    }

    private boolean cmdReload(Player player) {
        if (!player.hasPermission("shop.admin")) { noPermission(player); return true; }
        plugin.getShopManager().reload();
        msg(player, "Shop reloaded successfully.", NamedTextColor.GREEN);
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("balance", "give", "add", "remove", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Collections.emptyList(); // too many materials to list
        }
        return Collections.emptyList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void msg(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("[Shop] ", NamedTextColor.GOLD)
            .append(Component.text(text, color)));
    }

    private void noPermission(Player player) {
        msg(player, "You don't have permission to do that.", NamedTextColor.RED);
    }
}
