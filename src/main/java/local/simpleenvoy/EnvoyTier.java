package local.simpleenvoy;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;

/**
 * Represents the 6 rarity tiers an envoy chest can be.
 * Colours match the SimpleCrates / FactionEnchants book colour scheme.
 */
public enum EnvoyTier {

    // colorCode, hologramName, fireworkColor, fireworkType, blockMaterial, defaultWeight
    SIMPLE  ("§7", "§f§lSIMPLE SUPPLY CRATE",   Color.fromRGB(0xAAAAAA), FireworkEffect.Type.STAR,      Material.IRON_BLOCK,         55),
    UNIQUE  ("§a", "§a§lUNIQUE SUPPLY CRATE",   Color.fromRGB(0x55FF55), FireworkEffect.Type.BALL,      Material.GOLD_BLOCK,         30),
    ELITE   ("§b", "§b§lELITE SUPPLY CRATE",    Color.fromRGB(0x55FFFF), FireworkEffect.Type.BALL_LARGE,Material.DIAMOND_BLOCK,      12),
    ULTIMATE("§5", "§5§lULTIMATE SUPPLY CRATE", Color.fromRGB(0xAA00AA), FireworkEffect.Type.BURST,     Material.EMERALD_BLOCK,       0),
    LEGENDARY("§6","§6§lLEGENDARY SUPPLY CRATE",Color.fromRGB(0xFFAA00), FireworkEffect.Type.BURST,     Material.CRYING_OBSIDIAN,     3),
    GODLY   ("§d", "§d§lGODLY SUPPLY CRATE",    Color.fromRGB(0xFF55FF), FireworkEffect.Type.CREEPER,   Material.BEACON,              0);

    private final String colorCode;
    private final String displayName;
    private final Color fireworkColor;
    private final FireworkEffect.Type fireworkType;
    private final Material blockMaterial;
    private final int defaultWeight;

    EnvoyTier(String colorCode, String displayName, Color fireworkColor,
              FireworkEffect.Type fireworkType, Material blockMaterial, int defaultWeight) {
        this.colorCode    = colorCode;
        this.displayName  = displayName;
        this.fireworkColor = fireworkColor;
        this.fireworkType = fireworkType;
        this.blockMaterial = blockMaterial;
        this.defaultWeight = defaultWeight;
    }

    public String colorCode()     { return colorCode;     }
    public String displayName()   { return displayName;   }
    public Color fireworkColor()  { return fireworkColor; }
    public FireworkEffect.Type fireworkType() { return fireworkType; }
    public Material blockMaterial() { return blockMaterial; }
    public int defaultWeight()   { return defaultWeight; }

    public String key() { return name().toLowerCase(); }

    /** Parse case-insensitively, returning null on failure. */
    public static EnvoyTier fromString(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    public FireworkEffect buildFireworkEffect() {
        return FireworkEffect.builder()
                .with(fireworkType)
                .withColor(fireworkColor)
                .withFade(Color.WHITE)
                .build();
    }
}
