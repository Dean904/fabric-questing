package bor.samsara.questing;

import bor.samsara.questing.events.QuestCreationEventRegisters;
import bor.samsara.questing.hearth.HearthStoneEventRegisters;
import bor.samsara.questing.settings.AppConfiguration;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.events.RightClickActionEventManager;
import bor.samsara.questing.events.subject.CollectItemSubject;
import bor.samsara.questing.events.subject.KillSubject;
import bor.samsara.questing.events.subject.TalkToNpcSubject;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SamsaraFabricQuesting implements ModInitializer {

    public static final String MOD_ID = "samsara";
    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final KillSubject killSubject = new KillSubject();
    public static final CollectItemSubject collectItemSubject = new CollectItemSubject();
    public static final TalkToNpcSubject talkToNpcSubject = new TalkToNpcSubject();

    // TODO optionally render invisibile item frame wearing quest ! / ? for players based on quest status
    // TODO close mongo connection on close

    @Override
    public void onInitialize() {
        log.info("Initializing SamsaraFabricQuesting !!!");
        AppConfiguration.loadConfiguration();

        // ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        // scheduler.scheduleAtFixedRate(new QuestRunnable(), 0, 1, TimeUnit.MINUTES);

        UseItemCallback.EVENT.register(HearthStoneEventRegisters.useHearthstone());
        CommandRegistrationCallback.EVENT.register(HearthStoneEventRegisters.createHearthstone());


        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openCommandBookForNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.setQuestTrigger());

        UseItemCallback.EVENT.register(QuestCreationEventRegisters.updateQuestLogWhenOpened());

        UseEntityCallback.EVENT.register(RightClickActionEventManager.rightClickQuestNpc());
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(killSubject.hook());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            getOrMakePlayerOnJoin(handler.getPlayer());
            ModEntities.spawnWelcomingTraveler(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            savePlayerStatsOnExit(handler.getPlayer());
            ModEntities.despawnTravelingWelcomer(handler.getPlayer());
        });
    }

    private static MongoPlayer getOrMakePlayerOnJoin(ServerPlayerEntity serverPlayer) {
        try {
            MongoPlayer mongoPlayer = PlayerMongoClient.getPlayerByUuid(serverPlayer.getUuidAsString());
            registerPlayerQuests(mongoPlayer);
            return mongoPlayer;
        } catch (IllegalStateException e) {
            log.debug(e.getMessage());
            String playerName = serverPlayer.getName().getLiteralString();
            log.info("{} joining for first time.", playerName);
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
        }
    }

    private static void registerPlayerQuests(MongoPlayer mongoPlayer) {
        for (MongoPlayer.QuestProgress questProgress : mongoPlayer.getQuestPlayerProgressMap().values()) {
            if (!questProgress.isComplete()) {
                try {
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(questProgress.getQuestUuid());
                    attachQuestListenerToPertinentSubject(mongoPlayer, quest);
                } catch (Exception e) {
                    log.error("Failed to attach questProgress listener for player {} on questProgress {}: {}", mongoPlayer.getName(), questProgress, e.getMessage(), e);
                }
            }
        }
    }

    public static void attachQuestListenerToPertinentSubject(MongoPlayer playerState, MongoQuest quest) {
        ActionSubscription actionSubscription = new ActionSubscription(playerState.getUuid(), quest.getUuid(), quest.getObjective());
        MongoQuest.Objective.Type objectiveType = quest.getObjective().getType();
        switch (objectiveType) {
            case KILL -> SamsaraFabricQuesting.killSubject.attach(actionSubscription);
            case COLLECT -> SamsaraFabricQuesting.collectItemSubject.attach(actionSubscription);
            case TALK -> SamsaraFabricQuesting.talkToNpcSubject.attach(actionSubscription);
            case FIN -> {}
            default -> log.warn("Unknown Objective Type '{}' when registering Quest {} for Player {}", objectiveType, quest.getTitle(), playerState.getName());
        }
    }

    private static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        String playerUuid = serverPlayer.getUuidAsString();
        PlayerMongoClient.updatePlayer(PlayerMongoClient.getPlayerByUuid(playerUuid));
        PlayerMongoClient.unloadPlayer(playerUuid);
        SamsaraFabricQuesting.killSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.collectItemSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.talkToNpcSubject.detachPlayer(playerUuid);
    }

}

