package arcane.classes.plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class ClassSelector extends JavaPlugin implements Listener {

    private final List<String> classNames = new ArrayList<>();
    private final Map<String, Location> classLocations = new HashMap<>();
    private final Map<UUID, Integer> playerClassIndex = new HashMap<>();
    private final Map<UUID, Long> lastSwitchTime = new HashMap<>();
    private final Set<UUID> previewPlayers = new HashSet<>();
    private final Map<UUID, ArmorStand> cameraStands = new HashMap<>();
    private final Map<UUID, Location> lockedLocations = new HashMap<>();

    private String defaultClass;
    private long cooldownMillis;
    private int previewSlot;
    private String previewSound;
    private String targetServer;
    private String screenColor;
    private int fadeIn, stay, fadeOut;
    private boolean freezeScreen;
    private int kickDelay;
    private String kickMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Bukkit.getPluginManager().registerEvents(this, this);
        setupPacketListeners();
        startWatcher();
        Objects.requireNonNull(getCommand("adminclass")).setExecutor(this);
    }

    private void loadSettings() {
        FileConfiguration config = getConfig();

        classNames.clear();
        classLocations.clear();
        for (var section : config.getMapList("classes")) {
            String name = (String) section.get("name");
            classNames.add(name);
            Map<String, Object> loc = (Map<String, Object>) section.get("location");
            World world = Bukkit.getWorld((String) loc.get("world"));
            Location l = new Location(world,
                    getDouble(loc, "x"),
                    getDouble(loc, "y"),
                    getDouble(loc, "z"),
                    getFloat(loc, "yaw"),
                    getFloat(loc, "pitch"));
            classLocations.put(name, l);
        }

        defaultClass = config.getString("default-class", classNames.get(0));
        cooldownMillis = config.getLong("cooldown-ms", 1000);
        previewSlot = config.getInt("preview-slot", 5);
        previewSound = config.getString("preview-sound", "rpg:rpg.classsong");
        targetServer = config.getString("target-server", "RPG");

        screenColor = config.getString("screeneffect.color", "BLACK");
        fadeIn = config.getInt("screeneffect.fade-in", 10);
        stay = config.getInt("screeneffect.stay", 20);
        fadeOut = config.getInt("screeneffect.fade-out", 10);
        freezeScreen = config.getBoolean("screeneffect.freeze", true);

        kickDelay = config.getInt("kick-delay-ticks", 120);
        kickMessage = config.getString("kick-message", "Server is full!");
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    private float getFloat(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o instanceof Number ? ((Number) o).floatValue() : 0.0f;
    }

    private void setupPacketListeners() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (previewPlayers.contains(event.getPlayer().getUniqueId())) {
                            event.setCancelled(true);
                        }
                    }
                }
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("adminclass") && sender instanceof Player player) {
            endPreview(player);
            player.stopSound(SoundCategory.MASTER);
            for (String className : classNames) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mythichud layout " + player.getName() + " reset " + className.toLowerCase() + "_hud-layout");
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "screeneffect fullscreen " + screenColor + " " + fadeIn + " " + stay + " " + fadeOut + (freezeScreen ? " freeze " : " ") + joined.getName());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(joined)) {
                other.hidePlayer(this, joined);
                joined.hidePlayer(this, other);
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> openSelector(joined), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endPreview(event.getPlayer());
    }

    private void openSelector(Player player) {
        UUID id = player.getUniqueId();
        previewPlayers.add(id);
        player.playSound(player.getLocation(), previewSound, SoundCategory.MASTER, 1f, 1f);

        player.setGameMode(GameMode.SURVIVAL);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));

        playerClassIndex.put(id, 0);
        lastSwitchTime.put(id, System.currentTimeMillis());
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(previewSlot);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "mythichud layout " + player.getName() + " add " + defaultClass.toLowerCase() + "_hud-layout -s");

        runClassPreview(player, 0, null);
    }

    private void runClassPreview(Player player, int newIndex, Integer oldIndex) {
        String newClass = classNames.get(newIndex);
        Location loc = classLocations.get(newClass);
        UUID id = player.getUniqueId();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "screeneffect fullscreen " + screenColor + " " + fadeIn + " " + stay + " " + fadeOut + (freezeScreen ? " freeze " : " ") + player.getName());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "tab scoreboard show " + newClass + " " + player.getName());
            if (oldIndex != null) {
                String oldClass = classNames.get(oldIndex);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mythichud layout " + player.getName() + " reset " + oldClass.toLowerCase() + "_hud-layout");
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mythichud layout " + player.getName() + " add " + newClass.toLowerCase() + "_hud-layout -s");

            player.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            lockedLocations.put(id, loc.clone().add(0, 1.5, 0));

            Bukkit.getScheduler().runTaskLater(this, () -> {
                ArmorStand old = cameraStands.remove(id);
                if (old != null && !old.isDead()) old.remove();
                if (loc.getWorld() == null) return;

                Location dummyLocation = loc.clone().add(0, 1.5, 0);
                loc.getWorld().loadChunk(dummyLocation.getChunk());
                ArmorStand dummy = loc.getWorld().spawn(dummyLocation, ArmorStand.class, a -> {
                    a.setInvisible(true);
                    a.setMarker(true);
                    a.setInvulnerable(true);
                    a.setGravity(false);
                    a.setCollidable(false);
                });

                cameraStands.put(id, dummy);

                try {
                    PacketContainer cameraPacket = new PacketContainer(PacketType.Play.Server.CAMERA);
                    cameraPacket.getIntegers().write(0, dummy.getEntityId());
                    ProtocolLibrary.getProtocolManager().sendServerPacket(player, cameraPacket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 5L);
        }, 20L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!previewPlayers.contains(id)) return;

        event.setCancelled(true);
        if (event.getAction().toString().contains("LEFT")) {
            int index = playerClassIndex.getOrDefault(id, 0);
            String className = classNames.get(index);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mmocore admin class " + player.getName() + " " + className);

            Bukkit.getScheduler().runTaskLater(this, () -> sendToServer(player, targetServer), 20L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) player.kickPlayer(kickMessage);
            }, kickDelay);
        }
    }

    private void sendToServer(Player player, String serverName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(out)) {
            data.writeUTF("Connect");
            data.writeUTF(serverName);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (previewPlayers.contains(id) && lockedLocations.containsKey(id)) {
            event.setTo(lockedLocations.get(id));
        }
    }

    private void startWatcher() {
        new BukkitRunnable() {
            private final Map<UUID, Integer> lastSlot = new HashMap<>();
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    if (!previewPlayers.contains(id)) continue;

                    int currentSlot = player.getInventory().getHeldItemSlot();
                    int last = lastSlot.getOrDefault(id, previewSlot);
                    if (currentSlot != previewSlot && currentSlot != last) {
                        long now = System.currentTimeMillis();
                        if (now - lastSwitchTime.getOrDefault(id, 0L) < cooldownMillis) {
                            player.getInventory().setHeldItemSlot(previewSlot);
                            continue;
                        }
                        int index = playerClassIndex.getOrDefault(id, 0);
                        int oldIndex = index;
                        if (currentSlot < previewSlot) {
                            index = (index - 1 + classNames.size()) % classNames.size();
                        } else if (currentSlot > previewSlot) {
                            index = (index + 1) % classNames.size();
                        }
                        playerClassIndex.put(id, index);
                        lastSwitchTime.put(id, now);
                        runClassPreview(player, index, oldIndex);
                        player.getInventory().setHeldItemSlot(previewSlot);
                    }
                    lastSlot.put(id, currentSlot);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void endPreview(Player player) {
        UUID id = player.getUniqueId();
        previewPlayers.remove(id);
        lockedLocations.remove(id);

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setInvulnerable(false);

        ArmorStand dummy = cameraStands.remove(id);
        if (dummy != null && !dummy.isDead()) dummy.remove();

        try {
            PacketContainer resetCamera = new PacketContainer(PacketType.Play.Server.CAMERA);
            resetCamera.getIntegers().write(0, player.getEntityId());
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, resetCamera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
