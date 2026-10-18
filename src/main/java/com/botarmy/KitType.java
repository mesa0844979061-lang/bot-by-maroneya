package com.botarmy;

import org.bukkit.Material;

/** ชุดบอท (kit) ที่เลือกได้ในเมนู */
public enum KitType {
    CPVP("CPVP", Material.END_CRYSTAL,
            "เกราะเนเธอร์ไรต์ Prot 4 + ดาบ Sharp 5",
            "วางออบซิเดียน + คริสตัล ระเบิดใส่ศัตรู (คำนวณดาเมจ/ความปลอดภัยตัวเอง)",
            "แอปเปิลทองเอนแชนต์ + เอนเดอร์เพิร์ล"),
    CUTPVP("CUTPVP", Material.TNT_MINECART,
            "เกราะเนเธอร์ไรต์ Prot 3 + ดาบ + ขวาน",
            "รถรางระเบิด (TNT Minecart) + ราง + ไฟแช็ก",
            "ธนู/หน้าไม้ไฟ ยิงจุดรถระเบิดทันที",
            "แอปเปิลทอง + เอนเดอร์เพิร์ล"),
    SMP("SMP", Material.DIAMOND_SWORD,
            "เกราะเพชร Prot 3 + ดาบเพชร Sharp 4",
            "โล่ + แอปเปิลทอง + เอนเดอร์เพิร์ลน้อย"),
    FFA("FFA", Material.MACE,
            "เกราะเนเธอร์ไรต์ Prot 4 + ดาบ + ขวาน + ค้อน Mace",
            "Wind Charge ทำคอมโบค้อน (ระดับยากมีปีกด้วย)",
            "แอปเปิลทองเอนแชนต์ + เอนเดอร์เพิร์ล"),
    UHC("UHC", Material.GOLDEN_APPLE,
            "เกราะเพชร Prot 2 + ดาบเพชร Sharp 3",
            "โล่ + แอปเปิลทอง",
            "วางใยแมงมุม / ลาวา+น้ำ ใส่ศัตรู"),
    MACEPVP("MACE PVP", Material.MACE,
            "เกราะเนเธอร์ไรต์ Prot 4 + ค้อน Mace (Density/Breach/Wind Burst) อย่างเดียว + ขวานไว้ตัดโล่",
            "Wind Charge ลอยตัวทุบจากฟ้า + เพิร์ลวาร์ปแล้วลอยทุบ + ขวานทุบโล่แล้วต่อค้อน",
            "ระดับยาก: ปีก Elytra สลับเกราะทุบวนซ้ำ + บินทุบคนที่ยืนในหลุม"),
    AXEPVP("AXE PVP", Material.NETHERITE_AXE,
            "เกราะเนเธอร์ไรต์ Prot 4 + ขวานเนเธอร์ไรต์ Sharp 5 (ขวานอย่างเดียว)",
            "โดดคริติคอล + ทำลายโล่ศัตรู",
            "โล่ + แอปเปิลทองเอนแชนต์ + เอนเดอร์เพิร์ล");

    public final String display;
    public final Material icon;
    public final String[] lore;

    KitType(String display, Material icon, String... lore) {
        this.display = display;
        this.icon = icon;
        this.lore = lore;
    }
}
