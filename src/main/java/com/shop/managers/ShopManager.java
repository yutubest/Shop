package com.shop.managers;

import com.shop.ShopPlugin;
import com.shop.models.ShopItem;

import java.util.List;

public class ShopManager {

    private final ShopPlugin plugin;
    private List<ShopItem> items;

    public ShopManager(ShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        items = plugin.getDatabaseManager().getAllItems();
    }

    public List<ShopItem> getItems() {
        return items;
    }

    public ShopItem getById(int id) {
        return items.stream().filter(i -> i.getId() == id).findFirst().orElse(null);
    }
}
