package bor.samsara.questing.hearth;

import bor.samsara.questing.Sounds;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HearthStoneEventRegisters extends WarpStone {

    public static final String WARP_LOCATION = "warpLocation";
    public static final String WARP_DIMENSION = "warpDimension";

    public static final Identifier HEARTHSTONE = Identifier.of("hearthstone");

    public static CommandRegistrationCallback createHearthstone() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("hearthstone")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(literal("create")
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .then(argument("dimension", string())
                                                .then(argument("name", greedyString())
                                                        .executes(context -> {
                                                                    String name = getString(context, "name");
                                                                    BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
                                                                    String dimensionStr = getString(context, "dimension");
                                                                    RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimensionStr));
                                                                    ItemStack hearthstone = createHearthstoneItem(name, GlobalPos.create(dimension, pos));
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
                        )
        );
    }

    public static @NotNull ItemStack createHearthstoneItem(String name, GlobalPos globalPos) {
        ItemStack hearthstone = new ItemStack(Items.CARROT_ON_A_STICK);
        hearthstone.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hearthstone"));
        hearthstone.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("8 charges remaining.").styled(s -> s.withColor(0xd700fd)),
                Text.literal("Right click to teleport to " + name),
                Text.literal("Charge with Ender Pearls in offhand.").styled(s -> s.withColor(Colors.GRAY)))));
        hearthstone.set(DataComponentTypes.RARITY, Rarity.RARE);
        hearthstone.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        hearthstone.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        hearthstone.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(30, Optional.of(HEARTHSTONE)));
        // addEnchantment(hearthstone);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putLong(WARP_LOCATION, globalPos.pos().asLong());
        nbtCompound.putString(WARP_DIMENSION, globalPos.dimension().getValue().toString());
        nbtCompound.putInt(STONE_CHARGES, 8);
        hearthstone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        return hearthstone;
    }


    public static @NotNull UseItemCallback useHearthstone() {
        return (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (hand == Hand.OFF_HAND && stack.isOf(Items.ENDER_PEARL) && isHearthstone(player.getMainHandStack())) {
                return ActionResult.CONSUME;
            }

            if (isHearthstone(stack)) {

                if (player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
                    Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.BLOCK_BEACON_POWER_SELECT);
                    player.getOffHandStack().decrement(1);
                    adjustCharges(stack, +1);
                    return ActionResult.CONSUME;
                }

                if (player.getMovement().equals(Vec3d.ZERO)) {
                    ItemCooldownManager itemCooldownManager = player.getItemCooldownManager();
                    if (!isCharged(stack)) {
                        player.sendMessage(Text.literal("Zero charges remaining. Add more with Ender Pearls.").styled(style -> style.withColor(Formatting.RED)), true);
                        Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.ITEM_WOLF_ARMOR_REPAIR);
                        return ActionResult.CONSUME;
                    }
                    if (!isTeleporting(player.getUuidAsString()) && !itemCooldownManager.isCoolingDown(stack)) {
                        player.sendMessage(Text.of("💫 Whoosh!"), true);
                        itemCooldownManager.set(HEARTHSTONE, 30 * 20); // 30 * 20 ticks per second = 30 seconds cooldown
                        Future<?> task = executor.submit(createCastTask(player, world, stack));
                        adjustCharges(stack, -1);
                        playerTeleportTasks.put(player.getUuidAsString(), new TeleportTask(task, player.getEntityPos()));
                    }
                } else {
                    player.sendMessage(Text.literal("You cant do that while moving!").styled(style -> style.withColor(Formatting.RED)), true);
                }
                return ActionResult.PASS;
            }
            return ActionResult.PASS;
        };
    }

    private static boolean isHearthstone(ItemStack stack) {
        return stack.isOf(Items.CARROT_ON_A_STICK) && stack.getName().getString().equals("Hearthstone") && hasHearthstoneNbt(stack);
    }

    private static boolean hasHearthstoneNbt(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            return nbt.contains(WARP_LOCATION);
        }
        return false;
    }

    private static @NotNull Runnable createCastTask(PlayerEntity player, World world, ItemStack stack) {
        return () -> {
            try {
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_EVOKER_CAST_SPELL);
                Thread.sleep(150);
                //player.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.7f, 1.0f);

                CommandManager commandManager = Objects.requireNonNull(player.getEntityWorld().getServer()).getCommandManager();
                ServerCommandSource commandSource = player.getEntityWorld().getServer().getCommandSource().withSilent();

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
                        Sounds.aroundPlayer(player, SoundEvents.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f + (secondsLeft / 10f));
                        Sounds.aroundPlayer(player, SoundEvents.AMBIENT_CAVE.value(), 0.3f, 1.0f + (secondsLeft / 10f));
                        player.sendMessage(Text.of("💫 Teleporting in " + secondsLeft + " seconds. Dont move!"), true);
                    }
                    if (i == 160) {
                        Sounds.aroundPlayer(player, SoundEvents.BLOCK_BEACON_POWER_SELECT);
                    }
                }

                String dimensionStr = stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getString(WARP_DIMENSION).get();
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimensionStr));
                ServerWorld serverWorld = world.getServer().getWorld(dimension);
                BlockPos tpTarget = BlockPos.fromLong(stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt().getLong(WARP_LOCATION).get());
                player.teleportTo(new TeleportTarget(serverWorld, tpTarget.toCenterPos(), Vec3d.ZERO, 0, 0, PositionFlag.DELTA, TeleportTarget.NO_OP));

                player.addExhaustion(240);
                Sounds.aroundPlayer(player, SoundEvents.BLOCK_BEACON_POWER_SELECT);
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_TELEPORT);
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
        commandManager.parseAndExecute(commandSource, "/particle minecraft:sculk_soul %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
        commandManager.parseAndExecute(commandSource, "/particle minecraft:soul_fire_flame %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
        commandManager.parseAndExecute(commandSource, "/particle minecraft:trial_spawner_detection_ominous %f %f %f".formatted(jitter(x), jitter(y), jitter(z)));
    }

}
