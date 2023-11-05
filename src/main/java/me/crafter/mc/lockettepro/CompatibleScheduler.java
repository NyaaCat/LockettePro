package me.crafter.mc.lockettepro;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

public class CompatibleScheduler {
    private static final boolean isSpigot = ((Supplier<Boolean>) () -> {
        try {
            Class.forName("org.bukkit.Server.Spigot");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }).get();

    private static final boolean isFolia = ((Supplier<Boolean>) () -> {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }).get();


    /*
     Folia split the whole world tick into different main threads by its location
     So the scheduler of folia need to take the location of the task into account to allocate it to the suitable main thread
     It's recommended to pass a location to this method every time if there is a location associated with the task
     If the task is reading/writing the data in the world, there must a location be provided
     Same thing applied to all the method required a location in this class
     If the task is general and has nothing about a location, pass a `null` to it.
     */
    public static void runTask(Plugin pluginInstance, Location location, Runnable runnable) {
        if (isFolia) {
            if (location == null)
                Bukkit.getGlobalRegionScheduler().run(pluginInstance, t -> runnable.run());
            else
                Bukkit.getRegionScheduler().run(pluginInstance, location, t -> runnable.run());
        } else if (isSpigot) {
            Bukkit.getScheduler().runTask(pluginInstance, runnable);
        } else {
            throw new RuntimeException("Unsupported server platform!");
        }
    }

    public static void runTaskLater(Plugin pluginInstance, Location location, Runnable runnable, long delay) {
        if (isFolia) {
            if (location == null)
                Bukkit.getGlobalRegionScheduler().runDelayed(pluginInstance, t -> runnable.run(), delay <= 0 ? 1 : delay);
            else
                Bukkit.getRegionScheduler().runDelayed(pluginInstance, location, t -> runnable.run(), delay <= 0 ? 1 : delay);
        } else if (isSpigot) {
            Bukkit.getScheduler().runTaskLater(pluginInstance, runnable, delay);
        } else {
            throw new RuntimeException("Unsupported server platform!");
        }
    }

    public static CompatibleTask runTaskTimer(Plugin pluginInstance, Location location, Runnable runnable, long delay, long taskTimer) {
        if (isFolia) {
            if (location == null)
                return new CompatibleTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(pluginInstance, t -> runnable.run(), delay <= 0 ? 1 : delay, taskTimer));
            else
                return new CompatibleTask(Bukkit.getRegionScheduler().runAtFixedRate(pluginInstance, location, t -> runnable.run(), delay <= 0 ? 1 : delay, taskTimer));
        } else if (isSpigot) {
            return new CompatibleTask(Bukkit.getScheduler().runTaskTimer(pluginInstance, runnable, delay, taskTimer));
        } else {
            throw new RuntimeException("Unsupported server platform!");
        }
    }

    public static void runTaskAsynchronously(Plugin pluginInstance, Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(pluginInstance, t -> runnable.run());
        } else if (isSpigot) {
            Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, runnable);
        } else {
            throw new RuntimeException("Unsupported server platform!");
        }
    }

    public static void cancelTasks(Plugin pluginInstance) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(pluginInstance);
        } else if (isSpigot) {
            Bukkit.getScheduler().cancelTasks(pluginInstance);
        } else {
            throw new RuntimeException("Unsupported server platform!");
        }
    }

}

class CompatibleTask {
    private final Object task;

    CompatibleTask(Object task) {
        if (!(task instanceof ScheduledTask) && !(task instanceof org.bukkit.scheduler.BukkitTask))
            throw new RuntimeException("Unsupported task type!");
        this.task = task;
    }

    public void cancel() {
        if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask) {
            ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
        } else if (task instanceof org.bukkit.scheduler.BukkitTask) {
            ((org.bukkit.scheduler.BukkitTask) task).cancel();
        }
    }

}
