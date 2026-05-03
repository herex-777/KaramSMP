package me.herex.karmsmp.auction;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class AuctionCategoryUtil {
    private static final Set<Material> TOOLS = EnumSet.noneOf(Material.class);
    private static final Set<Material> COMBAT = EnumSet.noneOf(Material.class);
    private static final Set<Material> POTIONS = EnumSet.noneOf(Material.class);
    private static final Set<Material> BOOKS = EnumSet.noneOf(Material.class);
    private static final Set<Material> INGREDIENTS = EnumSet.noneOf(Material.class);
    private static final Set<Material> UTILITIES = EnumSet.noneOf(Material.class);

    static {
        addTools("PICKAXE", "AXE", "SHOVEL", "HOE", "SHEARS", "FISHING_ROD", "FLINT_AND_STEEL", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK");
        addCombat("SWORD", "BOW", "CROSSBOW", "TRIDENT", "ARROW", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SHIELD", "TOTEM_OF_UNDYING");
        addExact(POTIONS, "POTION", "SPLASH_POTION", "LINGERING_POTION", "DRAGON_BREATH", "GLASS_BOTTLE", "FERMENTED_SPIDER_EYE", "BLAZE_POWDER", "GLISTERING_MELON_SLICE", "MAGMA_CREAM", "GHAST_TEAR", "RABBIT_FOOT");
        addBooks("BOOK", "ENCHANTED_BOOK", "WRITABLE_BOOK", "WRITTEN_BOOK", "BOOKSHELF", "CHISELED_BOOKSHELF", "LECTERN", "PAPER", "MAP", "COMPASS");
        addIngredients("INGOT", "NUGGET", "GEM", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "REDSTONE", "COAL", "CHARCOAL", "QUARTZ", "DYE", "SEEDS", "WHEAT", "SUGAR_CANE", "BAMBOO", "KELP", "VINE", "LILY_PAD", "STRING", "FEATHER", "LEATHER", "BONE", "BONE_MEAL", "GUNPOWDER", "BLAZE_ROD", "ENDER_PEARL", "EYE_OF_ENDER");
        addUtilities("CHEST", "BARREL", "SHULKER_BOX", "HOPPER", "DISPENSER", "DROPPER", "OBSERVER", "PISTON", "BUTTON", "PRESSURE_PLATE", "COMPARATOR", "REPEATER", "CRAFTING_TABLE", "FURNACE", "SMOKER", "STONECUTTER", "LOOM", "SMITHING_TABLE", "GRINDSTONE", "MINECART", "BOAT", "RAIL", "SADDLE", "LEAD", "NAME_TAG", "BUCKET", "BED", "CLOCK", "FIREWORK", "ELYTRA", "BELL", "LANTERN", "TORCH", "ANVIL");
    }

    private AuctionCategoryUtil() {
    }

    public static boolean belongsTo(Material material, String category) {
        if (material == null || category == null) {
            return false;
        }
        String key = category.toLowerCase(Locale.ROOT);
        if (key.equals("all")) {
            return true;
        }
        return switch (key) {
            case "blocks" -> material.isBlock() && !UTILITIES.contains(material);
            case "tools" -> TOOLS.contains(material);
            case "food" -> material.isEdible();
            case "combat" -> COMBAT.contains(material);
            case "potions" -> POTIONS.contains(material);
            case "books" -> BOOKS.contains(material);
            case "ingredients" -> INGREDIENTS.contains(material);
            case "utilities" -> UTILITIES.contains(material);
            case "shulker" -> isShulkerBox(material);
            default -> false;
        };
    }

    public static boolean isShulkerBox(Material material) {
        return material != null && material.name().endsWith("SHULKER_BOX");
    }

    public static String display(String category) {
        if (category == null) {
            return "all";
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "blocks" -> "ʙʟᴏᴄᴋꜱ";
            case "tools" -> "ᴛᴏᴏʟꜱ";
            case "food" -> "ꜰᴏᴏᴅ";
            case "combat" -> "ᴄᴏᴍʙᴀᴛ";
            case "potions" -> "ᴘᴏᴛɪᴏɴꜱ";
            case "books" -> "ʙᴏᴏᴋꜱ";
            case "ingredients" -> "ɪɴɢʀᴇᴅɪᴇɴᴛꜱ";
            case "utilities" -> "ᴜᴛɪʟɪᴛɪᴇꜱ";
            case "shulker" -> "ꜱʜᴜʟᴋᴇʀꜱ";
            default -> "ᴀʟʟ";
        };
    }

    private static void addTools(String... contains) {
        addContaining(TOOLS, contains);
    }

    private static void addCombat(String... contains) {
        addContaining(COMBAT, contains);
    }

    private static void addBooks(String... names) {
        addExact(BOOKS, names);
    }

    private static void addIngredients(String... contains) {
        addContaining(INGREDIENTS, contains);
    }

    private static void addUtilities(String... contains) {
        addContaining(UTILITIES, contains);
    }

    private static void addContaining(Set<Material> set, String... contains) {
        for (Material material : Material.values()) {
            String name = material.name();
            for (String token : contains) {
                if (name.contains(token)) {
                    set.add(material);
                    break;
                }
            }
        }
    }

    private static void addExact(Set<Material> set, String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
    }
}
