package com.botarmy;

import org.bukkit.Material;

import java.util.Locale;

public enum Level {
    EASY("easy", new Spec()
            .armor("IRON", 1).sword(Material.IRON_SWORD, 1).reach(3.0).speed(1.10f).miss(0.30)
            .shield(1).totem(0.0, 0)),
    MEDIUM("medium", new Spec()
            .armor("DIAMOND", 2).sword(Material.DIAMOND_SWORD, 3).reach(3.1).speed(1.20f).miss(0.15)
            .shield(2).retreat(0.40).gapples(6, false).pearls(4).totem(0.25, 8)),
    HIGH("high", new Spec()
            .armor("NETHERITE", 3).sword(Material.NETHERITE_SWORD, 4).reach(3.2).speed(1.30f).miss(0.06)
            .shield(3).retreat(0.45).gapples(12, false).pearls(8).totem(0.30, 16)
            .mace(true).axe(true).wind(1.25).jumpCrit(true)),
    MAX("max", new Spec()
            .armor("NETHERITE", 4).sword(Material.NETHERITE_SWORD, 5).reach(3.3).speed(1.40f).miss(0.0)
            .shield(3).retreat(0.50).gapples(24, true).pearls(16).totem(1.0, 64)
            .mace(true).axe(true).wind(1.5).jumpCrit(true).elytra(true).full(true));

    public final String id;
    public final Spec spec;

    Level(String id, Spec spec) {
        this.id = id;
        this.spec = spec;
    }

    public static Level parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase(Locale.ROOT)) {
            case "easy": case "ง่าย": case "1": return EASY;
            case "medium": case "mid": case "normal": case "กลาง": case "2": return MEDIUM;
            case "high": case "hard": case "สูง": case "3": return HIGH;
            case "max": case "ultimate": case "สูงสุด": case "4": return MAX;
            default: return null;
        }
    }

    /** ค่าความสามารถของแต่ละระดับ */
    public static final class Spec {
        public String armorTier = "IRON";
        public int prot = 1;
        public Material swordMat = Material.IRON_SWORD;
        public int sharp = 1;
        public double reach = 3.0;
        public float speed = 1.1f;
        public double miss = 0.2;
        /** 0 = ไม่มีโล่ฉลาด, 1-3 = ยิ่งสูงยิ่งฉลาด */
        public int shield = 1;
        public double retreatHp = 0;
        public int gapples = 0;
        public boolean enchantedApples = false;
        public int pearls = 0;
        public double totemChance = 0;
        public int totemCount = 0;
        public boolean mace, axe, wind, jumpCrit, elytra, full;
        public double boost = 1.25;

        public Spec armor(String tier, int p) { armorTier = tier; prot = p; return this; }
        public Spec sword(Material m, int sharp) { swordMat = m; this.sharp = sharp; return this; }
        public Spec reach(double r) { reach = r; return this; }
        public Spec speed(float s) { speed = s; return this; }
        public Spec miss(double m) { miss = m; return this; }
        public Spec shield(int s) { shield = s; return this; }
        public Spec retreat(double hp) { retreatHp = hp; return this; }
        public Spec gapples(int n, boolean ench) { gapples = n; enchantedApples = ench; return this; }
        public Spec pearls(int n) { pearls = n; return this; }
        public Spec totem(double chance, int count) { totemChance = chance; totemCount = count; return this; }
        public Spec mace(boolean b) { mace = b; return this; }
        public Spec axe(boolean b) { axe = b; return this; }
        public Spec wind(double boost) { wind = true; this.boost = boost; return this; }
        public Spec jumpCrit(boolean b) { jumpCrit = b; return this; }
        public Spec elytra(boolean b) { elytra = b; return this; }
        public Spec full(boolean b) { full = b; return this; }

        public boolean canGapple() { return gapples > 0; }
        public boolean canPearl() { return pearls > 0; }
    }
}
