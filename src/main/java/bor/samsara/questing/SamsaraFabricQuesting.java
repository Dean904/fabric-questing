package bor.samsara.questing;

import bor.samsara.questing.config.AppConfiguration;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.events.RightClickActionEventManager;
import bor.samsara.questing.events.concrete.CollectItemSubject;
import bor.samsara.questing.events.concrete.KillSubject;
import bor.samsara.questing.events.concrete.QuestManager;
import bor.samsara.questing.events.concrete.TalkToNpcSubject;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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

        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openCommandBookForNpc());

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
            // TODO activatePlayer throwing exception and dupping players if NPC does not exist...
            for (Map.Entry<String, MongoPlayer.ActiveQuest> activeQuestKv : mongoPlayer.getNpcActiveQuestMap().entrySet()) {
                String questNpcUuid = activeQuestKv.getKey();
                MongoQuest quest = QuestMongoClient.getQuestByUuid(activeQuestKv.getValue().getQuestUuid());
                MongoNpc questNpc = NpcMongoClient.getNpc(questNpcUuid);
                QuestManager.attachQuestListenerToPertinentSubject(mongoPlayer, questNpc, quest.getObjective());
            }
            return mongoPlayer;
        } catch (IllegalStateException e) {
            String playerName = serverPlayer.getName().getLiteralString();
            log.info("{} joining for first time.", playerName);
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
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

