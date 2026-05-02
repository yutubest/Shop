package com.shop.database;

import com.shop.ShopPlugin;
import com.shop.models.ShopItem;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private final ShopPlugin plugin;
    private Connection connection;

    public DatabaseManager(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder + "/shop.db");
            createTables();
            plugin.getLogger().info("Database connected successfully.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid    TEXT PRIMARY KEY,
                    name    TEXT NOT NULL,
                    balance REAL NOT NULL DEFAULT 0.0
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_items (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    material     TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    buy_price    REAL NOT NULL DEFAULT -1,
                    sell_price   REAL NOT NULL DEFAULT -1,
                    category     TEXT NOT NULL DEFAULT 'General'
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT    NOT NULL,
                    player_name TEXT    NOT NULL,
                    material    TEXT    NOT NULL,
                    quantity    INTEGER NOT NULL,
                    total_price REAL    NOT NULL,
                    type        TEXT    NOT NULL,
                    timestamp   INTEGER NOT NULL
                )
            """);
        }

        insertDefaultItems();
    }

    private void insertDefaultItems() throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM shop_items")) {
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        String[][] defaults = {
            // material, display_name, buy_price, sell_price, category
            {"DIAMOND",        "Diamond",       "100.0", "50.0",  "Gems"},
            {"EMERALD",        "Emerald",       "80.0",  "40.0",  "Gems"},
            {"AMETHYST_SHARD", "Amethyst Shard","20.0",  "10.0",  "Gems"},
            {"GOLD_INGOT",     "Gold Ingot",    "20.0",  "10.0",  "Metals"},
            {"IRON_INGOT",     "Iron Ingot",    "10.0",  "5.0",   "Metals"},
            {"COPPER_INGOT",   "Copper Ingot",  "4.0",   "2.0",   "Metals"},
            {"NETHERITE_SCRAP","Netherite Scrap","200.0","100.0", "Metals"},
            {"OAK_LOG",        "Oak Log",       "5.0",   "2.0",   "Wood"},
            {"BIRCH_LOG",      "Birch Log",     "5.0",   "2.0",   "Wood"},
            {"SPRUCE_LOG",     "Spruce Log",    "5.0",   "2.0",   "Wood"},
            {"JUNGLE_LOG",     "Jungle Log",    "6.0",   "3.0",   "Wood"},
            {"WHEAT",          "Wheat",         "2.0",   "1.0",   "Food"},
            {"BREAD",          "Bread",         "5.0",   "2.5",   "Food"},
            {"APPLE",          "Apple",         "3.0",   "1.5",   "Food"},
            {"COOKED_BEEF",    "Steak",         "10.0",  "5.0",   "Food"},
            {"COOKED_CHICKEN", "Cooked Chicken","6.0",   "3.0",   "Food"},
            {"STONE",          "Stone",         "1.0",   "0.5",   "Blocks"},
            {"COBBLESTONE",    "Cobblestone",   "0.5",   "0.25",  "Blocks"},
            {"SAND",           "Sand",          "1.0",   "0.5",   "Blocks"},
            {"GRAVEL",         "Gravel",        "1.0",   "0.5",   "Blocks"},
            {"COAL",           "Coal",          "5.0",   "2.5",   "Materials"},
            {"REDSTONE",       "Redstone",      "8.0",   "4.0",   "Materials"},
            {"LAPIS_LAZULI",   "Lapis Lazuli",  "10.0",  "5.0",   "Materials"},
            {"BONE_MEAL",      "Bone Meal",     "3.0",   "1.5",   "Materials"},
            {"LEATHER",        "Leather",       "8.0",   "4.0",   "Materials"},
            {"STRING",         "String",        "3.0",   "1.5",   "Materials"},
            {"FEATHER",        "Feather",       "3.0",   "1.5",   "Materials"},
            {"GUNPOWDER",      "Gunpowder",     "6.0",   "3.0",   "Materials"},
            {"BLAZE_ROD",      "Blaze Rod",     "15.0",  "7.0",   "Materials"},
            {"ENDER_PEARL",    "Ender Pearl",   "20.0",  "10.0",  "Materials"},
        };

        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO shop_items (material, display_name, buy_price, sell_price, category) VALUES (?,?,?,?,?)")) {
            for (String[] item : defaults) {
                ps.setString(1, item[0]);
                ps.setString(2, item[1]);
                ps.setDouble(3, Double.parseDouble(item[2]));
                ps.setDouble(4, Double.parseDouble(item[3]));
                ps.setString(5, item[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Player balance ────────────────────────────────────────────────────────

    public boolean playerExists(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT 1 FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("playerExists error: " + e.getMessage());
            return false;
        }
    }

    public double getBalance(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT balance FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("getBalance error: " + e.getMessage());
        }
        return 0.0;
    }

    public void setBalance(UUID uuid, String name, double balance) {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO players (uuid, name, balance) VALUES (?,?,?) " +
            "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, balance = excluded.balance")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, balance);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("setBalance error: " + e.getMessage());
        }
    }

    // ── Shop items ────────────────────────────────────────────────────────────

    public List<ShopItem> getAllItems() {
        List<ShopItem> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
            "SELECT * FROM shop_items ORDER BY category, id")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new ShopItem(
                    rs.getInt("id"),
                    rs.getString("material"),
                    rs.getString("display_name"),
                    rs.getDouble("buy_price"),
                    rs.getDouble("sell_price"),
                    rs.getString("category")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("getAllItems error: " + e.getMessage());
        }
        return items;
    }

    public void addItem(String material, String displayName, double buyPrice, double sellPrice, String category) {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO shop_items (material, display_name, buy_price, sell_price, category) VALUES (?,?,?,?,?)")) {
            ps.setString(1, material.toUpperCase());
            ps.setString(2, displayName);
            ps.setDouble(3, buyPrice);
            ps.setDouble(4, sellPrice);
            ps.setString(5, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("addItem error: " + e.getMessage());
        }
    }

    public boolean removeItem(int id) {
        try (PreparedStatement ps = connection.prepareStatement(
            "DELETE FROM shop_items WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("removeItem error: " + e.getMessage());
            return false;
        }
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    public void logTransaction(UUID playerUuid, String playerName, String material,
                               int quantity, double totalPrice, String type) {
        try (PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO transactions (player_uuid,player_name,material,quantity,total_price,type,timestamp) " +
            "VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, material);
            ps.setInt(4, quantity);
            ps.setDouble(5, totalPrice);
            ps.setString(6, type);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("logTransaction error: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }
}
