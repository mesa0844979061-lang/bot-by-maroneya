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

        if (a.length == 0) {
            plugin.msg(s, "&e/bot <easy|medium|high|max> [จำนวน] &7- เสกกองทัพบอท");
            plugin.msg(s, "&e/bot frozen &7- แช่/ปลดแช่บอท (ถือโทเทมสแตก 99)");
            plugin.msg(s, "&e/bot mode <auto|patrol|attack> &7- บังคับโหมดกองทัพ");
            plugin.msg(s, "&e/bot kill <ชื่อผู้เล่น> &7- สั่งบอทรุมผู้เล่น (เกิน 2 ตัว แบ่งครึ่งไปรุม)");
            plugin.msg(s, "&e/bot kill clear &7- ยกเลิกคำสั่งรุม");
            plugin.msg(s, "&e/debot &7- ล้างบอททั้งหมด");
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
                plugin.msg(s, "&cยังไม่มีบอท เสกก่อนด้วย /bot <ระดับ> [จำนวน]");
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

        Level level = Level.parse(first);
        if (level == null) {
            plugin.msg(s, "&cไม่รู้จักระดับ &f" + a[0] + " &7(easy / medium / high / max)");
            return true;
        }
        if (!(s instanceof Player p)) {
            plugin.msg(s, "&cต้องใช้ในเกม");
            return true;
        }
        int count = 5;
        if (a.length >= 2) {
            try {
                count = Integer.parseInt(a[1]);
            } catch (NumberFormatException ex) {
                plugin.msg(s, "&cจำนวนต้องเป็นตัวเลข");
                return true;
            }
        }
        int max = plugin.getConfig().getInt("max-total-bots", 100);
        int room = max - mgr.size();
        if (room <= 0) {
            plugin.msg(s, "&cบอทเต็มแล้ว (" + max + ") ใช้ /debot ก่อน");
            return true;
        }
        count = Math.max(1, Math.min(count, room));
        int made = mgr.spawn(p, level, count);
        plugin.msg(s, "&aเสกบอทระดับ &f" + level.id + " &aจำนวน &f" + made + " &aตัว");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("bot")) return out;
        if (a.length == 1) {
            for (String o : List.of("easy", "medium", "high", "max", "frozen", "mode", "kill"))
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (a.length == 2) {
            if (a[0].equalsIgnoreCase("kill")) {
                if ("clear".startsWith(a[1].toLowerCase(Locale.ROOT))) out.add("clear");
                for (Player pl : Bukkit.getOnlinePlayers())
                    if (pl.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(pl.getName());
            } else if (a[0].equalsIgnoreCase("mode")) {
                for (String o : List.of("auto", "patrol", "attack"))
                    if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(o);
            } else if (Level.parse(a[0]) != null) {
                out.addAll(List.of("5", "10", "20", "50"));
            }
        }
        return out;
    }
}
