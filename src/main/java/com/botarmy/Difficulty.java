package com.botarmy;

import org.bukkit.Material;

/** ระดับความเก่งของบอท (กระทบเฉพาะ AI ไม่กระทบชุดเกราะของ kit) */
public enum Difficulty {
    LOW("ต่ำ", Material.WOODEN_SWORD, "ตีพลาดบ่อย ไม่โดดตี โล่ไม่ฉลาด ของพิเศษของชุดใช้แบบงุ่มง่าม"),
    MEDIUM("กลาง", Material.DIAMOND_SWORD, "โดดตีคริติคอลบ้าง ใช้โล่/แอปเปิลทอง/เพิร์ล ท่าไม้ตายพอประมาณ"),
    HARD("ยาก", Material.NETHERITE_SWORD, "โดดตีทุกจังหวะ แม่น เร็ว โล่ฉลาด ท่าไม้ตายระดับโลก");

    public final String display;
    public final Material icon;
    public final String desc;

    Difficulty(String display, Material icon, String desc) {
        this.display = display;
        this.icon = icon;
        this.desc = desc;
    }
}
