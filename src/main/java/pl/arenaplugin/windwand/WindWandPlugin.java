package pl.arenaplugin.windwand;

import org.bukkit.plugin.java.JavaPlugin;

public final class WindWandPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Rejestracja głównego systemu mechanik różdżki
        getServer().getPluginManager().registerEvents(new WandMechanic(this), this);
        getLogger().info("WindWandPlugin aktywowany! Gotowy na tryb Abstrakcja!");
    }

    @Override
    public void onDisable() {
        getLogger().info("WindWandPlugin został wyłączony.");
    }
}
