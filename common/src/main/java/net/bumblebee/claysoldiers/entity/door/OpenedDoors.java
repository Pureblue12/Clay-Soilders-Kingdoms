package net.bumblebee.claysoldiers.entity.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Doors that clay soldiers opened and haven't closed yet, with the soldiers currently going through each.
 * Soldiers only close doors that soldiers opened (a door a player left open stays open), and only the last soldier
 * going through closes it, so a door never shuts on a soldier behind. Memory only: after a restart a door left open
 * counts as opened by a player.
 */
final class OpenedDoors {
    private static final Map<Level, Map<BlockPos, Set<Integer>>> DOORS = new WeakHashMap<>();

    private OpenedDoors() {
    }

    static void opened(Level level, BlockPos door) {
        doors(level).computeIfAbsent(door.immutable(), pos -> new HashSet<>());
    }

    static boolean openedBySoldier(Level level, BlockPos door) {
        return doors(level).containsKey(door);
    }

    static void join(Level level, BlockPos door, Entity soldier) {
        Set<Integer> users = doors(level).get(door);
        if (users != null) {
            users.add(soldier.getId());
        }
    }

    static void leave(Level level, BlockPos door, Entity soldier) {
        Set<Integer> users = doors(level).get(door);
        if (users != null) {
            users.remove(soldier.getId());
        }
    }

    /**
     * Returns how many soldiers other than {@code soldier} are still going through the door (removed or dead soldiers
     * are forgotten).
     */
    static int otherUsers(Level level, BlockPos door, Entity soldier) {
        Set<Integer> users = doors(level).get(door);
        if (users == null) {
            return 0;
        }
        users.removeIf(id -> {
            Entity user = level.getEntity(id);
            return user == null || !user.isAlive();
        });
        return (int) users.stream().filter(id -> id != soldier.getId()).count();
    }

    static void closed(Level level, BlockPos door) {
        doors(level).remove(door);
    }

    private static Map<BlockPos, Set<Integer>> doors(Level level) {
        return DOORS.computeIfAbsent(level, l -> new HashMap<>());
    }
}
