package bor.samsara.questing;

import bor.samsara.questing.events.*;
import bor.samsara.questing.events.subject.*;
import bor.samsara.questing.hearth.HearthStoneEventRegisters;
import bor.samsara.questing.hearth.SoulStoneEventRegisters;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import bor.samsara.questing.settings.AppConfiguration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SamsaraFabricQuesting implements ModInitializer {

    public static final String MOD_ID = "samsara";
    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final KillSubject killSubject = new KillSubject();
    public static final CollectItemSubject collectItemSubject = new CollectItemSubject();
    public static final TalkToNpcSubject talkToNpcSubject = new TalkToNpcSubject();
    public static final DoQuestSubject doQuestSubject = new DoQuestSubject();
    public static final SetSpawnSubject setSpawnSubject = new SetSpawnSubject();
    public static final BreakBlockSubject breakBlockSubject = new BreakBlockSubject();

    public static final Queue<Runnable> questRunnables = new LinkedList<>();
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // TODO optionally render invisibile item frame wearing quest ! / ? for players based on quest status

    @Override
    public void onInitialize() {
        log.info("Initializing SamsaraFabricQuesting !!!");
        AppConfiguration.loadConfiguration();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            while (!questRunnables.isEmpty()) {
                Runnable runnable = questRunnables.poll();
                try {
                    runnable.run();
                } catch (Exception e) {
                    log.error("Error executing quest runnable: {}", e.getMessage(), e);
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MongoPlayer player = getOrMakePlayerOnJoin(handler.getPlayer());
            if (AppConfiguration.getBoolConfig(AppConfiguration.IS_WELCOMER_ENABLED))
                WelcomingTraveler.spawn(handler.getPlayer(), player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            savePlayerStatsOnExit(handler.getPlayer());
            WelcomingTraveler.despawn(handler.getPlayer());
        });

        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.setQuestTrigger());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openQuestLogForPlayer());

        UseEntityCallback.EVENT.register(RightClickActionEventManager.rightClickQuestNpc());
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(killSubject.processEntityKill());
        PlayerBlockBreakEvents.AFTER.register(breakBlockSubject.processAfterBlockBreak());

        UseItemCallback.EVENT.register(QuestCreationEventRegisters.updateQuestLogWhenOpened());
        UseBlockCallback.EVENT.register(RightClickActionEventManager.evaporateBucketInNether());

        ServerLivingEntityEvents.AFTER_DEATH.register(SoulStoneEventRegisters.saveDeathLocation());
        CommandRegistrationCallback.EVENT.register(SoulStoneEventRegisters.createSoulstone());
        UseItemCallback.EVENT.register(SoulStoneEventRegisters.useSoulstone());
        UseItemCallback.EVENT.register(HearthStoneEventRegisters.useHearthstone());
        CommandRegistrationCallback.EVENT.register(HearthStoneEventRegisters.createHearthstone());
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
            String firstJoinCommandBlock = AppConfiguration.getConfiguration(AppConfiguration.FIRST_JOIN_COMMAND);
            if (StringUtils.isNotBlank(firstJoinCommandBlock)) {
                CommandManager commandManager = serverPlayer.getEntityWorld().getServer().getCommandManager();
                ServerCommandSource commandSource = serverPlayer.getEntityWorld().getServer().getCommandSource();
                for (String command : firstJoinCommandBlock.split(";"))
                    commandManager.parseAndExecute(commandSource, command);
            }
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
        }
    }

    private static void registerPlayerQuests(MongoPlayer mongoPlayer) {
        for (MongoPlayer.ActiveQuestState activeQuestState : mongoPlayer.getActiveQuestProgressionMap().values()) {
            try {
                MongoQuest quest = QuestMongoClient.getQuestByUuid(activeQuestState.getQuestUuid());
                attachQuestListenerToPertinentSubject(mongoPlayer, quest);
            } catch (Exception e) {
                log.error("Failed to attach questProgress listener for player {} on questProgress {}: {}", mongoPlayer.getName(), activeQuestState, e.getMessage(), e);
            }
        }
    }

    public static void attachQuestListenerToPertinentSubject(MongoPlayer playerState, MongoQuest quest) {
        for (MongoQuest.Objective objective : quest.getObjectives()) {
            ActionSubscription actionSubscription = new ActionSubscription(playerState.getUuid(), quest.getUuid(), objective.getTarget());
            MongoQuest.Objective.Type objectiveType = objective.getType();
            switch (objectiveType) {
                case KILL -> SamsaraFabricQuesting.killSubject.attach(actionSubscription);
                case COLLECT -> SamsaraFabricQuesting.collectItemSubject.attach(actionSubscription);
                case TALK -> SamsaraFabricQuesting.talkToNpcSubject.attach(actionSubscription);
                case DO_QUEST -> SamsaraFabricQuesting.doQuestSubject.attach(actionSubscription, playerState);
                case SET_SPAWN -> SamsaraFabricQuesting.setSpawnSubject.attach(actionSubscription);
                case BREAK_BLOCK -> SamsaraFabricQuesting.breakBlockSubject.attach(actionSubscription);
                default -> log.warn("Unknown Objective Type '{}' when registering Quest {} for Player {}", objectiveType, quest.getTitle(), playerState.getName());
            }
        }
    }

    private static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        String playerUuid = serverPlayer.getUuidAsString();
        SamsaraFabricQuesting.killSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.collectItemSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.talkToNpcSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.doQuestSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.setSpawnSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.breakBlockSubject.detachPlayer(playerUuid);
    }

}
