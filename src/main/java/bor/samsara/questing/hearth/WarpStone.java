package bor.samsara.questing.hearth;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class WarpStone {

    static final ExecutorService executor = Executors.newThreadPerTaskExecutor(runnable -> new Thread(runnable, "HearthStoneEvent-Thread"));
    static final Map<String, TeleportTask> playerTeleportTasks = new ConcurrentHashMap<>();

    public record TeleportTask(Future<?> task, Vec3d startPos) {}

    public static boolean isTeleporting(String uuidAsString) {
        return playerTeleportTasks.containsKey(uuidAsString);
    }

    public static boolean hasPlayerMovedFromStartPos(PlayerEntity player) {
        TeleportTask teleTask = playerTeleportTasks.get(player.getUuidAsString());
        if (teleTask != null) {
            return !teleTask.startPos().equals(player.getEntityPos());
        }
        return false;
    }

    public static void cancelTeleport(PlayerEntity player) {
        TeleportTask teleTask = playerTeleportTasks.remove(player.getUuidAsString());
        if (teleTask != null) {
            teleTask.task().cancel(true);
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.1f);
            player.sendMessage(Text.of("Teleport cancelled!"), true);
        }
    }

    static double jitter(double d) {
        return d + ((Math.random() - 0.5) * 0.5); // Small jitter to simulate randomness
    }

}
