package bor.samsara.questing.entity;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;


public class ModEntities {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String QUEST_NPC = "questNPC";
    public static final String QUEST_START_NODE = "questStartNode";

    private ModEntities() {}

    public static void despawnTravelingWelcomer(ServerPlayerEntity player) {
        World world = player.getWorld();
        try {
            String travelerName = "§ Hey, " + player.getName().getString() + "!";
            MongoNpc mongoNpc = NpcMongoClient.getFirstNpcByName(travelerName);
            Entity trader = world.getEntity(UUID.fromString(mongoNpc.getUuid()));
            if (null != trader)
                player.getServer().execute(trader::discard);
        } catch (Exception e) {
            log.warn("Failed removing welcoming traveler: {}", e.getMessage(), e);
        }
        log.debug("RemovedWelcoming Traveler for {}", player.getName());
    }

    public static void spawnWelcomingTraveler(ServerPlayerEntity player) {
        World world = player.getWorld();
        try {
            String travelerName = "§ Hey, " + player.getName().getString() + "!";
            MongoNpc mongoNpc = getOrMakeWelcomeTravelerForPlayer(travelerName);
            MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuidAsString());

            if (playerState.getNpcQuestProgressMap().containsKey(mongoNpc.getUuid()) && playerState.getNpcQuestProgressMap().get(mongoNpc.getUuid()).isComplete()) {
                return;
            }

            String firstQuestId = mongoNpc.getQuestIds().getFirst();
            MongoQuest firstQuest = QuestMongoClient.getQuestByUuid(firstQuestId);
            playerState.getNpcQuestProgressMap().put(mongoNpc.getUuid(), new MongoPlayer.QuestProgress(firstQuestId, firstQuest.getTitle(), 0));
            PlayerMongoClient.updatePlayer(playerState);

            SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, mongoNpc, firstQuest.getObjective());
            WanderingTraderEntity trader = makeWanderingTraderEntity(world, mongoNpc.getUuid(), player, travelerName);
            //configureWelcomeGoal(trader); TODO
            world.spawnEntity(trader);
            log.debug("Spawned a Welcoming Traveler for {}!", player.getName());
        } catch (Exception e) {
            log.error("Failed spawning welcoming traverler: {}", e.getMessage(), e);
        }
    }

    private static MongoNpc getOrMakeWelcomeTravelerForPlayer(String travelerName) {
        try {
            return NpcMongoClient.getFirstNpcByName(travelerName);
        } catch (IllegalStateException e) {
            MongoNpc mongoNpc = new MongoNpc(UUID.randomUUID().toString(), travelerName);
            mongoNpc.setDialogueType("WELCOME");
            mongoNpc.setQuestIds(getWelcomingQuestUuids());
            NpcMongoClient.createNpc(mongoNpc);
            return mongoNpc;
        }
    }

    private static List<String> getWelcomingQuestUuids() {
        String travelerStartQuestTitle = "Traveler Greeting";
        String travelerEndQuestTitle = "Traveler Farewell";

        try {
            MongoQuest qStart = QuestMongoClient.getQuestByTitle(travelerStartQuestTitle);
            MongoQuest qEnd = QuestMongoClient.getQuestByTitle(travelerEndQuestTitle);
            return List.of(qStart.getUuid(), qEnd.getUuid());
        } catch (IllegalStateException e) {
            log.info("Creating Welcoming Traveler quests for the first time.");
            List<String> dialogue = List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "Quick, go talk to the old man in the village, Bondred.",
                    "Go!", "Now fool!", "...", "Is this some sort of game to you?", "This is SERIOUS!");

            MongoQuest qStart = new MongoQuest();
            qStart.setTitle(travelerStartQuestTitle);
            qStart.setSequence(0);
            qStart.setObjective(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "WELCOME", 4));
            qStart.setReward(new MongoQuest.Reward("minecraft:totem_of_undying", 1, 15));
            qStart.setDialogue(dialogue);
            QuestMongoClient.createQuest(qStart);

            MongoQuest qEnd = new MongoQuest();
            qEnd.setTitle(travelerEndQuestTitle);
            qEnd.setSequence(1);
            qEnd.setObjective(new MongoQuest.Objective(MongoQuest.Objective.Type.FIN, "", -1));
            qEnd.setReward(new MongoQuest.Reward("none", 0, 0));
            qEnd.setDialogue(dialogue);
            QuestMongoClient.createQuest(qEnd);

            return List.of(qStart.getUuid(), qEnd.getUuid());
        }
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

    private static WanderingTraderEntity makeWanderingTraderEntity(World world, String uuid, ServerPlayerEntity player, String name) {
        WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world, SpawnReason.TRIGGERED);
        trader.setUuid(UUID.fromString(uuid));

        Vec3d forward = player.getRotationVec(1.0f).normalize();
        trader.refreshPositionAndAngles(player.getPos().x + forward.x * 2, player.getPos().y, player.getPos().z + forward.z * 2, player.getYaw(), player.getPitch());
        trader.setCustomName(Text.literal(name));
        trader.setCustomNameVisible(true);
        trader.addCommandTag(QUEST_NPC);
        trader.setAiDisabled(false);
        trader.setSilent(false);
        trader.setNoGravity(false);
        trader.setGlowing(true);
        trader.getOffers().clear();

        trader.attachLeash(player, true); // tether trader to player
        trader.setTarget(player);

        return trader;
    }

    public static int createQuestNPC(ServerCommandSource source, String name, boolean isStartNode) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();

        try {
            UUID villagerUuid = UUID.randomUUID();
            VillagerEntity villager = makeVillagerEntity(world, villagerUuid, player, name);
            villager.addCommandTag(QUEST_NPC);
            if (isStartNode)
                villager.addCommandTag(QUEST_START_NODE);
            world.spawnEntity(villager);
            MongoNpc mongoNpc = new MongoNpc(villagerUuid.toString(), name);
            NpcMongoClient.createNpc(mongoNpc);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned a Quest NPC!"), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int spawnEntityFromUUID(ServerCommandSource source, String uuid) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();

        try {
            MongoNpc mongoNpc = NpcMongoClient.getNpc(uuid);
            if (mongoNpc == null) {
                source.sendError(Text.literal("No NPC found with UUID: " + uuid));
                return 0;
            }
            VillagerEntity villager = makeVillagerEntity(world, UUID.fromString(mongoNpc.getUuid()), player, mongoNpc.getName());
            villager.addCommandTag(QUEST_NPC);
            if (mongoNpc.isStartNode()) {
                villager.addCommandTag(QUEST_START_NODE);
            }
            world.spawnEntity(villager);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned NPC with UUID: " + uuid), true);
        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull VillagerEntity makeVillagerEntity(World world, UUID uuid, ServerPlayerEntity player, String name) {
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.TRIGGERED);
        villager.setUuid(uuid);
        villager.refreshPositionAndAngles(player.getPos().x, player.getPos().y, player.getPos().z, player.getYaw(), player.getPitch());
        villager.setCustomName(Text.literal(name));
        villager.setAiDisabled(true);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setNoGravity(true);
        return villager;
    }


}

