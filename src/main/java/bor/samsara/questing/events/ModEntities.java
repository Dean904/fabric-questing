package bor.samsara.questing.events;

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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;


public class ModEntities {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String QUEST_NPC = "questNPC";
    public static final String QUEST_START_NODE = "questStartNode";

    private static final List<String> welcomingQuestUuids = getWelcomingQuestUuids();
    private static final Map<String, String> playerWelcomerMap = new HashMap<>();

    private ModEntities() {}

    public static void despawnTravelingWelcomer(ServerPlayerEntity player) {
        World world = player.getWorld();
        try {
            if (playerWelcomerMap.containsKey(player.getUuidAsString())) {
                NpcMongoClient.deleteNpc(playerWelcomerMap.get(player.getUuidAsString()));
                Entity trader = world.getEntity(UUID.fromString(playerWelcomerMap.get(player.getUuidAsString())));
                if (null != trader)
                    player.getServer().execute(trader::discard);
            }
        } catch (Exception e) {
            log.warn("Failed removing welcoming traveler: {}", e.getMessage(), e);
        }
        log.debug("RemovedWelcoming Traveler for {}", player.getName().getString());
    }

    public static void spawnWelcomingTraveler(ServerPlayerEntity player) {
        World world = player.getWorld();
        try {
            MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuidAsString());
            if (playerState.getActiveQuestProgressionMap().containsKey(welcomingQuestUuids.get(1)) &&
                    playerState.getActiveQuestProgressionMap().get(welcomingQuestUuids.get(1)).areAllObjectivesComplete()) {
                log.debug("{} has completed welcoming quest line.", player.getName().getString());
                return;
            }

            MongoNpc mongoNpc = getOrMakeWelcomeTravelerForPlayer(player);
            for (String questId : mongoNpc.getQuestIds()) {
                if (!playerState.getActiveQuestProgressionMap().containsKey(questId) || !playerState.getActiveQuestProgressionMap().get(questId).areAllObjectivesComplete()) {
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(questId);
                    playerState.setActiveQuest(mongoNpc.getUuid(), questId, new MongoPlayer.QuestProgress(questId, quest.getTitle(), quest.getSequence(), quest.getObjectives()));
                    PlayerMongoClient.updatePlayer(playerState);
                    SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, quest);
                    break;
                }
            }

            WanderingTraderEntity trader = makeWanderingTraderEntity(world, player, mongoNpc.getUuid());
            playerWelcomerMap.put(player.getUuidAsString(), trader.getUuidAsString());
            world.spawnEntity(trader);
            log.debug("Spawned a Welcoming Traveler for {}!", player.getName().getString());
        } catch (Exception e) {
            log.error("Failed spawning welcoming traverler: {}", e.getMessage(), e);
        }
    }

    private static MongoNpc getOrMakeWelcomeTravelerForPlayer(ServerPlayerEntity player) {
        try {
            return NpcMongoClient.getNpc(playerWelcomerMap.get(player.getUuidAsString()));
        } catch (IllegalStateException e) {
            MongoNpc mongoNpc = new MongoNpc(UUID.randomUUID().toString(), player.getName().getString() + " Welcoming Traveler");
            mongoNpc.setDialogueType("WELCOME");
            mongoNpc.setQuestIds(welcomingQuestUuids);
            NpcMongoClient.createNpc(mongoNpc);
            return mongoNpc;
        }
    }

    private static List<String> getWelcomingQuestUuids() {
        String travelerStartQuestTitle = "Traveler Greeting";
        String travelerEndQuestTitle = "Traveler Call To Action";
        String travelerFinishQuestTitle = "Traveler Farewell";

        try {
            MongoQuest qStart = QuestMongoClient.getQuestByTitle(travelerStartQuestTitle);
            MongoQuest qEnd = QuestMongoClient.getQuestByTitle(travelerEndQuestTitle);
            MongoQuest qFinish = QuestMongoClient.getQuestByTitle(travelerFinishQuestTitle);
            return List.of(qStart.getUuid(), qEnd.getUuid(), qFinish.getUuid());
        } catch (IllegalStateException e) {
            log.info("Creating Welcoming Traveler quests for the first time.");

            MongoQuest qStart = new MongoQuest();
            qStart.setTitle(travelerStartQuestTitle);
            qStart.setCategory(MongoQuest.Category.WELCOME);
            qStart.setSequence(0);
            qStart.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "WELCOME", 4)));
            qStart.setReward(new MongoQuest.Reward("minecraft:totem_of_undying", 1, 15));
            qStart.setProvidesQuestBook(false);
            qStart.setDialogue(List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "Quick, go talk to the old man in the village, Bondred.",
                    "It's dangerous to go alone! Take this.", "Go!"));
            QuestMongoClient.createQuest(qStart);

            MongoQuest qEnd = new MongoQuest();
            qEnd.setTitle(travelerEndQuestTitle);
            qEnd.setCategory(MongoQuest.Category.WELCOME);
            qEnd.setSequence(1);
            qEnd.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "Bondred", 1)));
            qEnd.setReward(new MongoQuest.Reward("none", 0, 0));
            qEnd.setDialogue(List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "Quick, go talk to the old man in the village, Bondred."));
            QuestMongoClient.createQuest(qEnd);

            MongoQuest qFinish = new MongoQuest();
            qFinish.setTitle(travelerFinishQuestTitle);
            qFinish.setCategory(MongoQuest.Category.WELCOME);
            qFinish.setSequence(2);
            qFinish.setProvidesQuestBook(false);
            qFinish.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.FIN, "", -1)));
            qFinish.setReward(new MongoQuest.Reward("none", 0, 0));
            qFinish.setDialogue(List.of("These are troubling times indeed.",
                    "I wonder, are you here because of the cataclysm, or are you the harbinger?",
                    "Don't you have something you should be doing?", "Is this some sort of game to you?"));
            QuestMongoClient.createQuest(qFinish);

            return List.of(qStart.getUuid(), qEnd.getUuid(), qFinish.getUuid());
        }
    }

    private static WanderingTraderEntity makeWanderingTraderEntity(World world, ServerPlayerEntity player, String uuid) {
        WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world, SpawnReason.TRIGGERED);
        trader.setUuid(UUID.fromString(uuid));

        Vec3d forward = player.getRotationVec(1.0f).normalize();
        trader.refreshPositionAndAngles(player.getPos().x + forward.x * 2, player.getPos().y, player.getPos().z + forward.z * 2, player.getYaw(), player.getPitch());
        trader.setCustomName(Text.literal("§ Hey, " + player.getName().getString() + "!"));
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
            VillagerEntity villager = makeVillagerEntity(world, UUID.fromString(mongoNpc.getUuid()), player, mongoNpc.getName());
            villager.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 5, false, false));
            villager.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, Integer.MAX_VALUE, 99, false, false));
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

