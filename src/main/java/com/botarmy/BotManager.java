package com.botarmy;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** จัดการกองทัพทั้งหมด: เสก, ลาดตระเวน, เลือกเป้า, จัดแถว */
public final class BotManager {

    public enum Mode { AUTO, PATROL, ATTACK }

    private final BotArmyPlugin plugin;
    private NPCRegistry registry;
    private final List<ArmyBot> bots = new ArrayList<>();
    private final Map<UUID, ArmyBot> byEntity = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private final Random rnd = new Random();

    private long now = 0;
    private Location home;
    private UUID owner;
    private Location patrolPoint;
    private long nextPatrolAt, pauseUntil;
    private List<Player> enemies = new ArrayList<>();
    private Player focus;
    private ArmyBot leader;
    private Mode mode = Mode.AUTO;
    private boolean frozenAll;
    private BukkitTask task;

    BotManager(BotArmyPlugin plugin) {
        this.plugin = plugin;
    }

    BotArmyPlugin plugin() { return plugin; }
    long now() { return now; }
    ArmyBot leader() { return leader; }
    Location home() { return home; }
    Mode mode() { return mode; }
    boolean isFrozen() { return frozenAll; }
    int size() { return bots.size(); }

    void start() {
        registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    void shutdown() {
        if (task != null) task.cancel();
        clear();
        if (registry != null) registry.deregisterAll();
    }

    ArmyBot get(Entity e) {
        return e == null ? null : byEntity.get(e.getUniqueId());
    }

    // ------------------------------------------------------------ commands

    int spawn(Player owner, Level level, int count) {
        this.owner = owner.getUniqueId();
        home = owner.getLocation().clone();
        patrolPoint = null;
        float yaw = home.getYaw();
        Vector fwd = fwd(yaw), right = right(yaw);
        int cols = Math.max(1, plugin.getConfig().getInt("formation-columns", 5));
        boolean showTab = plugin.getConfig().getBoolean("show-in-tablist", false);
        List<String> skins = plugin.getConfig().getStringList("skins");
        int made = 0;

        for (int i = 0; i < count; i++) {
            int row = i / cols, col = i % cols;
            double r = (col - (cols - 1) / 2.0) * 1.9;
            double f = 4 + row * 2.0;
            Location loc = home.clone().add(fwd.clone().multiply(f)).add(right.clone().multiply(r));
            loc = safeGround(loc);
            loc.setYaw(yaw);
            loc.setPitch(0);

            String name = BotNames.generate(rnd, usedNames);
            NPC npc = registry.createNPC(EntityType.PLAYER, name);
            npc.setProtected(false);
            npc.data().set(NPC.Metadata.REMOVE_FROM_PLAYERLIST, !showTab);
            npc.getNavigator().getDefaultParameters()
                    .range(150f)
                    .speedModifier(level.spec.speed)
                    .distanceMargin(1.8);
            if (!skins.isEmpty()) {
                try {
                    npc.getOrAddTrait(SkinTrait.class).setSkinName(skins.get(rnd.nextInt(skins.size())));
                } catch (Throwable t) {
                    plugin.getLogger().warning("skin error: " + t.getMessage());
                }
            }

            ArmyBot b = new ArmyBot(this, npc, name, level);
            if (!npc.spawn(loc)) {
                npc.destroy();
                continue;
            }
            b.hadSpawned = true;
            b.frozen = frozenAll;
            bots.add(b);
            if (b.uuid() != null) byEntity.put(b.uuid(), b);
            made++;
            announce("multiplayer.player.joined", name, made - 1);
        }
        return made;
    }

    int clear() {
        int n = bots.size();
        int i = 0;
        for (ArmyBot b : bots) {
            announce("multiplayer.player.left", b.name, i++);
            b.destroy();
        }
        bots.clear();
        byEntity.clear();
        usedNames.clear();
        leader = null;
        frozenAll = false;
        return n;
    }

    /** แจ้ง "xxx joined the game" / "xxx left the game" เหมือนผู้เล่นจริง (ทยอยส่งทีละตัว) */
    private void announce(String key, String name, int index) {
        if (!plugin.isEnabled() || !plugin.getConfig().getBoolean("broadcast-join-leave", true)) return;
        Component msg = Component.translatable(key, NamedTextColor.YELLOW, Component.text(name));
        long delay = Math.min(index, 40) * 3L;
        if (delay == 0) {
            Bukkit.broadcast(msg);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.broadcast(msg), delay);
        }
    }

    boolean toggleFrozen() {
        frozenAll = !frozenAll;
        for (ArmyBot b : bots) b.setFrozen(frozenAll);
        return frozenAll;
    }

    void setMode(Mode m) { this.mode = m; }

    // ------------------------------------------------------------ main loop

    private void tick() {
        now += 2;
        byEntity.clear();
        int respawn = plugin.getConfig().getInt("respawn-seconds", 0) * 20;

        for (Iterator<ArmyBot> it = bots.iterator(); it.hasNext(); ) {
            ArmyBot b = it.next();
            if (b.isSpawned()) {
                b.hadSpawned = true;
                b.respawnAt = 0;
                byEntity.put(b.uuid(), b);
            } else if (b.hadSpawned) {
                if (respawn > 0 && home != null) {
                    if (b.respawnAt == 0) b.respawnAt = now + respawn;
                    if (now >= b.respawnAt) {
                        Location base = (leader != null && leader.player() != null) ? leader.player().getLocation() : home;
                        if (b.respawn(safeGround(base))) {
                            byEntity.put(b.uuid(), b);
                        } else {
                            b.respawnAt = now + 100;
                        }
                    }
                } else {
                    b.destroy();
                    it.remove();
                }
            }
        }
        if (bots.isEmpty()) {
            leader = null;
            return;
        }

        collectEnemies();
        electLeader();
        computeFocus();

        for (ArmyBot b : new ArrayList<>(bots)) {
            try {
                b.tick(now);
            } catch (Throwable t) {
                if (now % 200 == 0) plugin.getLogger().warning("bot " + b.name + " error: " + t);
            }
        }
    }

    private void collectEnemies() {
        boolean targetOwner = plugin.getConfig().getBoolean("target-owner", true);
        List<Player> list = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            GameMode gm = pl.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;
            if (pl.isDead() || !pl.isValid()) continue;
            if (pl.hasPermission("botarmy.ignore")) continue;
            if (!targetOwner && pl.getUniqueId().equals(owner)) continue;
            if (byEntity.containsKey(pl.getUniqueId())) continue;
            list.add(pl);
        }
        enemies = list;
    }

    private void electLeader() {
        leader = null;
        int idx = 0;
        for (ArmyBot b : bots) {
            if (!b.isSpawned()) continue;
            if (leader == null && !b.frozen) {
                leader = b;
                b.slot = 0;
            } else {
                b.slot = ++idx;
            }
        }
    }

    private void computeFocus() {
        focus = null;
        if (mode == Mode.PATROL) return;
        double range = plugin.getConfig().getDouble("detect-range", 45);
        double best = mode == Mode.ATTACK ? Double.MAX_VALUE : range * range;
        for (ArmyBot b : bots) {
            Player p = b.player();
            if (p == null || b.frozen) continue;
            Location bl = p.getLocation();
            for (Player e : enemies) {
                if (e.getWorld() != bl.getWorld()) continue;
                double d = e.getLocation().distanceSquared(bl);
                if (d < best) {
                    best = d;
                    focus = e;
                }
            }
        }
    }

    /** เลือกเป้าให้บอท: โฟกัสของทัพ แต่ถ้ามีคนอยู่ประชิดจะสู้คนนั้นก่อน */
    Player pickTarget(ArmyBot b, Player p) {
        if (b.frozen) return null;
        if (mode == Mode.PATROL) {
            Player la = b.lastAttacker();
            return (la != null && enemies.contains(la)) ? la : null;
        }
        Location bl = p.getLocation();
        Player nearest = null;
        double nd = Double.MAX_VALUE;
        for (Player e : enemies) {
            if (e.getWorld() != bl.getWorld()) continue;
            double d = e.getLocation().distanceSquared(bl);
            if (d < nd) {
                nd = d;
                nearest = e;
            }
        }
        if (focus != null && focus.getWorld() == bl.getWorld()
                && (nearest == null || Math.sqrt(nd) > 10)) {
            return focus;
        }
        return nearest != null && (mode == Mode.ATTACK
                || Math.sqrt(nd) <= plugin.getConfig().getDouble("detect-range", 45)) ? nearest : null;
    }

    // ------------------------------------------------------------ patrol / formation

    Location patrolPoint(Location from) {
        double radius = plugin.getConfig().getDouble("patrol-radius", 25);
        boolean pick = patrolPoint == null || patrolPoint.getWorld() != from.getWorld();
        if (!pick) {
            if (from.distanceSquared(patrolPoint) < 9) {
                if (pauseUntil == 0) pauseUntil = now + 40 + rnd.nextInt(60);
                else if (now >= pauseUntil) pick = true;
            } else if (now >= nextPatrolAt) {
                pick = true;
            }
        }
        if (pick) {
            Location base = home != null && home.getWorld() == from.getWorld() ? home : from;
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = 8 + rnd.nextDouble() * Math.max(1, radius - 8);
            Location l = base.clone().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            l.setY(from.getY());
            patrolPoint = safeGround(l);
            nextPatrolAt = now + 400 + rnd.nextInt(300);
            pauseUntil = 0;
        }
        return patrolPoint;
    }

    Location formationPoint(ArmyBot b) {
        Player lp = leader == null ? null : leader.player();
        if (lp == null) return home;
        Location ll = lp.getLocation();
        int cols = Math.max(1, plugin.getConfig().getInt("formation-columns", 5));
        int idx = Math.max(0, b.slot - 1);
        int row = idx / cols + 1, col = idx % cols;
        double r = (col - (cols - 1) / 2.0) * 2.0;
        double back = row * 2.2;
        float yaw = ll.getYaw();
        return ll.clone().add(right(yaw).multiply(r)).subtract(fwd(yaw).multiply(back));
    }

    private static Vector fwd(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vector right(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.cos(rad), 0, -Math.sin(rad));
    }

    Location safeGround(Location l) {
        Location c = l.clone();
        World w = c.getWorld();
        if (w == null) return c;
        int x = c.getBlockX(), z = c.getBlockZ(), y = c.getBlockY();
        for (int i = 0; i < 8 && w.getBlockAt(x, y, z).getType().isSolid(); i++) y++;
        for (int i = 0; i < 10 && y > w.getMinHeight() && !w.getBlockAt(x, y - 1, z).getType().isSolid(); i++) y--;
        c.setY(y);
        return c;
    }
}
