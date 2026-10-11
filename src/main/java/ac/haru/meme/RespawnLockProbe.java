package ac.haru.meme;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RespawnLockProbe extends JavaPlugin implements Listener {
    private final Map<UUID, Probe> probes = new ConcurrentHashMap<>();
    private PacketListenerAbstract packetListener;
    private List<Song> songs;

    @Override
    public void onEnable() {
        var definitions = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getResource("songs.yml"), "Missing songs.yml"),
                StandardCharsets.UTF_8));
        songs = definitions.getMapList("songs").stream()
                .map(song -> new Song((String) song.get("sound"),
                        ((Number) song.get("duration-ms")).longValue()))
                .toList();
        if (songs.isEmpty()) {
            getLogger().warning("songs.yml must contain at least one song.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        packetListener = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.CLIENT_STATUS) {
                    return;
                }
                var packet = new WrapperPlayClientClientStatus(event);
                if (packet.getAction() != WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN) {
                    return;
                }

                Probe probe = probes.get(event.getUser().getUUID());
                if (probe == null) {
                    return;
                }
                if (System.nanoTime() < probe.unlockAt || !probe.released) {
                    event.setCancelled(true);
                    probe.blocked.incrementAndGet();
                    return;
                }

                if (event.isCancelled()) {
                    getLogger().warning("[OTHER_CANCEL] player=" + probe.name);
                    return;
                }
                probe.allowed.incrementAndGet();
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::releaseExpired, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            getLogger().warning("[ABORT] player=" + player.getName()
                    + " reason=packetevents_user_missing");
            return;
        }
        Song song = songs.get(ThreadLocalRandom.current().nextInt(songs.size()));
        for (Song previous : songs) {
            player.stopSound(previous.sound(), SoundCategory.RECORDS);
        }
        Probe probe = new Probe(player.getName(), song);
        probes.put(player.getUniqueId(), probe);
        event.deathScreenMessageOverride(Component.text(
                "好消息，你需要听完这首歌才能复活"));
        player.playSound(player.getLocation(), song.sound(), SoundCategory.RECORDS, 1.0f, 1.0f);
    }

    private void releaseExpired() {
        long now = System.nanoTime();
        for (var entry : probes.entrySet()) {
            Probe probe = entry.getValue();
            if (probe.released || now < probe.unlockAt) {
                continue;
            }

            Player player = getServer().getPlayer(entry.getKey());
            if (player == null || !player.isDead()) {
                probes.remove(entry.getKey(), probe);
                getLogger().warning("[ABORT] player=" + probe.name
                        + " reason=not_online_or_not_dead");
                continue;
            }

            refreshDeathScreen(player);
            probe.released = true;
        }
    }

    private void refreshDeathScreen(Player player) {
        // The local player leaves the entity lookup after death; closing a screen uses it directly.
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerCloseWindow(0));
    }

    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        Probe probe = probes.remove(player.getUniqueId());
        if (probe == null) {
            return;
        }

        if (!probe.released || probe.allowed.get() == 0) {
            getLogger().warning("[UNEXPECTED_RESPAWN] player=" + probe.name
                    + " reason=no_allowed_request_after_refresh");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        probes.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2
                || (!args[0].equalsIgnoreCase("status") && !args[0].equalsIgnoreCase("rescue"))) {
            return false;
        }
        Player player = args.length == 2 ? getServer().getPlayerExact(args[1])
                : sender instanceof Player self ? self : null;
        if (player == null) {
            sender.sendMessage("请指定在线玩家：/respawnprobe " + args[0] + " <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("rescue")) {
            probes.remove(player.getUniqueId());
            if (player.isDead()) {
                player.spigot().respawn();
            }
            sender.sendMessage("已解除该玩家的验证状态并执行救援重生；这次测试作废。");
            return true;
        }

        Probe probe = probes.get(player.getUniqueId());
        if (probe == null) {
            sender.sendMessage(player.getName() + " 当前没有进行中的验证。");
        } else {
            sender.sendMessage(player.getName() + " dead=" + player.isDead()
                    + " sound=" + probe.song.sound()
                    + " remaining_ms=" + probe.remainingMillis()
                    + " refresh_sent=" + probe.released
                    + " blocked=" + probe.blocked.get()
                    + " allowed=" + probe.allowed.get());
        }
        return true;
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            for (UUID id : probes.keySet()) {
                Player player = getServer().getPlayer(id);
                if (player != null && player.isDead()) {
                    refreshDeathScreen(player);
                }
            }
        }
        probes.clear();
    }

    private record Song(String sound, long durationMillis) {
        private long waitNanos() {
            return TimeUnit.MILLISECONDS.toNanos(durationMillis) * 85 / 100;
        }
    }

    private static final class Probe {
        private final String name;
        private final Song song;
        private final long unlockAt;
        private final AtomicInteger blocked = new AtomicInteger();
        private final AtomicInteger allowed = new AtomicInteger();
        private volatile boolean released;

        private Probe(String name, Song song) {
            this.name = name;
            this.song = song;
            this.unlockAt = System.nanoTime() + song.waitNanos();
        }

        private long remainingMillis() {
            return Math.max(0, TimeUnit.NANOSECONDS.toMillis(unlockAt - System.nanoTime()));
        }
    }
}
