package com.shop.listeners;

import com.shop.ShopPlugin;
import com.shop.gui.ShopGUI;
import com.shop.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopListener implements Listener {

    private final ShopPlugin plugin;
    private final Map<UUID, ShopGUI> openShops = new HashMap<>();

    public ShopListener(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void openShop(Player player) {
        if (!player.hasPermission("shop.use")) {
            player.sendMessage(Component.text("You don't have permission to use the shop!", NamedTextColor.RED));
            return;
        }
        ShopGUI gui = new ShopGUI(plugin, player, plugin.getShopManager().getItems());
        openShops.put(player.getUniqueId(), gui);
        gui.open();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getEconomyManager().initPlayer(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openShops.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ShopGUI gui = openShops.get(player.getUniqueId());
        if (gui == null || !event.getInventory().equals(gui.getInventory())) return;
        // Cancel drags into the shop GUI
        for (int slot : event.getRawSlots()) {
            if (slot < 54) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ShopGUI gui = openShops.get(player.getUniqueId());
        if (gui == null) return;

        // Only handle clicks inside the shop GUI (top inventory)
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(gui.getInventory())) return;

        event.setCancelled(true);

        int slot = event.getSlot();

        if (gui.isNextPage(slot)) {
            gui.nextPage();
            gui.refresh();
            return;
        }
        if (gui.isPrevPage(slot)) {
            gui.prevPage();
            gui.refresh();
            return;
        }
        if (gui.isNavSlot(slot)) return; // filler / balance display

        ShopItem shopItem = gui.getItemAtSlot(slot);
        if (shopItem == null) return;

        boolean shift = event.isShiftClick();
        if (event.isLeftClick()) {
            handleBuy(player, shopItem, shift ? 64 : 1, gui);
        } else if (event.isRightClick()) {
            if (shift) handleSellAll(player, shopItem, gui);
            else       handleSell(player, shopItem, 1, gui);
        }
    }

    // ── Buy / Sell logic ──────────────────────────────────────────────────────

    private void handleBuy(Player player, ShopItem shopItem, int quantity, ShopGUI gui) {
        if (!shopItem.isBuyable()) {
            msg(player, "This item is not for sale.", NamedTextColor.RED);
            return;
        }

        double total = shopItem.getBuyPrice() * quantity;
        if (!plugin.getEconomyManager().has(player, total)) {
            msg(player, "Insufficient funds! Need " + fmt(total) +
                ", you have " + fmt(plugin.getEconomyManager().getBalance(player)) + ".", NamedTextColor.RED);
            return;
        }

        Material mat = shopItem.getBukkitMaterial();
        if (getAvailableSpace(player, mat) < quantity) {
            msg(player, "Not enough inventory space!", NamedTextColor.RED);
            return;
        }

        plugin.getEconomyManager().withdraw(player, total);
        player.getInventory().addItem(new ItemStack(mat, quantity));
        plugin.getDatabaseManager().logTransaction(
            player.getUniqueId(), player.getName(), shopItem.getMaterial(), quantity, total, "BUY");

        msg(player, "Bought " + quantity + "x " + shopItem.getDisplayName() + " for " + fmt(total) + ".", NamedTextColor.GREEN);
        gui.refresh();
    }

    private void handleSell(Player player, ShopItem shopItem, int quantity, ShopGUI gui) {
        if (!shopItem.isSellable()) {
            msg(player, "This item cannot be sold.", NamedTextColor.RED);
            return;
        }

        Material mat = shopItem.getBukkitMaterial();
        int have = countItems(player, mat);
        if (have < quantity) {
            msg(player, "You only have " + have + "x " + shopItem.getDisplayName() + ".", NamedTextColor.RED);
            return;
        }

        double total = shopItem.getSellPrice() * quantity;
        removeItems(player, mat, quantity);
        plugin.getEconomyManager().deposit(player, total);
        plugin.getDatabaseManager().logTransaction(
            player.getUniqueId(), player.getName(), shopItem.getMaterial(), quantity, total, "SELL");

        msg(player, "Sold " + quantity + "x " + shopItem.getDisplayName() + " for " + fmt(total) + ".", NamedTextColor.GREEN);
        gui.refresh();
    }

    private void handleSellAll(Player player, ShopItem shopItem, ShopGUI gui) {
        if (!shopItem.isSellable()) {
            msg(player, "This item cannot be sold.", NamedTextColor.RED);
            return;
        }
        int count = countItems(player, shopItem.getBukkitMaterial());
        if (count == 0) {
            msg(player, "You don't have any " + shopItem.getDisplayName() + ".", NamedTextColor.RED);
            return;
        }
        handleSell(player, shopItem, count, gui);
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private int getAvailableSpace(Player player, Material material) {
        int space = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += material.getMaxStackSize();
            } else if (item.getType() == material && item.getAmount() < item.getMaxStackSize()) {
                space += item.getMaxStackSize() - item.getAmount();
            }
        }
        return space;
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void msg(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text("[Shop] ", NamedTextColor.GOLD)
            .append(Component.text(text, color)));
    }

    private String fmt(double amount) {
        return plugin.getEconomyManager().format(amount);
    }
}
