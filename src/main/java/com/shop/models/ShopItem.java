package com.shop.models;

import org.bukkit.Material;

public class ShopItem {

    private final int id;
    private final String material;
    private final String displayName;
    private final double buyPrice;
    private final double sellPrice;
    private final String category;

    public ShopItem(int id, String material, String displayName, double buyPrice, double sellPrice, String category) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.category = category;
    }

    public int getId() { return id; }
    public String getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public double getBuyPrice() { return buyPrice; }
    public double getSellPrice() { return sellPrice; }
    public String getCategory() { return category; }
    public boolean isBuyable() { return buyPrice > 0; }
    public boolean isSellable() { return sellPrice > 0; }

    public Material getBukkitMaterial() {
        try {
            return Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }
}
