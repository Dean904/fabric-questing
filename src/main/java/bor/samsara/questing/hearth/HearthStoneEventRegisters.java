package bor.samsara.questing.hearth;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HearthStoneEventRegisters {

    public static final String WARP_LOCATION = "warpLocation";

    private static final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            runnable -> new Thread(runnable, "HearthStoneEvent-Thread"));

    public record TeleportTask(Future<?> task, Vec3d startPos) {}

    private static final Map<String, TeleportTask> playerTeleportTasks = new ConcurrentHashMap<>();

    public static boolean isTeleporting(String uuidAsString) {
        return playerTeleportTasks.containsKey(uuidAsString);
    }

    public static boolean hasPlayerMovedFromStartPos(PlayerEntity player) {
        TeleportTask teleTask = playerTeleportTasks.get(player.getUuidAsString());
        if (teleTask != null) {
            return !teleTask.startPos().equals(player.getPos());
        }
        return false;
    }

    public static void cancelTeleport(PlayerEntity player) {
        TeleportTask teleTask = playerTeleportTasks.remove(player.getUuidAsString());
        if (teleTask != null) {
            teleTask.task().cancel(true);
            player.sendMessage(Text.of("Teleport cancelled!"), true);
        }
    }

    public static CommandRegistrationCallback createHearthstone() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("hearthstone")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(literal("create")
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .then(argument("name", greedyString())
                                                .executes(context -> {
                                                            String name = getString(context, "name");

                                                            ItemStack hearthstone = new ItemStack(Items.CARROT_ON_A_STICK);
                                                            hearthstone.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hearthstone"));
                                                            hearthstone.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Right click to teleport to " + name))));
                                                            hearthstone.set(DataComponentTypes.RARITY, Rarity.RARE);
                                                            hearthstone.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                                                            hearthstone.set(DataComponentTypes.MAX_STACK_SIZE, 1);
                                                            // addEnchantment(hearthstone);

                                                            BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
                                                            NbtCompound nbtCompound = new NbtCompound();
                                                            nbtCompound.putLong(WARP_LOCATION, pos.asLong());
                                                            hearthstone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

                                                            ServerCommandSource source = context.getSource();
                                                            ServerPlayerEntity player = source.getPlayerOrThrow();
                                                            if (player.getInventory().insertStack(hearthstone)) {
                                                                source.sendFeedback(() -> Text.literal("Given hearthstone to " + player.getName().getString()), false);
                                                            } else {
                                                                // If inventory is full, drop it on the ground
                                                                player.dropItem(hearthstone, true);
                                                            }

                                                            return 1;
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

    public static @NotNull UseItemCallback useHearthstone() {
        return (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.CARROT_ON_A_STICK) && stack.getName().getString().equals("Hearthstone")) {
                if (!isTeleporting(player.getUuidAsString())) {
                    player.sendMessage(Text.of("💫 Whoosh!"), true);
                    Future<?> task = executor.submit(createCastTask(player, world, stack));
                    playerTeleportTasks.put(player.getUuidAsString(), new TeleportTask(task, player.getPos()));
                }
                return ActionResult.PASS;
            }
            return ActionResult.PASS;
        };
    }

    private static @NotNull Runnable createCastTask(PlayerEntity player, World world, ItemStack stack) {
        return () -> {
            try {
                BiConsumer<SoundEvent, Float> play = (s, p) ->
                        player.playSoundToPlayer(s, SoundCategory.PLAYERS, 1.0f, p);
                play.accept(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f); // C5
                Thread.sleep(150);

                for (int i = 10; i > 0; i--) {
                    play.accept(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.0f + (i / 10f));
                    player.sendMessage(Text.of("💫 Teleporting in " + i + " seconds. Dont move!"), true);
                    Thread.sleep(1000);
                }

                BlockPos tpTarget = BlockPos.fromLong(stack.get(DataComponentTypes.CUSTOM_DATA).getNbt().getLong(WARP_LOCATION).get());
                ServerWorld serverWorld = world.getServer().getWorld(World.OVERWORLD);
                player.teleportTo(new TeleportTarget(serverWorld, tpTarget.toCenterPos(), Vec3d.ZERO, 0, 0, PositionFlag.ROT, TeleportTarget.NO_OP));

                player.addExhaustion(240);
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                playerTeleportTasks.remove(player.getUuidAsString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }


    private static void addEnchantment(ItemStack item) {
        Object2IntOpenHashMap<RegistryKey<Enchantment>> map = new Object2IntOpenHashMap<>();
        map.addTo(Enchantments.UNBREAKING, 1);

        ItemEnchantmentsComponent.Builder enchantBuilder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        //enchantBuilder.add(RegistryEntry.of(Enchantment.builder(),),  1);

//        item.set(DataComponentTypes.ENCHANTMENTS,
//                new EnchantmentComponent(List.of(
//                        new EnchantmentComponent.Entry(Enchantments.UNBREAKING, 1)
//                ))
//        );

        item.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);

    }
}
