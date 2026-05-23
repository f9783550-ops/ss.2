package pl.arenaplugin.windwand;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class WandMechanic implements Listener {

    private final WindWandPlugin plugin;
    private final Random random = new Random();

    // Mapy pomocnicze dla mechanik
    private final Map<UUID, Player> grabbedPlayers = new HashMap<>(); // M1
    private final Map<UUID, Float> lastYaw = new HashMap<>(); // M1
    private final Map<UUID, Double> accumulatedForce = new HashMap<>(); // M1
    private final Map<UUID, Location> frozenPlayers = new HashMap<>(); // M3
    private final Set<UUID> lavaMasters = new HashSet<>(); // M4
    private final Map<UUID, List<Block>> activeLavaPlatforms = new HashMap<>(); // M4
    private final Set<UUID> speedMasters = new HashSet<>(); // M5
    private final Set<UUID> enderCursed = new HashSet<>(); // M6

    public WandMechanic(WindWandPlugin plugin) {
        this.plugin = plugin;
        startGlobalTicker();
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.getType() != Material.WIND_CHARGE) return;

        event.setCancelled(true); // Wyłączamy bazowy knockback i hity

        // Losowanie mechaniki 1 z 6
        int mechanic = random.nextInt(6) + 1;

        switch (mechanic) {
            case 1 -> activatePortalGun(attacker, target);
            case 2 -> activateSlownessBall(attacker, target);
            case 3 -> activateImprisonment(target);
            case 4 -> activateLavaMaster(attacker);
            case 5 -> activateSpeedMaster(attacker);
            case 6 -> activateEnderCurse(target);
        }
    }

    // ==========================================
    // MECHANIKA 1: PORTAL GUN
    // ==========================================
    private void activatePortalGun(Player attacker, Player target) {
        grabbedPlayers.put(attacker.getUniqueId(), target);
        lastYaw.put(attacker.getUniqueId(), attacker.getLocation().getYaw());
        accumulatedForce.put(attacker.getUniqueId(), 0.0);

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
        attacker.sendMessage("§e[Różdżka] §aWylosowano: Lewitacja! Przytrzymaj SHIFT i kręć się!");
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1f, 1.5f);

        // Limit 10 sekund trzymania
        new BukkitRunnable() {
            @Override
            public void run() {
                if (grabbedPlayers.containsKey(attacker.getUniqueId()) && grabbedPlayers.get(attacker.getUniqueId()).equals(target)) {
                    grabbedPlayers.remove(attacker.getUniqueId());
                    lastYaw.remove(attacker.getUniqueId());
                    accumulatedForce.remove(attacker.getUniqueId());
                    attacker.sendMessage("§e[Różdżka] §cCzas trwania lewitacji minął!");
                }
            }
        }.runTaskLater(plugin, 200L);
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        Player attacker = event.getPlayer();
        if (!event.isSneaking() && grabbedPlayers.containsKey(attacker.getUniqueId())) {
            Player target = grabbedPlayers.remove(attacker.getUniqueId());
            double force = accumulatedForce.remove(attacker.getUniqueId());
            lastYaw.remove(attacker.getUniqueId());

            if (target != null && target.isOnline()) {
                double finalLaunchForce = 1.2 + force;
                Vector launchDirection = attacker.getLocation().getDirection().multiply(finalLaunchForce);
                target.setVelocity(launchDirection);
                
                attacker.sendMessage("§e[Różdżka] §cWystrzał! Pęd: " + String.format("%.2f", finalLaunchForce));
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 10f);
            }
        }
    }

    // ==========================================
    // MECHANIKA 2: KULA SPOWOLNIENIA
    // ==========================================
    private void activateSlownessBall(Player attacker, Player target) {
        attacker.sendMessage("§e[Różdżka] §aWylosowano: Kula Spowolnienia!");
        Vector knockback = target.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(1.5).setY(0.4);
        target.setVelocity(knockback);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 19, false, true)); // Slowness 20 na 6s
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
    }

    // ==========================================
    // MECHANIKA 3: UWIEZIENIE
    // ==========================================
    private void activateImprisonment(Player target) {
        UUID uuid = target.getUniqueId();
        frozenPlayers.put(uuid, target.getLocation());
        target.sendMessage("§cZostałeś uwięziony na 2 sekundy!");

        new BukkitRunnable() {
            @Override
            public void run() {
                frozenPlayers.remove(uuid);
                target.sendMessage("§aMożesz już się ruszać!");
            }
        }.runTaskLater(plugin, 40L); // 2 sekundy = 40 ticków
    }

    // ==========================================
    // MECHANIKA 4: MISTRZ LAWY
    // ==========================================
    private void activateLavaMaster(Player attacker) {
        attacker.sendTitle("§aZOSTAŁEŚ MISTRZEM LAWY", "§7Możesz chodzić po lawie!", 10, 40, 10);
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 400, 0, false, false));
        lavaMasters.add(attacker.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                lavaMasters.remove(attacker.getUniqueId());
                attacker.sendMessage("§cEfekt Mistrza Lawy dobiegł końca!");
                // Czyszczenie pozostałości platform
                if (activeLavaPlatforms.containsKey(attacker.getUniqueId())) {
                    for (Block b : activeLavaPlatforms.remove(attacker.getUniqueId())) {
                        attacker.sendBlockChange(b.getLocation(), b.getBlockData());
                    }
                }
            }
        }.runTaskLater(plugin, 400L); // 20 sekund
    }

    // ==========================================
    // MECHANIKA 5: MISTRZ SZYBKOŚCI (MIGOTANIE)
    // ==========================================
    private void activateSpeedMaster(Player attacker) {
        attacker.sendMessage("§e[Różdżka] §aWylosowano: Mistrz Szybkości!");
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 3, false, false)); // Speed 4
        speedMasters.add(attacker.getUniqueId());

        // Licznik migotania trwający 20 sekund (co 2 sekundy przełączenie)
        new BukkitRunnable() {
            int elapsed = 0;
            boolean invisible = false;

            @Override
            public void run() {
                if (elapsed >= 10 || !attacker.isOnline() || !speedMasters.contains(attacker.getUniqueId())) {
                    speedMasters.remove(attacker.getUniqueId());
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showPlayer(plugin, attacker);
                    }
                    cancel();
                    return;
                }

                invisible = !invisible;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.equals(attacker)) continue;
                    if (invisible) {
                        p.hidePlayer(plugin, attacker); // Chowa gracza wraz ze zbroją
                    } else {
                        p.showPlayer(plugin, attacker);
                    }
                }
                
                if (invisible) attacker.sendMessage("§7[Niewidzialność] Jesteś ukryty!");
                else attacker.sendMessage("§7[Niewidzialność] Jesteś widoczny!");

                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 40L); // Co 40 ticków = 2 sekundy
    }

    // ==========================================
    // MECHANIKA 6: KLĄTWA ENDERA
    // ==========================================
    private void activateEnderCurse(Player target) {
        target.sendMessage("§e[Różdżka] §cZostałeś przeklęty przez Endera! Każdy krok Cię teleportuje!");
        enderCursed.add(target.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                enderCursed.remove(target.getUniqueId());
                target.sendMessage("§aKlątwa Endera minęła.");
            }
        }.runTaskLater(plugin, 400L); // 20 sekund
    }

    // ==========================================
    // PROCESY ZDARZEŃ W CZASIE RZECZYWISTYM (TICKER)
    // ==========================================
    private void startGlobalTicker() {
        new BukkitRunnable() {
            double particleAngle = 0;

            @Override
            public void run() {
                particleAngle += 0.2;

                // Ticker dla Mechaniki 1 (Portal Gun) i Mechaniki 3 (Uwięzienie Cząsteczki)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID uuid = p.getUniqueId();

                    // M1: Trzymanie gracza
                    if (grabbedPlayers.containsKey(uuid)) {
                        Player target = grabbedPlayers.get(uuid);
                        if (p.isSneaking() && target != null && target.isOnline()) {
                            Vector lookDirection = p.getEyeLocation().getDirection().normalize();
                            Location targetLoc = p.getEyeLocation().add(lookDirection.multiply(4));
                            target.teleport(targetLoc);

                            // Liczenie obrotów dla pędu
                            float currentYaw = p.getLocation().getYaw();
                            float prevYaw = lastYaw.getOrDefault(uuid, currentYaw);
                            float yawChange = Math.abs(currentYaw - prevYaw);
                            if (yawChange > 180) yawChange = 360 - yawChange;

                            double force = accumulatedForce.getOrDefault(uuid, 0.0);
                            if (force < 3.5) {
                                accumulatedForce.put(uuid, force + (yawChange * 0.04));
                            }
                            lastYaw.put(uuid, currentYaw);
                        }
                    }

                    // M3: Cząsteczki uwięzienia wokół zamrożonych
                    if (frozenPlayers.containsKey(uuid)) {
                        Location loc = p.getLocation();
                        double x = 2 * Math.cos(particleAngle);
                        double z = 2 * Math.sin(particleAngle);
                        loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.getX() + x, loc.getY() + 0.5, loc.getZ() + z, 1, 0, 0, 0, 0);
                    }

                    // M4: Chodzenie po lawie za pomocą pakietów fake blocków
                    if (lavaMasters.contains(uuid)) {
                        Location loc = p.getLocation().subtract(0, 1, 0);
                        List<Block> currentBlocks = new ArrayList<>();
                        
                        // Usuwamy stare fake bloki dla tego gracza zanim zrobimy nowe
                        if (activeLavaPlatforms.containsKey(uuid)) {
                            for (Block b : activeLavaPlatforms.get(uuid)) {
                                p.sendBlockChange(b.getLocation(), b.getBlockData());
                            }
                        }

                        // Generowanie platformy 3x3 pod stopami z barier
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                Block b = loc.clone().add(x, 0, z).getBlock();
                                if (b.getType() == Material.LAVA) {
                                    p.sendBlockChange(b.getLocation(), Material.BARRIER.createBlockData());
                                    currentBlocks.add(b);
                                }
                            }
                        }
                        activeLavaPlatforms.put(uuid, currentBlocks);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // M3: Blokada ruchu przy uwięzieniu
        if (frozenPlayers.containsKey(uuid)) {
            Location from = frozenPlayers.get(uuid);
            if (event.getTo().getX() != from.getX() || event.getTo().getZ() != from.getZ()) {
                event.setTo(from);
            }
            return;
        }

        // M6: Klątwa Endera (teleportacja przy zmianie bloku)
        if (enderCursed.contains(uuid) && event.hasChangedBlock()) {
            Location loc = player.getLocation();
            double randX = loc.getX() + (random.nextDouble() * 12 - 6);
            double randZ = loc.getZ() + (random.nextDouble() * 12 - 6);
            Location targetLoc = new Location(loc.getWorld(), randX, loc.getY(), randZ, loc.getYaw(), loc.getPitch());
            
            // Szukanie bezpiecznej wysokości dla koordynatów
            Block highest = targetLoc.getWorld().getHighestBlockAt(targetLoc);
            targetLoc.setY(highest.getY() + 1);

            player.teleport(targetLoc);
            player.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            player.getWorld().spawnParticle(Particle.PORTAL, targetLoc, 15);
        }
    }
}
