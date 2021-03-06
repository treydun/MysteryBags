package cheezbags;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import cheezbags.listener.MysteryBagsListener;

public class MysteryBags extends JavaPlugin {
    
    public static final String PREFIX = "§2[MysteryBags] §f";
    
    private static MysteryBags instance;
    public static String VERSION;
    
    public boolean overrideDrops, dropFromMobs, requirePlayerKill, lootingSensitiveChance, lootingSensitiveAmount, spyMessage, announceRare, rareSound, rareFirework, logBags, logRares, worldguard, allowBaby, allowSpawners;
    public double lootingEffectiveness;
    
    public Set<String> limitRegions, limitWorlds;
    public String rareLootMessage, openMessage;
    public Map<Material, HashSet<String>> rares = new HashMap<Material, HashSet<String>>();
    
    public Random random;
    
    private YamlConfiguration output;
    private static FileConfiguration config;
    public Map<String, MysteryBag> cheezBags = new HashMap<String, MysteryBag>();
    
    public Set<String> acceptableCommands = new HashSet<String>();
    private String[] ac = {
        "mysterybags",
        "mysterybag",
        "mbag",
        "mbags"
    };
    
    @Override
    public void onEnable() {
        CommandHandler handler = new CommandHandler();
        for (String s : ac) {
            acceptableCommands.add(s);
            getCommand(s).setExecutor(handler);
        }

        String pack = Bukkit.getServer().getClass().getPackage().getName();
        VERSION = pack.substring(pack.lastIndexOf('.') + 1);
        
        getServer().getPluginManager().registerEvents(new MysteryBagsListener(this), this);
        
        instance = this;
        config();
        random = new Random();
        
        if (!new File(getDataFolder(), "output.yml").exists())
            saveResource("output.yml", false);
        if (!new File(getDataFolder(), "treasure.yml").exists())
            saveResource("treasure.yml", false);
        worldguard = getServer().getPluginManager().getPlugin("WorldGuard") != null;
        
        load();
    }
    
    private void config() {
        this.saveDefaultConfig();
        config = getConfig();
    }
    
    public void load() {
        reloadConfig();
        config = getConfig();
        
        overrideDrops = ConfigReader.getBoolean(config, "override-drops");
        dropFromMobs = ConfigReader.getBoolean(config, "drop-from-mobs");
        requirePlayerKill = ConfigReader.getBoolean(config, "require-player-kill");
        lootingSensitiveChance = ConfigReader.getBoolean(config, "looting-sensitive");
        lootingSensitiveAmount = ConfigReader.getBoolean(config, "looting-increases-amount");
        lootingEffectiveness = config.getDouble("looting-effectiveness");
        limitRegions = new HashSet<String>(config.getStringList("drop-limit-to-area"));
        limitWorlds = new HashSet<String>(config.getStringList("drop-limit-to-world"));
        openMessage = config.getString("open-message").replace("&", "§");
        rareLootMessage = config.getString("announce-rare-loot-message").replace("&", "§");
        spyMessage = ConfigReader.getBoolean(config, "openingspymessage");
        announceRare = ConfigReader.getBoolean(config, "announce-rare-loot");
        rareFirework = ConfigReader.getBoolean(config, "rare-loot-firework");
        rareSound = ConfigReader.getBoolean(config, "rare-loot-sound");
        logBags = ConfigReader.getBoolean(config, "log-bags");
        logRares = ConfigReader.getBoolean(config, "log-rare-loot");
        allowSpawners = config.isSet("default-allow-spawner-drop") ? ConfigReader.getBoolean(config, "default-allow-spawner-drop") : true;
        allowBaby = config.isSet("default-allow-baby-drop") ? ConfigReader.getBoolean(config, "default-allow-baby-drop") : true;
        
        rares.clear();
        for (String key : config.getConfigurationSection("rare-loot").getKeys(false)) {
            try {
                rares.put(Material.valueOf(key.toUpperCase()), new HashSet<String>(config.getStringList("rare-loot." + key)));
            } catch (Exception e) {
                throwError(key + " in the rare-loot section isn't correct.");
            }
        }
        
        cheezBags.clear();
        output = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "output.yml"));
        
        for (File f : getDataFolder().listFiles()) {
            if (f.getName().endsWith(".yml")) {
                String s = f.getName();
                switch (s.replace(".yml", "")) {
                case "config":
                case "output":
                    break;
                    default:
                        MysteryBag bag;
                        try {
                            bag = new MysteryBag(s.replace(".yml", ""), YamlConfiguration.loadConfiguration(f));
                        } catch (Exception e) {
                            Bukkit.getLogger().info("[MysteryBags] " + s + " is not a valid Mystery Bag! Kill it before it lays eggs!");
                            e.printStackTrace();
                            continue;
                        }
                        cheezBags.put(bag.getId(), bag);
                        Bukkit.getLogger().info("Registered " + bag.getId());
                        break;
                }
            }
        }
    }
    
    public static MysteryBags instance() {
        return instance;
    }
    
    public static FileConfiguration getconfig() {
        return config;
    }
    
    public static void throwError(String s) {
        Bukkit.getConsoleSender().sendMessage("§4[ERROR] §c" + s);
    }
    
    public static boolean isRare(ItemStack loot) {
        String nameToCheck = loot.getItemMeta().hasDisplayName() ? loot.getItemMeta().getDisplayName().replace("§", "&") : null;
        if (nameToCheck != null)
            nameToCheck = nameToCheck.contains(" statistic_item_amount ") ? nameToCheck.split(" statistic_item_amount ")[0] : nameToCheck;
        return instance.rares.containsKey(loot.getType()) && (instance.rares.get(loot.getType()).isEmpty() || instance.rares.get(loot.getType()).contains(nameToCheck));
    }
    
    public static String getRareLootMessage(Player p, ItemStack loot) {
        return instance.rareLootMessage.replace("%PLAYER%", p.getName()).replace("%ITEM%", capitalizeFirst(loot.getType().toString())).replace("%ITEMNAME%", unnamed(loot) ? capitalizeFirst(loot.getType().toString()) : loot.getItemMeta().getDisplayName());
    }
    
    private static boolean unnamed(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName())
            return true;
        String[] split = meta.getDisplayName().split(" statistic_item_amount ");
        if (split.length > 1 && split[0].equals("§j")) {
            return true;
        }
        return false;
    }
    
    public static String getOpenMessage(ItemStack loot) {
        if (loot == null)
            return instance.openMessage;
        return instance.openMessage.replace("%ITEM%", capitalizeFirst(loot.getType().toString())).replace("%ITEMNAME%", loot.getItemMeta().getDisplayName() == null ? (isCommandBook(loot) ? "magic" : capitalizeFirst(loot.getType().toString())) : loot.getItemMeta().getDisplayName());
    }
    
    public static boolean isCommandBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK)
            return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasLore() && meta.getLore().get(0).equals("§e§lRun Command:§j§j§j");
    }
    
    public static String capitalizeFirst(String s) {
        String[] split = s.split("_");
        String rebuild = "";
        for (String part : split)
            rebuild += part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase() + " ";
        return rebuild.substring(0, rebuild.length() - 1);
    }
    
    public static void log(Player p, MysteryBag bag, ItemStack loot, boolean rare) {
        if (instance.logBags) {
            int i = instance.output.getInt("opened-bags." + bag.getId() + "." + p.getName());
            instance.output.set("opened-bags." + bag.getId() + "." + p.getName(), i + 1);
            
            try {
                instance.output.save(new File(instance.getDataFolder(), "output.yml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (rare && instance.logRares) {
            List<String> rares = instance.output.getStringList("rare-loot");
            rares.add(p.getName() + " got a " + loot.getType().toString() + " " + (loot.getItemMeta().getDisplayName() == null ? "" : "called " + loot.getItemMeta().getDisplayName() + " ") + "from " + bag.getId());
            instance.output.set("rare-loot", rares);
            
            try {
                instance.output.save(new File(instance.getDataFolder(), "output.yml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void giveItem(Player p, ItemStack item) {
        if (item != null)
            for (ItemStack overflow : p.getInventory().addItem(item).values())
                if (overflow != null)
                    p.getWorld().dropItem(p.getLocation().add(0.3, 0.2, 0.3), overflow);
    }
    
    public static void setCustomSkullItemEncoded(ItemStack item, String base64) {
        SkullMeta im = (SkullMeta) item.getItemMeta();
        GameProfile profile = new GameProfile(a(base64), base64.substring(base64.length() - 10, base64.length() - 1));
        profile.getProperties().put("textures", new Property("textures", base64));
        Field profileField = null;
        try {
            profileField = im.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(im, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        item.setItemMeta(im);
    }
    
    public static String getStringEncoded(ItemStack item) {
        SkullMeta im = (SkullMeta) item.getItemMeta();
        GameProfile profile = null;
        Field profileField = null;
        try {
            profileField = im.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profile = (GameProfile) profileField.get(im);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
        Collection<Property> prop = profile.getProperties().get("textures");
        for (Property p : prop) {
            if (p.getName().equals("textures")) {
                return p.getValue();
            }
        }
        return null;
    }
    
    private static UUID a(String base64) {
        String sub = base64.substring(base64.length() - 13, base64.length() - 3);
        Random rand = new Random(sub.hashCode());
        byte[] arr = new byte[16];
        rand.nextBytes(arr);
        return UUID.nameUUIDFromBytes(arr);
    }

}
