package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import bor.samsara.questing.settings.AppConfiguration;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static bor.samsara.questing.events.ModEntities.QUEST_NPC;

public class WelcomingTraveler {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final String WELCOMER_PROTO_TYPE_NAME = "Welcoming Traveler Prototype";
    public static final String DEFAULT_CALL_TO_ACTION_QUEST_TITLE = "Traveler Call To Action";
    private static String requiredWelcomeQuestUuid = null;

    private static final Map<String, String> playerWelcomerMap = new HashMap<>();

    public static void despawn(ServerPlayerEntity player) {
        try {
            if (playerWelcomerMap.containsKey(player.getUuidAsString())) {
                log.debug("Removing Welcoming Traveler for {}", player.getName().getString());
                String welcomerUuid = playerWelcomerMap.get(player.getUuidAsString());
                NpcMongoClient.deleteNpc(welcomerUuid);

                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuidAsString());
                playerState.removeActiveQuestForNpc(welcomerUuid);
                PlayerMongoClient.updatePlayer(playerState);

                World world = player.getWorld();
                Entity trader = world.getEntity(UUID.fromString(welcomerUuid));
                if (null != trader)
                    player.getServer().execute(trader::discard);

            }
        } catch (Exception e) {
            log.warn("Failed removing welcoming traveler: {}", e.getMessage(), e);
        }
    }

    public static void spawn(ServerPlayerEntity player, MongoPlayer playerState) {
        try {
            if (playerState.isQuestComplete(getRequiredWelcomeQuestUuid())) {
                log.debug("{} has completed welcoming quest line.", player.getName().getString());
                return;
            }

            MongoNpc mongoNpc = makeTemporaryWelcomeTravelerForPlayer(player);
            NpcMongoClient.createNpc(mongoNpc);
            for (String questId : mongoNpc.getQuestIds()) {
                if (!playerState.isQuestComplete(questId)) {
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(questId);
                    playerState.setCurrentQuestForNpc(mongoNpc.getUuid(), questId);
                    playerState.attachActiveQuestState(new MongoPlayer.ActiveQuestState(quest));
                    PlayerMongoClient.updatePlayer(playerState);
                    SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, quest);
                    break;
                }
            }

            World world = player.getWorld();
            WanderingTraderEntity trader = makeWanderingTraderEntity(world, player, mongoNpc.getUuid());
            playerWelcomerMap.put(player.getUuidAsString(), trader.getUuidAsString());
            world.spawnEntity(trader);
            log.debug("Spawned a Welcoming Traveler for {}!", player.getName().getString());
        } catch (Exception e) {
            log.error("Failed spawning welcoming traverler: {}", e.getMessage(), e);
        }
    }

    private static MongoNpc makeTemporaryWelcomeTravelerForPlayer(ServerPlayerEntity player) {
        MongoNpc prototype = getOrMakePrototypeWelcomeTraveler();
        MongoNpc mongoNpc = new MongoNpc(UUID.randomUUID().toString(), player.getName().getString() + " Welcoming Traveler");
        mongoNpc.setDialogueType(prototype.getDialogueType());
        mongoNpc.setQuestIds(prototype.getQuestIds());
        mongoNpc.setStartNode(prototype.isStartNode());
        return mongoNpc;
    }

    private static String getRequiredWelcomeQuestUuid() {
        if (null == requiredWelcomeQuestUuid) {
            getOrMakePrototypeWelcomeTraveler(); // ensure default quests are created
            MongoQuest quest = QuestMongoClient.getQuestByTitle(AppConfiguration.getConfiguration(AppConfiguration.REQUIRED_WELCOME_QUEST_TITLE));
            requiredWelcomeQuestUuid = quest.getUuid();
        }
        return requiredWelcomeQuestUuid;
    }

    private static MongoNpc getOrMakePrototypeWelcomeTraveler() {
        try {
            return NpcMongoClient.getNpcByName(WELCOMER_PROTO_TYPE_NAME);
        } catch (IllegalStateException e) {
            MongoNpc mongoNpc = new MongoNpc(UUID.randomUUID().toString(), WELCOMER_PROTO_TYPE_NAME);
            mongoNpc.setDialogueType("WELCOME");
            mongoNpc.setQuestIds(getOrMakeWelcomingQuestUuids());
            NpcMongoClient.createNpc(mongoNpc);
            return mongoNpc;
        }
    }

    // TODO update reward to HearthStone via /give trigger?
    //    BlockPos spawnHengeAltarPos = new BlockPos(-717, 126, 543);
    //    ItemStack hearthstone = HearthStoneEventRegisters.createHearthstoneItem("SpawnHenge", spawnHengeAltarPos);
    private static List<String> getOrMakeWelcomingQuestUuids() {
        String travelerStartQuestTitle = "Traveler Greeting";
        String travelerFarewellQuestTitle = "Traveler Farewell";

        try {
            MongoQuest qStart = QuestMongoClient.getQuestByTitle(travelerStartQuestTitle);
            MongoQuest qEnd = QuestMongoClient.getQuestByTitle(DEFAULT_CALL_TO_ACTION_QUEST_TITLE);
            MongoQuest qFinish = QuestMongoClient.getQuestByTitle(travelerFarewellQuestTitle);
            return List.of(qStart.getUuid(), qEnd.getUuid(), qFinish.getUuid());
        } catch (IllegalStateException e) {
            log.info("Creating Welcoming Traveler quests for the first time.");

            MongoQuest qStart = new MongoQuest(UUID.randomUUID().toString());
            qStart.setTitle(travelerStartQuestTitle);
            qStart.setCategory(MongoQuest.CategoryEnum.WELCOME);
            qStart.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "WELCOME", 4)));
            qStart.setReward(new MongoQuest.Reward("minecraft:totem_of_undying", 1, 15));
            qStart.setProvidesQuestBook(false);
            qStart.setDialogue(List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "Quick, go talk to the old man in the village, Bondred.",
                    "It's dangerous to go alone! Take this.", "Go!"));
            QuestMongoClient.createQuest(qStart);

            MongoQuest qEnd = new MongoQuest(UUID.randomUUID().toString());
            qEnd.setTitle(DEFAULT_CALL_TO_ACTION_QUEST_TITLE);
            qEnd.setCategory(MongoQuest.CategoryEnum.WELCOME);
            qEnd.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "Bondred", 1)));
            qEnd.setReward(null);
            qStart.setProvidesQuestBook(true);
            qEnd.setDialogue(List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "Quick, go talk to the old man in the village, Bondred."));
            QuestMongoClient.createQuest(qEnd);

            MongoQuest qFinish = new MongoQuest(UUID.randomUUID().toString());
            qFinish.setTitle(travelerFarewellQuestTitle);
            qFinish.setCategory(MongoQuest.CategoryEnum.END);
            qFinish.setProvidesQuestBook(false);
            qFinish.setReward(null);
            qFinish.setDialogue(List.of("These are troubling times indeed.",
                    "I wonder, are you here because of the cataclysm, or are you the harbinger?",
                    "Don't you have something you should be doing?",
                    "Is this some sort of game to you?"));
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

        trader.setDespawnDelay(300 * 20); // despawn after 5 minutes, 20 ticks per second
        trader.attachLeash(player, true); // tether trader to player
        trader.setTarget(player);

        return trader;
    }

}
