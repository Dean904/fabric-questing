package bor.samsara.questing.hearth;

import bor.samsara.questing.Sounds;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SoulStoneEventRegisters extends WarpStone {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String SOUL_SIGN = "signedSoulstone";
    public static final Identifier SOULSTONE = Identifier.of("soulstone");

    private static final Map<String, ItemStack> playerSoulstonePersistanceMap = new HashMap<>();

    public static CommandRegistrationCallback createSoulstone() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("soulstone")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(argument("charges", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                            ItemStack hearthstone = createSoulstoneItem(context.getArgument("charges", Integer.class));
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

    public static @NotNull ItemStack createSoulstoneItem(int charges) {
        ItemStack soulstone = new ItemStack(Items.ENDER_EYE);
        soulstone.set(DataComponentTypes.CUSTOM_NAME, Text.literal("SoulStone"));
        soulstone.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal(charges + " charges remaining.").styled(s -> s.withColor(0xd700fd)),
                Text.literal("Right click to warp to your last death location."),
                Text.literal("Charge with Ender Pearls in offhand.").styled(s -> s.withColor(Colors.GRAY))))
        );
        soulstone.set(DataComponentTypes.RARITY, Rarity.RARE);
        soulstone.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        soulstone.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        soulstone.set(DataComponentTypes.USE_COOLDOWN, new UseCooldownComponent(7, Optional.of(SOULSTONE)));

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(SOUL_SIGN, "SIGNED");
        nbtCompound.putInt(STONE_CHARGES, charges);
        soulstone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));
        return soulstone;
    }

    public static UseItemCallback useSoulstone() {
        return (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (hand == Hand.OFF_HAND && stack.isOf(Items.ENDER_PEARL) && isSoulStone(player.getMainHandStack())) {
                return ActionResult.CONSUME;
            }

            if (isSoulStone(stack)) {
                if (player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
                    Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE);
                    player.getOffHandStack().decrement(1);
                    adjustCharges(stack, +1);
                    return ActionResult.CONSUME;
                }
                if (player.getMovement().equals(Vec3d.ZERO)) {
                    ItemCooldownManager itemCooldownManager = player.getItemCooldownManager();
                    if (!isCharged(stack)) {
                        player.sendMessage(Text.literal("Zero charges remaining. Add more with Ender Pearls.").styled(style -> style.withColor(Formatting.RED)), true);
                        Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.ITEM_OMINOUS_BOTTLE_DISPOSE.value());
                        return ActionResult.CONSUME;
                    }
                    if (!isTeleporting(player.getUuidAsString()) && !itemCooldownManager.isCoolingDown(stack)) {
                        itemCooldownManager.set(SOULSTONE, 7 * 20); // 7 * 20 ticks per second = 7 seconds cooldown
                        GlobalPos lastDeathPos = player.getLastDeathPos().orElse(null);
                        if (lastDeathPos != null && lastDeathPos.pos().getY() > -64) {
                            BlockPos safeWarpLocation = findRandomSafeLocation(lastDeathPos.pos(), world, new Random());
                            if (safeWarpLocation != null) {
                                player.sendMessage(Text.of("💫 Whoosh!"), true);
                                Future<?> task = executor.submit(createCastTask(player, world, stack, safeWarpLocation, lastDeathPos.dimension()));
                                playerTeleportTasks.put(player.getUuidAsString(), new HearthStoneEventRegisters.TeleportTask(task, player.getEntityPos()));
                                adjustCharges(stack, -1);
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

    private static boolean isSoulStone(ItemStack stack) {
        return stack.isOf(Items.ENDER_EYE) && stack.getName().getString().equals("SoulStone") && hasSoulstoneNbt(stack);
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

            if ((world.getBlockState(candidate).isAir() || world.getBlockState(candidate).getBlock() == Blocks.WATER) && !world.getBlockState(below).isAir()) {
                log.debug("Found Soulstone safe location after {} tries.", attempt);
                return candidate;
            }
        }

        log.debug("Could not find safe soulstone location for {} with radius {}.", deathPos, radius);
        return null;
    }

    private static @NotNull Runnable createCastTask(PlayerEntity player, World world, ItemStack stack, BlockPos blockPos, RegistryKey<World> dimension) {
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

    public static ServerLivingEntityEvents.@NotNull AllowDeath cacheSoulstoneBeforeDeath() {
        return (LivingEntity livingEntity, DamageSource damageSource, float damageAmount) -> {
            if (livingEntity instanceof ServerPlayerEntity player) {
                for (ItemStack item : player.getInventory()) {
                    if (isSoulStone(item)) {
                        playerSoulstonePersistanceMap.put(player.getUuidAsString(), item);
                        player.getInventory().removeOne(item);
                        return true;
                    }
                }
            }
            return true;
        };
    }

    public static ServerPlayerEvents.@NotNull AfterRespawn handleAfterRespawn() {
        return (oldPlayer, newPlayer, alive) -> {
            String uuid = newPlayer.getUuidAsString();
            if (playerSoulstonePersistanceMap.containsKey(uuid)) {
                log.debug("Granting player soul stone after respawn.");
                newPlayer.giveItemStack(playerSoulstonePersistanceMap.get(uuid));
                playerSoulstonePersistanceMap.remove(uuid);
            }
        };
    }
}
