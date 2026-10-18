package com.botarmy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotCommand implements CommandExecutor, TabCompleter {
    private final BotArmyPlugin plugin;
    private final BotManager mgr;

    BotCommand(BotArmyPlugin plugin, BotManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("debot")) {
            int n = mgr.clear();
            plugin.msg(s, "&aล้างบอทแล้ว &7(" + n + " ตัว)");
            return true;
        }

        if (a.length == 0 || a[0].equalsIgnoreCase("menu")) {
            if (s instanceof Player p) {
                new BotMenu(p.getUniqueId()).open(p);
            } else {
                help(s);
            }
            return true;
        }

        String first = a[0].toLowerCase(Locale.ROOT);

        if (first.equals("frozen") || first.equals("freeze") || first.equals("แช่")) {
            boolean f = mgr.toggleFrozen();
            plugin.msg(s, f ? "&b❄ แช่บอททั้งหมดแล้ว (ถือโทเทมสแตก 99)" : "&a🔥 ปลดแช่บอทแล้ว");
            return true;
        }

        if (first.equals("kill") || first.equals("รุม")) {
            if (a.length < 2) {
                plugin.msg(s, "&cใช้: /bot kill <ชื่อผู้เล่น>  (หรือ /bot kill clear)");
                return true;
            }
            String arg = a[1];
            if (arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("stop")) {
                mgr.clearKill();
                plugin.msg(s, "&aยกเลิกคำสั่งรุมแล้ว");
                return true;
            }
            Player t = Bukkit.getPlayerExact(arg);
            if (t == null) t = Bukkit.getPlayer(arg);
            if (t == null) {
                plugin.msg(s, "&cไม่พบผู้เล่น &f" + arg + " &7(ต้องออนไลน์อยู่)");
                return true;
            }
            if (mgr.get(t) != null) {
                plugin.msg(s, "&cนั่นคือบอท ใช้กับผู้เล่นจริงเท่านั้น");
                return true;
            }
            if (mgr.size() == 0) {
                plugin.msg(s, "&cยังไม่มีบอท เสกก่อนด้วย /bot");
                return true;
            }
            int n = mgr.setKillTarget(t);
            if (n == 0) {
                plugin.msg(s, "&cไม่มีบอทที่พร้อมรุม (อาจถูกแช่อยู่)");
            } else {
                plugin.msg(s, "&a☠ บอท &f" + n + "&a/" + mgr.activeCount() + " ตัว กำลังรุม &f" + t.getName()
                        + (mgr.activeCount() > 2 ? " &7(ที่เหลือทำงานตามปกติ)" : ""));
            }
            return true;
        }

        if (first.equals("macex")) {
            if (!(s instanceof Player p)) {
                plugin.msg(s, "&cใช้ในเกมเท่านั้น");
                return true;
            }
            int max = plugin.getConfig().getInt("max-total-bots", 100);
            if (mgr.size() >= max) {
                plugin.msg(s, "&cบอทเต็มแล้ว (" + max + ") ใช้ /debot ก่อน");
                return true;
            }
            int made = mgr.spawn(p, KitType.MACEPVP, Spec.macex(), 1);
            plugin.msg(s, made > 0 ? "&6⚡ เสกบอท MACE ฝีมือระดับโลก 1 ตัวแล้ว" : "&cเสกไม่สำเร็จ");
            return true;
        }

        if (first.equals("mode")) {
            if (a.length < 2) {
                plugin.msg(s, "&cใช้: /bot mode <auto|patrol|attack> &7(ตอนนี้: " + mgr.mode().name().toLowerCase() + ")");
                return true;
            }
            switch (a[1].toLowerCase(Locale.ROOT)) {
                case "auto" -> mgr.setMode(BotManager.Mode.AUTO);
                case "patrol" -> mgr.setMode(BotManager.Mode.PATROL);
                case "attack" -> mgr.setMode(BotManager.Mode.ATTACK);
                default -> {
                    plugin.msg(s, "&cโหมดไม่ถูกต้อง");
                    return true;
                }
            }
            plugin.msg(s, "&aตั้งโหมดเป็น &f" + mgr.mode().name().toLowerCase());
            return true;
        }

        help(s);
        return true;
    }

    private void help(CommandSender s) {
        plugin.msg(s, "&e/bot &7- เปิดเมนูเลือกชุดบอท (CPVP / CUTPVP / SMP / FFA / UHC / AXE PVP / MACE PVP)");
        plugin.msg(s, "&e/bot frozen &7- แช่/ปลดแช่บอท (ถือโทเทมสแตก 99)");
        plugin.msg(s, "&e/bot macex &7- เสกบอทค้อน MACE ฝีมือระดับโลก 1 ตัว");
        plugin.msg(s, "&e/bot mode <auto|patrol|attack> &7- บังคับโหมดกองทัพ");
        plugin.msg(s, "&e/bot kill <ชื่อผู้เล่น> &7- สั่งบอทรุมผู้เล่น (เกิน 2 ตัว แบ่งครึ่งไปรุม)");
        plugin.msg(s, "&e/bot kill clear &7- ยกเลิกคำสั่งรุม");
        plugin.msg(s, "&e/debot &7- ล้างบอททั้งหมด");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("bot")) return out;
        if (a.length == 1) {
            for (String o : List.of("menu", "help", "frozen", "mode", "kill", "macex"))
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (a.length == 2) {
            if (a[0].equalsIgnoreCase("kill")) {
                if ("clear".startsWith(a[1].toLowerCase(Locale.ROOT))) out.add("clear");
                for (Player pl : Bukkit.getOnlinePlayers())
                    if (pl.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(pl.getName());
            } else if (a[0].equalsIgnoreCase("mode")) {
                for (String o : List.of("auto", "patrol", "attack"))
                    if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(o);
            }
        }
        return out;
    }
}
