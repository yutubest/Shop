package com.shop.gui;

import com.shop.ShopPlugin;
import com.shop.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopGUI {

    private static final int PAGE_SLOTS = 45;
    private static final int INV_SIZE   = 54;

    // Navigation bar slot indices
    private static final int SLOT_PREV    = 45;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_NEXT    = 53;

    private final ShopPlugin plugin;
    private final Player player;
    private final List<ShopItem> items;

    private int page = 0;
    private Inventory inventory;
    private final Map<Integer, ShopItem> slotMap = new HashMap<>();

    public ShopGUI(ShopPlugin plugin, Player player, List<ShopItem> items) {
        this.plugin = plugin;
        this.player = player;
        this.items  = items;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, INV_SIZE, buildTitle());
        populate();
        player.openInventory(inventory);
    }

    /** Refresh contents without closing/reopening the inventory. */
    public void refresh() {
        if (inventory == null) return;
        inventory.clear();
        slotMap.clear();
        populate();
    }

    private Component buildTitle() {
        return Component.text("Shop", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(" — Page " + (page + 1) + "/" + maxPages(),
                NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
    }

    private void populate() {
        ItemStack filler = fillerPane();

        // Fill navigation row with glass panes
        for (int i = PAGE_SLOTS; i < INV_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Place shop items for current page
        int start = page * PAGE_SLOTS;
        int end   = Math.min(start + PAGE_SLOTS, items.size());
        for (int i = start; i < end; i++) {
            int slot = i - start;
            ShopItem shopItem = items.get(i);
            inventory.setItem(slot, buildItemStack(shopItem));
            slotMap.put(slot, shopItem);
        }

        // Balance display
        double balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, buildNavItem(
            Material.GOLD_INGOT,
            Component.text("Balance", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text(plugin.getEconomyManager().format(balance), NamedTextColor.GREEN)
        ));

        // Pagination arrows
        if (page > 0) {
            inventory.setItem(SLOT_PREV, buildNavItem(
                Material.ARROW,
                Component.text("◀ Previous Page", NamedTextColor.YELLOW),
                Component.text("Click to go back", NamedTextColor.GRAY)
            ));
        }
        if (page < maxPages() - 1) {
            inventory.setItem(SLOT_NEXT, buildNavItem(
                Material.ARROW,
                Component.text("Next Page ▶", NamedTextColor.YELLOW),
                Component.text("Click to continue", NamedTextColor.GRAY)
            ));
        }
    }

    private ItemStack buildItemStack(ShopItem shopItem) {
        ItemStack stack = new ItemStack(shopItem.getBukkitMaterial());
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text(shopItem.getDisplayName(), NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Category: " + shopItem.getCategory(), NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (shopItem.isBuyable()) {
            lore.add(Component.text("Buy: " + plugin.getEconomyManager().format(shopItem.getBuyPrice()),
                NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Left-click  → buy 1",   NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Shift+Left  → buy 64",  NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Not for sale", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        if (shopItem.isSellable()) {
            lore.add(Component.text("Sell: " + plugin.getEconomyManager().format(shopItem.getSellPrice()),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Right-click → sell 1",  NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Shift+Right → sell all", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Cannot be sold", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildNavItem(Material material, Component name, Component... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (Component line : loreLines) {
            lore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack fillerPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private int maxPages() {
        return Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SLOTS));
    }

    // ── Accessors used by the listener ────────────────────────────────────────

    public Inventory getInventory() { return inventory; }

    public ShopItem getItemAtSlot(int slot) { return slotMap.get(slot); }

    public boolean isNavSlot(int slot) {
        return slot >= PAGE_SLOTS;
    }

    public boolean isNextPage(int slot) { return slot == SLOT_NEXT && page < maxPages() - 1; }
    public boolean isPrevPage(int slot) { return slot == SLOT_PREV && page > 0; }

    public void nextPage() { page = Math.min(page + 1, maxPages() - 1); }
    public void prevPage() { page = Math.max(page - 1, 0); }
}
