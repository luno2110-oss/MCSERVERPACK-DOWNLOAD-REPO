package org.bukkit;

import org.bukkit.block.data.BlockData;

/** Ersatz für die Vorschau. */
public final class Bukkit {
    public static BlockData createBlockData(Material m) {
        return h3d.Mock.block(m);
    }

    public static Server getServer() {
        return h3d.Mock.server();
    }

    public static org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return h3d.Mock.server().getScheduler();
    }
}
