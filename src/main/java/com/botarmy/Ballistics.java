package com.botarmy;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class Ballistics {
    private Ballistics() {}

    /** หาเวกเตอร์ปา ender pearl ให้ตกใกล้ to (จำลอง gravity 0.03 / drag 0.99) */
    static Vector solve(Location from, Location to, double speed) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double dy = to.getY() - from.getY();
        if (d < 3) return null;
        double hx = dx / d, hz = dz / d;
        for (double deg = 3; deg <= 70; deg += 1.5) {
            double rad = Math.toRadians(deg);
            double vh = Math.cos(rad) * speed, vy = Math.sin(rad) * speed;
            double x = 0, y = 0;
            for (int t = 0; t < 250; t++) {
                x += vh; y += vy;
                vh *= 0.99; vy *= 0.99; vy -= 0.03;
                if (x >= d) {
                    if (Math.abs(y - dy) <= 1.5) {
                        double h0 = Math.cos(rad) * speed;
                        return new Vector(hx * h0, Math.sin(rad) * speed, hz * h0);
                    }
                    break;
                }
                if (y < dy - 40) break;
            }
        }
        return null;
    }
}
