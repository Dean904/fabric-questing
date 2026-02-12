package bor.samsara.questing.hearth;

import bor.samsara.questing.Sounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class WarpStone {

    protected static final String STONE_CHARGES = "stoneCharges";

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
            Sounds.aroundPlayer(player, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
            Sounds.aroundPlayer(player, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
            Sounds.aroundPlayer(player, SoundEvents.BLOCK_GLASS_BREAK, 1.0f, 0.1f);
            player.sendMessage(Text.of("Teleport cancelled!"), true);
        }
    }

    protected static void adjustCharges(ItemStack stack, int amount) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData.copyNbt();
        int charges = nbt.getInt(STONE_CHARGES, 0);
        nbt.putInt(STONE_CHARGES, charges + amount);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        LinkedList<Text> lines = new LinkedList<>(stack.getComponents().get(DataComponentTypes.LORE).styledLines());
        lines.removeFirst();
        lines.addFirst(Text.literal(charges + amount + " charges remaining.").styled(s -> s.withColor(0xd700fd)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
    }

    protected static boolean isCharged(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData.copyNbt();
        int charges = nbt.getInt(STONE_CHARGES, 0);
        return charges > 0;
    }

    static double jitter(double d) {
        return d + ((Math.random() - 0.5) * 0.5); // Small jitter to simulate randomness
    }

}
