package com.botarmy;

import org.bukkit.Material;

/** ค่าความสามารถของบอท 1 ตัว = ชุด (kit) + ระดับความเก่ง + ตั้งค่าโทเทม */
public final class Spec {
    public String armorTier = "IRON";
    public int prot = 1;
    public Material swordMat = Material.IRON_SWORD;
    public int sharp = 1;
    public int axeSharp = 4;
    public double reach = 3.0;
    public float speed = 1.1f;
    public double miss = 0.2;
    /** 0 = ไม่มีโล่ฉลาด, 1-3 = ยิ่งสูงยิ่งฉลาด */
    public int shield = 1;
    public double retreatHp = 0;
    public int gapples = 0;
    public boolean enchantedApples = false;
    public int pearls = 0;
    /** ถือโทเทมที่มือซ้าย */
    public boolean totem;
    /** โทเทมเป็นสแตก 99 (ใช้ได้เมื่อ totem = true) */
    public boolean totem99;
    /** จำนวนโทเทมเมื่อไม่ใช้สแตก 99 */
    public int totemCount = 1;
    public boolean mace, axe, wind, jumpCrit, elytra, full;
    /** ใช้ขวานอย่างเดียว (ไม่มีดาบ) */
    public boolean axeOnly;
    /** ค้อน Mace เป็นอาวุธหลักช่องแรก (ไม่มีดาบ) */
    public boolean maceMain;
    public int density = 3;
    /** ต่อคอมโบเด้ง Wind Burst แล้วทุบซ้ำได้กี่ครั้ง */
    public int chainMax = 0;
    /** คูลดาวน์ท่าลอยตัวทุบ (tick) / โอกาสเริ่มท่าต่อจังหวะ */
    public int comboCd = 100;
    public double comboChance = 0.6;
    /** ดาบมี Fire Aspect (ตีแล้วติดไฟ) */
    public boolean fireAspect;
    public double boost = 1.25;

    // ---- โดดตี (critical)
    /** โอกาสวางแผนโดดตีคริต ต่อการตี 1 ครั้ง (0 = ไม่โดด) */
    public double critChance = 0;
    /** แรงสะบัดเพิ่ม (W-tap) หลังตีโดน */
    public double kb = 0;

    /** ใยแมงมุม: cooldown (tick) 0 = ไม่ใช้, ring = จำนวนด้านที่ล้อม */
    public int webCd = 0, webRing = 0;
    /** ลาวา+น้ำ: cooldown (tick) 0 = ไม่ใช้ */
    public int lavaCd = 0;

    // ---- CPVP: คริสตัล
    public boolean crystal;
    /** หน่วงก่อนระเบิดคริสตัลหลังวาง (tick) ยิ่งน้อยยิ่งเก่ง */
    public int crystalDelay = 8;
    public int crystalCd = 60;
    /** ระยะวาง/ตีคริสตัล (บล็อก) */
    public double crystalRange = 3.5;
    /** ยอมให้ตัวเองโดนไม่เกินกี่ส่วนของเลือด */
    public double crystalSelfTol = 0.9;
    /** น้ำหนักความกลัวดาเมจตัวเองตอนให้คะแนนตำแหน่ง */
    public double crystalSelfW = 0.3;
    /** 0 = เลือกตำแหน่งมั่ว ๆ, 1 = เลือกตำแหน่งดีที่สุดเสมอ */
    public double crystalSkill = 0;
    public double crystalMinDmg = 1;

    // ---- CUTPVP: รถรางระเบิด + ธนู/หน้าไม้ไฟ
    public boolean cart, bow;
    public int cartCount = 1, cartCd = 600, cartFuse = 60;
    /** ยิงธนูไฟจุดรถระเบิดทันที (ไม่ต้องรอฟิวส์) */
    public boolean cartArrowIgnite;
    public int bowCd = 60;
    /** ความคลาดเคลื่อนการเล็ง (องศา) */
    public double bowAcc = 6;
    public double arrowDamage = 2.5;

    /** /bot macex: บอทค้อนฝีมือระดับโลก 1 ตัว (เหนือระดับยากปกติ) */
    public static Spec macex() {
        Spec s = of(KitType.MACEPVP, Difficulty.HARD, true, false);
        s.totemCount = 8;
        s.miss = 0.0;
        s.speed = 1.45f;
        s.reach = 3.4;
        s.critChance = 1.0;
        s.kb = 0.32;
        s.retreatHp = 0.45;
        s.comboCd = 20;
        s.comboChance = 1.0;
        s.chainMax = 5;
        s.boost = 1.6;
        return s;
    }

    public boolean canGapple() { return gapples > 0; }
    public boolean canPearl() { return pearls > 0; }
    public int totemAmount() { return totem99 ? 99 : totemCount; }

    public static Spec of(KitType kit, Difficulty d, boolean totem, boolean totem99) {
        Spec s = new Spec();
        boolean low = d == Difficulty.LOW;
        boolean mid = d == Difficulty.MEDIUM;
        boolean hard = d == Difficulty.HARD;

        // ---- AI ตามระดับ (กลาง = โดดตีแต่ไม่โหด / ยาก = โหดสุด)
        switch (d) {
            case LOW -> {
                s.reach = 3.0; s.speed = 1.10f; s.miss = 0.30; s.shield = 1; s.retreatHp = 0; s.totemCount = 1;
                s.critChance = 0; s.kb = 0;
            }
            case MEDIUM -> {
                s.reach = 3.1; s.speed = 1.18f; s.miss = 0.14; s.shield = 2; s.retreatHp = 0.35; s.totemCount = 2;
                s.critChance = 0.65; s.kb = 0;
            }
            case HARD -> {
                s.reach = 3.3; s.speed = 1.38f; s.miss = 0.02; s.shield = 3; s.retreatHp = 0.50; s.totemCount = 4;
                s.critChance = 0.97; s.kb = 0.28;
            }
        }
        s.jumpCrit = !low;

        // ---- ชุดตาม kit
        int gapBase;
        int pearlBase;
        switch (kit) {
            case CPVP -> {
                s.armorTier = "NETHERITE"; s.prot = 4;
                s.swordMat = Material.NETHERITE_SWORD; s.sharp = 5;
                s.full = true; s.enchantedApples = true; s.fireAspect = true;
                gapBase = 8; pearlBase = 16;
                s.crystal = true;
                s.crystalDelay = low ? 8 : mid ? 4 : 1;
                s.crystalCd = low ? 70 : mid ? 22 : 6;
                s.crystalRange = low ? 3.5 : mid ? 4.0 : 4.5;
                s.crystalSelfTol = low ? 0.9 : mid ? 0.6 : 0.5;
                s.crystalSelfW = low ? 0.3 : mid ? 1.0 : 1.5;
                s.crystalSkill = low ? 0.0 : mid ? 0.75 : 1.0;
                s.crystalMinDmg = low ? 1 : mid ? 4 : 2;
            }
            case CUTPVP -> {
                s.armorTier = "NETHERITE"; s.prot = 3;
                s.swordMat = Material.NETHERITE_SWORD; s.sharp = 5;
                s.axe = true; s.axeSharp = 4;
                gapBase = 6; pearlBase = 8;
                s.cart = true; s.bow = true;
                s.cartCount = low ? 1 : mid ? 2 : 3;
                s.cartCd = low ? 700 : mid ? 360 : 160;
                s.cartFuse = low ? 60 : mid ? 36 : 22;
                s.cartArrowIgnite = hard;
                s.bowCd = low ? 70 : mid ? 40 : 20;
                s.bowAcc = low ? 6.0 : mid ? 2.5 : 0.6;
                s.arrowDamage = low ? 2.0 : mid ? 2.5 : 4.0;
            }
            case SMP -> {
                s.armorTier = "DIAMOND"; s.prot = 3;
                s.swordMat = Material.DIAMOND_SWORD; s.sharp = 4;
                gapBase = 6; pearlBase = 4;
            }
            case FFA -> {
                s.armorTier = "NETHERITE"; s.prot = 4;
                s.swordMat = Material.NETHERITE_SWORD; s.sharp = 5;
                s.axe = true; s.axeSharp = 5; s.full = true; s.enchantedApples = true; s.fireAspect = true;
                s.mace = !low;
                s.wind = !low;
                s.density = 5;
                s.boost = hard ? 1.5 : 1.25;
                s.elytra = hard;
                s.chainMax = hard ? 3 : 0;
                s.comboCd = hard ? 60 : 100;
                s.comboChance = 0.6;
                gapBase = 8; pearlBase = 16;
            }
            case MACEPVP -> {
                s.armorTier = "NETHERITE"; s.prot = 4;
                s.swordMat = Material.MACE; s.sharp = 0;
                s.maceMain = true; s.mace = true; s.axe = true; s.axeSharp = 5;
                s.full = true; s.enchantedApples = true; s.density = 5;
                s.wind = !low;
                s.elytra = hard;
                s.boost = hard ? 1.5 : 1.3;
                s.chainMax = low ? 0 : mid ? 1 : 3;
                s.comboCd = low ? 140 : mid ? 90 : 40;
                s.comboChance = low ? 0.3 : mid ? 0.55 : 0.92;
                gapBase = 8; pearlBase = 16;
            }
            case AXEPVP -> {
                s.armorTier = "NETHERITE"; s.prot = 4;
                s.swordMat = Material.NETHERITE_AXE; // ไม่ได้ใช้ (ขวานอย่างเดียว)
                s.axe = true; s.axeOnly = true; s.axeSharp = 5;
                s.full = true; s.enchantedApples = true;
                gapBase = 8; pearlBase = 16;
            }
            default -> { // UHC
                s.armorTier = "DIAMOND"; s.prot = 2;
                s.swordMat = Material.DIAMOND_SWORD; s.sharp = 3;
                gapBase = 6; pearlBase = 0;
                s.webCd = low ? 320 : mid ? 180 : 120;
                s.webRing = hard ? 3 : 0;
                s.lavaCd = low ? 600 : mid ? 320 : 200;
            }
        }
        s.gapples = low ? 0 : gapBase;
        s.pearls = low ? 0 : (mid ? Math.min(pearlBase, 4) : pearlBase);

        // ---- โทเทม (สแตก 99 ต้องถือโทเทมด้วย)
        s.totem = totem || totem99;
        s.totem99 = totem99;
        return s;
    }
}
