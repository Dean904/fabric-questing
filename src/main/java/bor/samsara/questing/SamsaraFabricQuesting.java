package bor.samsara.questing;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.events.ModEntities;
import bor.samsara.questing.events.QuestCreationEventRegisters;
import bor.samsara.questing.events.RightClickActionEventManager;
import bor.samsara.questing.events.subject.CollectItemSubject;
import bor.samsara.questing.events.subject.KillSubject;
import bor.samsara.questing.events.subject.TalkToNpcSubject;
import bor.samsara.questing.hearth.HearthStoneEventRegisters;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import bor.samsara.questing.settings.AppConfiguration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

public class SamsaraFabricQuesting implements ModInitializer {

    public static final String MOD_ID = "samsara";
    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final KillSubject killSubject = new KillSubject();
    public static final CollectItemSubject collectItemSubject = new CollectItemSubject();
    public static final TalkToNpcSubject talkToNpcSubject = new TalkToNpcSubject();

    public static final Queue<Runnable> questRunnables = new LinkedList<>();

    // TODO optionally render invisibile item frame wearing quest ! / ? for players based on quest status
    // TODO close mongo connection on close

    @Override
    public void onInitialize() {
        log.info("Initializing SamsaraFabricQuesting !!!");
        AppConfiguration.loadConfiguration();

        // ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        // scheduler.scheduleAtFixedRate(new QuestRunnable(), 0, 1, TimeUnit.MINUTES);

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

        UseItemCallback.EVENT.register(HearthStoneEventRegisters.useHearthstone());
        CommandRegistrationCallback.EVENT.register(HearthStoneEventRegisters.createHearthstone());

        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openCommandBookForNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.setQuestTrigger());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openQuestLogForPlayer());

        UseItemCallback.EVENT.register(QuestCreationEventRegisters.updateQuestLogWhenOpened());
        UseBlockCallback.EVENT.register(RightClickActionEventManager.evaporateBucketInNether());
        UseEntityCallback.EVENT.register(RightClickActionEventManager.rightClickQuestNpc());
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(killSubject.hook());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MongoPlayer player = getOrMakePlayerOnJoin(handler.getPlayer());
            ModEntities.spawnWelcomingTraveler(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            savePlayerStatsOnExit(handler.getPlayer());
            ModEntities.despawnTravelingWelcomer(handler.getPlayer());
        });
    }

    private void giveHearthStone(ServerPlayerEntity player) {
        BlockPos spawnHengeAltarPos = new BlockPos(-717, 126, 543);
        ItemStack hearthstone = HearthStoneEventRegisters.createHearthstoneItem("SpawnHenge", spawnHengeAltarPos);
        player.getInventory().insertStack(hearthstone);
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
            CommandManager commandManager = serverPlayer.getServer().getCommandManager();
            ServerCommandSource commandSource = serverPlayer.getServer().getCommandSource();
            commandManager.executeWithPrefix(commandSource, "/time set 23300");
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
        }
    }

    private static void registerPlayerQuests(MongoPlayer mongoPlayer) {
        for (MongoPlayer.ActiveQuestState activeQuestState : mongoPlayer.getActiveQuestProgressionMap().values()) {
            if (!activeQuestState.areAllObjectivesComplete()) {
                try {
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(activeQuestState.getQuestUuid());
                    attachQuestListenerToPertinentSubject(mongoPlayer, quest);
                } catch (Exception e) {
                    log.error("Failed to attach questProgress listener for player {} on questProgress {}: {}", mongoPlayer.getName(), activeQuestState, e.getMessage(), e);
                }
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
                case FIN -> {}
                default -> log.warn("Unknown Objective Type '{}' when registering Quest {} for Player {}", objectiveType, quest.getTitle(), playerState.getName());
            }
        }
    }

    private static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        String playerUuid = serverPlayer.getUuidAsString();
        //PlayerMongoClient.updatePlayer(PlayerMongoClient.getPlayerByUuid(playerUuid));
        SamsaraFabricQuesting.killSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.collectItemSubject.detachPlayer(playerUuid);
        SamsaraFabricQuesting.talkToNpcSubject.detachPlayer(playerUuid);
    }

}
