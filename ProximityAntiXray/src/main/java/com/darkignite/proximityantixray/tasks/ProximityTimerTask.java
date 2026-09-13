package com.darkignite.proximityantixray.tasks;

import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;

import com.darkignite.proximityantixray.ProximityAntiXray;

public final class ProximityTimerTask extends TimerTask {
    private final ProximityAntiXray plugin;

    public ProximityTimerTask(ProximityAntiXray plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean timingsEnabled = plugin.isTimingsEnabled();
        long start = timingsEnabled ? System.currentTimeMillis() : 0L;

        try {
            plugin.getExecutorService().invokeAll(plugin.getPlayerData().values());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RejectedExecutionException e) {

        }

        if (timingsEnabled) {
            long stop = System.currentTimeMillis();
            plugin.getLogger().info((stop - start) + "ms per proximity check tick.");
        }
    }
}
