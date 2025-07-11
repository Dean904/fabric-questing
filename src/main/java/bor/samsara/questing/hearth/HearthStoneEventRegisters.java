package bor.samsara.questing.hearth;

import bor.samsara.questing.entity.QuestLogBook;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HearthStoneEventRegisters {

    public static final String WARP_LOCATION = "warpLocation";

    private static final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            runnable -> new Thread(runnable, "HearthStoneEvent-Thread"));
    public static final Identifier HEARTHSTONE = Identifier.of("hearthstone");

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
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.1f);
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
                                                            ItemStack hearthstone = createHearthstoneItem(name, BlockPosArgumentType.getBlockPos(context, "pos"));
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

    public static @NotNull ItemStack createHearthstoneItem(String name, BlockPos pos) {
        ItemStack hearthstone = new ItemStack(Items.CARROT_ON_A_STICK);
        hearthstone.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hearthstone"));
        hearthstone.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Right click to teleport to " + name))));
        hearthstone.set(DataComponentTypes.RARITY, Rarity.RARE);
        hearthstone.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        hearthstone.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        hearthstone.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(30, Optional.of(HEARTHSTONE)));
        // addEnchantment(hearthstone);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putLong(WARP_LOCATION, pos.asLong());
        hearthstone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));
        return hearthstone;
    }

    public static @NotNull UseItemCallback useHearthstone() {
        return (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.CARROT_ON_A_STICK) && stack.getName().getString().equals("Hearthstone") && hasHearthstoneNbt(stack)) {
                if (player.getMovement().equals(Vec3d.ZERO)) {
                    ItemCooldownManager itemCooldownManager = player.getItemCooldownManager();
                    if (!isTeleporting(player.getUuidAsString()) && !itemCooldownManager.isCoolingDown(stack)) {
                        player.sendMessage(Text.of("💫 Whoosh!"), true);
                        itemCooldownManager.set(HEARTHSTONE, 30 * 20); // 30 * 20 ticks per second = 30 seconds cooldown
                        Future<?> task = executor.submit(createCastTask(player, world, stack));
                        playerTeleportTasks.put(player.getUuidAsString(), new TeleportTask(task, player.getPos()));
                    }
                } else {
                    player.sendMessage(Text.literal("You cant do that while moving!").styled(style -> style.withColor(Formatting.RED)), true);
                }
                return ActionResult.PASS;
            }
            return ActionResult.PASS;
        };
    }

    private static boolean hasHearthstoneNbt(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.getNbt();
            return nbt != null && nbt.contains(WARP_LOCATION);
        }
        return false;
    }

    private static @NotNull Runnable createCastTask(PlayerEntity player, World world, ItemStack stack) {
        return () -> {
            try {
                player.playSoundToPlayer(SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.0f);
                Thread.sleep(150);
                //player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.7f, 1.0f);

                CommandManager commandManager = Objects.requireNonNull(player.getServer()).getCommandManager();
                ServerCommandSource commandSource = player.getServer().getCommandSource().withSilent();

                int numSteps = 180;
                for (int i = 0; i < numSteps; i++) {

                    Thread.sleep(10_000 / numSteps); // 10 seconds total, 180 steps = 55ms per step

                    double radius = 3.5 * ((double) (numSteps - i) / numSteps) + 0.2; // radius decreases from 3.5 to 0.2 over the steps
                    double angle = i * (Math.PI * 2 / numSteps); // 360 / 180 = 2 degrees per step, but in radians it is 2 * Math.PI / 180

                    summonParticles(player, radius, angle, i, numSteps, commandManager, commandSource);
                    summonParticles(player, radius, angle + (Math.PI * 2 / 3), i, numSteps, commandManager, commandSource);
                    summonParticles(player, radius, angle + (Math.PI * 4 / 3), i, numSteps, commandManager, commandSource);

                    if (i % (numSteps / 10) == 0) {
                        int secondsLeft = 10 - (i / (numSteps / 10));
                        player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.0f + (secondsLeft / 10f));
                        player.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.PLAYERS, 0.3f, 1.0f + (secondsLeft / 10f));
                        player.sendMessage(Text.of("💫 Teleporting in " + secondsLeft + " seconds. Dont move!"), true);
                    }
                    if (i == 160) {
                        player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }

                ServerWorld serverWorld = world.getServer().getWorld(World.OVERWORLD);
                BlockPos tpTarget = BlockPos.fromLong(stack.get(DataComponentTypes.CUSTOM_DATA).getNbt().getLong(WARP_LOCATION).get());
                player.teleportTo(new TeleportTarget(serverWorld, tpTarget.toCenterPos(), Vec3d.ZERO, 0, 0, PositionFlag.DELTA, TeleportTarget.NO_OP));

                player.addExhaustion(240);
                player.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                playerTeleportTasks.remove(player.getUuidAsString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static void summonParticles(PlayerEntity player, double radius, double angle, int i, int numSteps, CommandManager commandManager, ServerCommandSource commandSource) {
        double swirlHeight = 1.2;
        double x = player.getX() + radius * Math.cos(angle);
        double z = player.getZ() + radius * Math.sin(angle);
        double y = player.getY() + swirlHeight * ((double) i / numSteps);
        commandManager.executeWithPrefix(commandSource, "/particle minecraft:sculk_soul %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
        commandManager.executeWithPrefix(commandSource, "/particle minecraft:soul_fire_flame %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
        commandManager.executeWithPrefix(commandSource, "/particle minecraft:trial_spawner_detection_ominous %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
    }

    private static double jitter(double d) {
        return d + ((Math.random() - 0.5) * 0.5); // Small jitter to simulate randomness
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
