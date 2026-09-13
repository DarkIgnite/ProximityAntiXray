package com.darkignite.proximityantixray.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.darkignite.proximityantixray.ProximityAntiXray;
import com.darkignite.proximityantixray.data.PlayerData;
import com.darkignite.proximityantixray.data.VectorialLocation;
import com.darkignite.proximityantixray.tasks.ProximityCallable;
import com.darkignite.proximityantixray.tasks.UpdateBukkitRunnable;

public final class PlayerListener implements Listener {
    private final ProximityAntiXray plugin;

    public PlayerListener(ProximityAntiXray plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!plugin.validatePlayer(player)) {
            return;
        }

        PlayerData playerData = new PlayerData(new VectorialLocation[] { new VectorialLocation(player.getEyeLocation()) });
        playerData.setCallable(new ProximityCallable(plugin, playerData));
        plugin.getPlayerData().put(player.getUniqueId(), playerData);

        if (plugin.isFolia()) {
            player.getScheduler().runAtFixedRate(plugin, new UpdateBukkitRunnable(plugin, player), null, 1L, plugin.getUpdateTicks());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerData().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getPlayerData().get(player.getUniqueId());

        if (!plugin.validatePlayerData(player, playerData, "onPlayerMove")) {
            return;
        }

        Location to = event.getTo();

        if (to.getWorld().equals(playerData.getLocations()[0].getWorld())) {
            VectorialLocation location = new VectorialLocation(to);
            Vector vector = location.getVector();
            vector.setY(vector.getY() + player.getEyeHeight());
            playerData.setLocations(new VectorialLocation[] { location });
        }
    }
}
