package bor.samsara.questing.entity;

import bor.samsara.questing.mixin.PathAwareEntityAccessor;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;


public class ModEntities {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);


    public static void spawnWelcomingTraveler(ServerPlayerEntity player) {
        World world = player.getWorld();
        try {
            UUID traderUuid = UUID.randomUUID();
            String travelerName = "§ Hey, " + player.getName().getString() + "!";

            WanderingTraderEntity trader = makeWanderingTraderEntity(world, traderUuid, player, travelerName);
            configureWelcomeGoal(trader);
            world.spawnEntity(trader);
            MongoNpc mongoNpc = new MongoNpc(traderUuid.toString(), travelerName);
            MongoNpc.Quest q = new MongoNpc.Quest();
            q.setSequence(0);
            q.setObjective(new MongoNpc.Quest.Objective(MongoNpc.Quest.Objective.Type.TALK, "OldMan", 1));
            q.setDialogue(List.of("What are you doing here?!?", "This must mean the cycle has started again.", "Quick, go talk to the old man in the village, Brom.", "Go!", "Now fool!"));
            mongoNpc.setQuests(Map.of(0, q));
            NpcMongoClient.createNpc(mongoNpc);
        } catch (Exception e) {
            log.error("Failed spawning welcoming traverler: {}", e.getMessage(), e);

        }
        log.debug("Spawned a Welcoming Traveler for {}!", player.getName());
    }

    private static void configureWelcomeGoal(WanderingTraderEntity trader) {
        try {
            Field f = MobEntity.class.getDeclaredField("goalSelector");
            f.setAccessible(true);
            GoalSelector selector = (GoalSelector) f.get(trader);
            selector.add(1, createGoal(trader, selector));
        } catch (ReflectiveOperationException e) {
            log.error("Error reflecting goal accessor: {}", e.getMessage(), e);
        }
    }

    private static GoToWalkTargetGoal createGoal(WanderingTraderEntity trader, GoalSelector sel) {
        GoToWalkTargetGoal goToWalkTargetGoal = new GoToWalkTargetGoal(trader, 1.0);
        // trader.setCustomer();
        // trader.setWanderTarget();
        // trader.setTarget();
        WanderAroundGoal wanderGoal = new WanderAroundGoal(trader, 1.0, 80);
        sel.add(5, goToWalkTargetGoal);
        sel.add(7, wanderGoal);
        sel.add(8, new LookAtEntityGoal(trader, PlayerEntity.class, 8.0F));
        sel.add(8, new LookAtEntityGoal(trader, GuardianEntity.class, 12.0F, 0.01F));
        sel.add(9, new LookAroundGoal(trader));
        wanderGoal.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        goToWalkTargetGoal.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        //this.targetSelector.add(1, new ActiveTargetGoal(this, LivingEntity.class, 10, true, false, new GuardianEntity.GuardianTargetPredicate(this)));
        return goToWalkTargetGoal;
    }

    private static WanderingTraderEntity makeWanderingTraderEntity(World world, UUID uuid, ServerPlayerEntity player, String name) {
        WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world, SpawnReason.TRIGGERED);
        trader.setUuid(uuid);
        trader.refreshPositionAndAngles(player.getPos().x, player.getPos().y, player.getPos().z, player.getYaw(), player.getPitch());
        trader.setCustomName(Text.literal(name));
        trader.setCustomNameVisible(true);
        trader.addCommandTag("questNPC");
        trader.setAiDisabled(false);
        trader.setSilent(false);
        trader.setInvulnerable(true);
        trader.setNoGravity(false);
        trader.setGlowing(true);
        trader.getOffers().clear();

        trader.attachLeash(player, true); // tether trader to player
        trader.setTarget(player);

        return trader;
    }


    public static int createQuestNPC(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();

        try {
            UUID villagerUuid = UUID.randomUUID();
            VillagerEntity villager = makeVillagerEntity(world, villagerUuid, player, name);
            world.spawnEntity(villager);
            MongoNpc mongoNpc = new MongoNpc(villagerUuid.toString(), name);
            NpcMongoClient.createNpc(mongoNpc);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned a Quest NPC!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull VillagerEntity makeVillagerEntity(World world, UUID uuid, ServerPlayerEntity player, String name) {
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.TRIGGERED);
        villager.setUuid(uuid);
        villager.refreshPositionAndAngles(player.getPos().x, player.getPos().y, player.getPos().z, player.getYaw(), player.getPitch());
        villager.setCustomName(Text.literal(name));
        villager.addCommandTag("questNPC");
        villager.setAiDisabled(true);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setNoGravity(true);
        return villager;
    }


    public static void despawnTravelingWelcomer(ServerCommandSource source) {
    }

}

