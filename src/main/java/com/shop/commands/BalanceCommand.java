package com.shop.commands;

import com.shop.ShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class BalanceCommand implements CommandExecutor, TabCompleter {

    private static final String BAR = "─────────────────────────";

    private final ShopPlugin plugin;

    public BalanceCommand(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player self)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            if (!self.hasPermission("shop.use")) {
                self.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            showBalance(self, self);
        } else {
            if (!self.hasPermission("shop.admin")) {
                self.sendMessage(Component.text("You don't have permission to check other players' balance.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                self.sendMessage(Component.text("Player \"" + args[0] + "\" is not online.", NamedTextColor.RED));
                return true;
            }
            showBalance(self, target);
        }

        return true;
    }

    private void showBalance(Player viewer, Player target) {
        boolean isSelf = viewer.equals(target);
        double balance = plugin.getEconomyManager().getBalance(target);
        String formattedBalance = plugin.getEconomyManager().format(balance);

        Component bar = plain(BAR, NamedTextColor.DARK_GRAY);

        viewer.sendMessage(Component.empty());
        viewer.sendMessage(bar);
        viewer.sendMessage(
            Component.text("  Account Balance", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
        );
        viewer.sendMessage(bar);
        viewer.sendMessage(
            plain("  Player:  ", NamedTextColor.GRAY)
                .append(plain(target.getName(), NamedTextColor.WHITE))
        );
        viewer.sendMessage(
            plain("  Balance: ", NamedTextColor.GRAY)
                .append(plain(formattedBalance, NamedTextColor.GREEN))
        );
        viewer.sendMessage(bar);
        if (!isSelf) {
            viewer.sendMessage(plain("  Viewing balance of " + target.getName(), NamedTextColor.DARK_GRAY));
            viewer.sendMessage(bar);
        }
        viewer.sendMessage(Component.empty());
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text, color)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shop.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        return Collections.emptyList();
    }
}
