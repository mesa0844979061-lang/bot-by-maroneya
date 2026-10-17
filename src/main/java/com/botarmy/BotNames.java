package com.botarmy;

import org.bukkit.Bukkit;

import java.util.Random;
import java.util.Set;

final class BotNames {
    private BotNames() {}

    private static final String[] A = {
            "Shadow", "Frost", "Nova", "Pixel", "Blaze", "Storm", "Viper", "Cobalt", "Echo", "Raven",
            "Zen", "Hyper", "Ninja", "Void", "Ember", "Lunar", "Turbo", "Rogue", "Titan", "Mystic",
            "Crimson", "Ghost", "Arctic", "Savage", "Dusty", "Cosmic", "Tiny", "Mega", "Lucky", "Sneaky",
            "Rapid", "Silent", "Golden", "Iron", "Toxic", "Sky", "Night", "Solar", "Atomic", "Wild"
    };
    private static final String[] B = {
            "Wolf", "Fox", "Panda", "Dragon", "Knight", "Hunter", "Sniper", "Pirate", "Wizard", "Tiger",
            "Hawk", "Cat", "Bear", "Gamer", "Miner", "Builder", "Slayer", "Ranger", "Phantom", "Samurai",
            "Falcon", "Cobra", "Shark", "Lynx", "Otter", "Bee", "Turtle", "Eagle", "Spartan", "Legend"
    };

    static String generate(Random r, Set<String> taken) {
        for (int tries = 0; tries < 200; tries++) {
            String a = A[r.nextInt(A.length)];
            String b = B[r.nextInt(B.length)];
            String n;
            switch (r.nextInt(5)) {
                case 0: n = a + b; break;
                case 1: n = a + "_" + b; break;
                case 2: n = a + b + (r.nextInt(99) + 1); break;
                case 3: n = (a + b).toLowerCase() + (2000 + r.nextInt(26)); break;
                default: n = a + (r.nextInt(9000) + 100); break;
            }
            if (n.length() > 16) n = n.substring(0, 16);
            String low = n.toLowerCase();
            if (taken.contains(low)) continue;
            if (Bukkit.getPlayerExact(n) != null) continue;
            taken.add(low);
            return n;
        }
        String n = "Bot" + (System.nanoTime() % 1000000);
        taken.add(n.toLowerCase());
        return n;
    }
}
