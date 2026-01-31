package bor.samsara.questing.hearth;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Future;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SoulStoneEventRegisters extends WarpStone {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String SOUL_SIGN = "signedSoulstone";
    public static final Identifier SOULSTONE = Identifier.of("soulstone");


    public static CommandRegistrationCallback createSoulstone() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("soulstone")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                            ItemStack hearthstone = createSoulstoneItem(context.getArgument("count", Integer.class));
                                            ServerCommandSource source = context.getSource();
                                            ServerPlayerEntity player = source.getPlayerOrThrow();
                                            if (player.getInventory().insertStack(hearthstone)) {
                                                source.sendFeedback(() -> Text.literal("Given soulstone to " + player.getName().getString()), false);
                                            } else {
                                                // If inventory is full, drop it on the ground
                                                player.dropItem(hearthstone, true);
                                            }
                                            return 1;
                                        }
                                ))
        );
    }

    public static @NotNull ItemStack createSoulstoneItem(int size) {
        ItemStack soulstone = new ItemStack(Items.ENDER_EYE);
        soulstone.set(DataComponentTypes.CUSTOM_NAME, Text.literal("SoulStone"));
        soulstone.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Right click to warp to your last death location."))));
        soulstone.set(DataComponentTypes.RARITY, Rarity.RARE);
        soulstone.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        soulstone.set(DataComponentTypes.MAX_STACK_SIZE, 16);
        soulstone.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(7, Optional.of(SOULSTONE)));
        soulstone.setCount(size);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(SOUL_SIGN, "SIGNED");
        soulstone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));
        return soulstone;
    }

    public static ServerLivingEntityEvents.@NotNull AfterDeath saveDeathLocation() {
        return (entity, damageSource) -> {
            if (entity.isPlayer()) {
                MongoPlayer mongoPlayer = PlayerMongoClient.getPlayerByUuid(entity.getUuidAsString());
                mongoPlayer.setDeathPosition(entity.getBlockPos());
                mongoPlayer.setDeathDimension(entity.getEntityWorld().getRegistryKey().getValue().toString());
                PlayerMongoClient.updatePlayer(mongoPlayer);
            }
        };
    }

    public static UseItemCallback useSoulstone() {
        return (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.ENDER_EYE) && stack.getName().getString().equals("SoulStone") && hasSoulstoneNbt(stack)) {
                if (player.getMovement().equals(Vec3d.ZERO)) {
                    ItemCooldownManager itemCooldownManager = player.getItemCooldownManager();
                    if (!isTeleporting(player.getUuidAsString()) && !itemCooldownManager.isCoolingDown(stack)) {
                        itemCooldownManager.set(SOULSTONE, 7 * 20); // 7 * 20 ticks per second = 7 seconds cooldown
                        MongoPlayer mongoPlayer = PlayerMongoClient.getPlayerByUuid(player.getUuidAsString());
                        BlockPos lastDeathPos = mongoPlayer.getDeathPosition();
                        if (lastDeathPos != null && lastDeathPos.getY() > -64) {
                            BlockPos safeWarpLocation = findRandomSafeLocation(lastDeathPos, world, new Random());
                            if (safeWarpLocation != null) {
                                player.sendMessage(Text.of("💫 Whoosh!"), true);
                                Future<?> task = executor.submit(createCastTask(player, world, stack, safeWarpLocation, mongoPlayer.getDeathDimension()));
                                playerTeleportTasks.put(player.getUuidAsString(), new HearthStoneEventRegisters.TeleportTask(task, player.getEntityPos()));
                            } else {
                                player.sendMessage(Text.of("💫 No safe location found."), true);
                            }
                        } else {
                            player.sendMessage(Text.of("💫 No death location saved."), true);
                        }
                    }
                } else {
                    player.sendMessage(Text.literal("You cant do that while moving!").styled(style -> style.withColor(Formatting.RED)), true);
                }
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        };
    }

    private static boolean hasSoulstoneNbt(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.copyNbt();
            return nbt.contains(SOUL_SIGN);
        }
        return false;
    }

    private static BlockPos findRandomSafeLocation(BlockPos deathPos, World world, Random random) {
        int radius = 14;
        int minY = Math.max(-64, deathPos.getY() - radius);
        int maxY = Math.min(316, deathPos.getY() + radius);

        for (int attempt = 0; attempt < 200; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt((maxY - minY) + 1) + minY - deathPos.getY(); // relative offset within vertical band

            BlockPos candidate = deathPos.add(dx, dy, dz);
            BlockPos below = candidate.down();

            if (world.getBlockState(candidate).isAir() && !world.getBlockState(below).isAir()) {
                log.debug("Found Soulstone safe location after {} tries.", attempt);
                return candidate;
            }
        }

        log.debug("Could not find safe soulstone location for {} with radius {}.", deathPos, radius);
        return null;
    }

    private static @NotNull Runnable createCastTask(PlayerEntity player, World world, ItemStack stack, BlockPos blockPos, String deathDimension) {
        return () -> {
            try {
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_ENDER_EYE_DEATH);
                Thread.sleep(15);

                CommandManager commandManager = Objects.requireNonNull(player.getEntityWorld().getServer()).getCommandManager();
                ServerCommandSource commandSource = player.getEntityWorld().getServer().getCommandSource().withSilent();

                int numSteps = 400; // more steps = smoother curve
                long totalMillis = 2000;
                long seconds = totalMillis / 1000;
                long sleepPerStep = Math.max(1, totalMillis / numSteps);

                // Spline endpoints and control point
                Vec3d start = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
                Vec3d end = blockPos.toCenterPos();

                for (int i = 0; i < numSteps; i++) {
                    double t = (double) i / (numSteps - 1);
                    Vec3d point = lerp(start, end, t);

                    commandManager.parseAndExecute(commandSource, "/particle minecraft:sculk_soul %f %f %f".formatted(jitter(point.x), jitter(point.y), jitter(point.z)));
                    commandManager.parseAndExecute(commandSource, "/particle minecraft:soul_fire_flame %f %f %f".formatted(jitter(point.x), jitter(point.y), jitter(point.z)));
                    commandManager.parseAndExecute(commandSource, "/particle minecraft:trial_spawner_detection_ominous %f %f %f".formatted(jitter(point.x), jitter(point.y), jitter(point.z)));

                    if (i % (numSteps / seconds) == 0) {
                        long secondsLeft = seconds - (i / (numSteps / seconds));
                        Sounds.aroundPlayer(player, SoundEvents.AMBIENT_CAVE.value(), 1.0f, 1.0f + (secondsLeft / 10f));
                        player.sendMessage(Text.of("💫 Teleporting in " + secondsLeft + " seconds. Dont move!"), true);
                    }

                    Thread.sleep(sleepPerStep);
                }

                stack.decrement(1);
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(deathDimension));
                ServerWorld serverWorld = world.getServer().getWorld(dimension);
                player.teleportTo(new TeleportTarget(serverWorld, blockPos.toCenterPos(), Vec3d.ZERO, (float) (Math.random() * 180), 0, PositionFlag.DELTA, TeleportTarget.NO_OP));

                player.addExhaustion(240);
                Sounds.aroundPlayer(player, SoundEvents.ITEM_TOTEM_USE);
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_TELEPORT);
                Sounds.aroundPlayer(player, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value());
                playerTeleportTasks.remove(player.getUuidAsString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        double oneMinusT = 1.0 - t;
        return new Vec3d(
                (a.x * oneMinusT + b.x * t) + Math.sin(t * 2 * Math.PI),
                (a.y * oneMinusT + b.y * t) + Math.sin(t * 2 * Math.PI),
                (a.z * oneMinusT + b.z * t) + Math.sin(t * 2 * Math.PI)
        );
    }

}
