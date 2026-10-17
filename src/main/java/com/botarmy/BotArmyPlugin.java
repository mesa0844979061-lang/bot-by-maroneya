package com.botarmy;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class BotArmyPlugin extends JavaPlugin {
    private BotManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("ต้องติดตั้ง Citizens ก่อน! https://ci.citizensnpcs.co/");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        manager = new BotManager(this);
        manager.start();
        getServer().getPluginManager().registerEvents(new BotListener(this, manager), this);
        BotCommand cmd = new BotCommand(this, manager);
        getCommand("bot").setExecutor(cmd);
        getCommand("bot").setTabCompleter(cmd);
        getCommand("debot").setExecutor(cmd);
        getLogger().info("Bot by maroneya พร้อมใช้งาน");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    void msg(CommandSender s, String legacy) {
        s.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&8[&cBot by maroneya&8] &r" + legacy));
    }
}
