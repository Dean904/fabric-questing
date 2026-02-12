package bor.samsara.questing.events;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import bor.samsara.questing.settings.AppConfiguration;
import com.mongodb.MongoWriteException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.HoldInHandsGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static bor.samsara.questing.events.QuestNpcs.QUEST_NPC;

public class WelcomingTraveler {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final String WELCOMER_PROTO_TYPE_NAME = "Welcoming Traveler Prototype";
    public static final String REQUIRED_WELCOMER_QUEST = AppConfiguration.getConfiguration(AppConfiguration.REQUIRED_WELCOME_QUEST_TITLE);
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

                World world = player.getEntityWorld();
                Entity trader = world.getEntity(UUID.fromString(welcomerUuid));
                if (null != trader)
                    player.getEntityWorld().getServer().execute(trader::discard);

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
            try {
                NpcMongoClient.createNpc(mongoNpc);
            } catch (MongoWriteException e) {
                log.warn("Welcoming Traveler {} already exists. Deleting and trying again: {}", mongoNpc.getName(), e.getMessage());
                MongoNpc dupe = NpcMongoClient.getFirstNpcByName(mongoNpc.getName());
                NpcMongoClient.deleteNpc(dupe.getUuid());
                NpcMongoClient.createNpc(mongoNpc);
            }
            RightClickActionEventManager.progressPlayerToNextIncompleteQuest(player, playerState, mongoNpc);

            World world = player.getEntityWorld();
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
            MongoQuest quest = QuestMongoClient.getQuestByTitle(REQUIRED_WELCOMER_QUEST);
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
            mongoNpc.setQuestIds(getOrMakeWelcomingQuests());
            NpcMongoClient.createNpc(mongoNpc);
            return mongoNpc;
        }
    }

    private static List<String> getOrMakeWelcomingQuests() {
        String travelerFarewellQuestTitle = "Traveler Farewell";
        MongoQuest qBegin, qFinish;

        try {
            qBegin = QuestMongoClient.getQuestByTitle(REQUIRED_WELCOMER_QUEST);
        } catch (IllegalStateException e) {
            log.info("Creating Welcoming quest {}", REQUIRED_WELCOMER_QUEST);

            qBegin = new MongoQuest(UUID.randomUUID().toString());
            qBegin.setTitle(REQUIRED_WELCOMER_QUEST);
            qBegin.setCategory(MongoQuest.CategoryEnum.MAIN);
            qBegin.setObjectives(List.of(new MongoQuest.Objective(MongoQuest.Objective.Type.TALK, "Bondred", 1)));
            qBegin.setSubmissionTarget("Bondred");
            qBegin.setReward(new MongoQuest.Reward("minecraft:emerald", 2, 30));
            qBegin.setProvidesQuestBook(true);
            qBegin.addTriggers(MongoQuest.EventTrigger.ON_DIALOGUE_DONE, List.of(
                    "/summon minecraft:lightning_bolt",
                    "/particle minecraft:gust @npcLoc",
                    "/tp @npc ~ ~500 ~",
                    "/kill @npc",
                    "/playsound minecraft:item.chorus_fruit.teleport player @p"));
            qBegin.addTriggers(MongoQuest.EventTrigger.ON_BOOK_GRANT, List.of(
                    "/reward minecraft:bundle{minecraft:copper_sword[enchants=unbreaking:3;smite:2;looting:1],1} 1"));
            qBegin.setDialogue(List.of("What are you doing here?!?",
                    "This must mean the cycle has started again.",
                    "You must go talk to the §3old man in the village§r at once.",
                    "§aBondred§r is at §aThe Lions Pride Inn§r just bellow this ruin. But before you go...",
                    "..it's §cdangerous§r to go alone! Take this."));

            QuestMongoClient.createQuest(qBegin);
        }

        try {
            qFinish = QuestMongoClient.getQuestByTitle(travelerFarewellQuestTitle);
        } catch (IllegalStateException e) {
            log.info("Creating Welcoming Traveler quest {}", travelerFarewellQuestTitle);
            qFinish = new MongoQuest(UUID.randomUUID().toString());
            qFinish.setTitle(travelerFarewellQuestTitle);
            qFinish.setCategory(MongoQuest.CategoryEnum.END);
            qFinish.setProvidesQuestBook(false);
            qFinish.setReward(null);
            qFinish.setDialogue(List.of("These are troubling times indeed.",
                    "I wonder, are you here because of the cataclysm, or are you the harbinger?",
                    "Don't you have something you should be doing?",
                    "Is this some sort of game to you?"));
            QuestMongoClient.createQuest(qFinish);
        }

        return List.of(qBegin.getUuid(), qFinish.getUuid());
    }

    private static WanderingTraderEntity makeWanderingTraderEntity(World world, ServerPlayerEntity player, String uuid) {
        WanderingTraderEntity trader = EntityType.WANDERING_TRADER.create(world, SpawnReason.TRIGGERED);
        trader.setUuid(UUID.fromString(uuid));
        trader.addCommandTag(QUEST_NPC);

        Vec3d forward = player.getRotationVec(1.0f).normalize();
        trader.refreshPositionAndAngles(player.getEntityPos().x + forward.x * 3, player.getEntityPos().y, player.getEntityPos().z + forward.z * 3, player.getYaw(), player.getPitch());
        trader.setCustomName(Text.literal("§ Hey, " + player.getName().getString() + "!"));
        trader.setCustomNameVisible(true);
        trader.setGlowing(true);
        trader.getOffers().clear();
        trader.setDespawnDelay(200 * 20); // despawn after ~3 minutes, 20 ticks per second

        trader.setCustomer(player);
        trader.clearGoals(HoldInHandsGoal.class::isInstance);
        trader.clearGoals(StopFollowingCustomerGoal.class::isInstance);
        trader.clearGoals(WanderAroundFarGoal.class::isInstance);

        trader.attachLeash(player, false);
        trader.lookAtEntity(player, 360, 360);

        return trader;
    }

}
