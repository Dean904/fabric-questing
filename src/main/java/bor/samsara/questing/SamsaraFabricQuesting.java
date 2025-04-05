package bor.samsara.questing;

import bor.samsara.questing.events.QuestActionEventManager;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.scheduled.QuestRunnable;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class SamsaraFabricQuesting implements ModInitializer {

    public static final String MOD_ID = "samsara";
    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String bot_token = "MTE5MDM0MzM1MDM2NTIxMjY5Mw.Gjq9qk.6MWgRRcifLAe_CC1Eof-ZEM36GviJ40FMjGzGk";

    public static final AtomicInteger playerOnlineCount = new AtomicInteger();

    // BIG TODO optionally render invisibile item frame wearing quest ! / ? for players based on quest status

    @Override
    public void onInitialize() {
        log.info("Initializing SamsaraDiscordGaming !!!");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleAtFixedRate(new QuestRunnable(), 0, 1, TimeUnit.MINUTES);

        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openCommandBookForNpc());

        UseEntityCallback.EVENT.register(QuestActionEventManager.rightClickQuestNpc());
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(QuestActionEventManager.afterKilledOtherEntity());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            QuestActionEventManager.getOrMakePlayerOnJoin(handler.getPlayer());
            // should the join event initialize quest trackers in an EventManager to minimize DB calls? I mean we're maybe talking ~60 players per instance?
            ModEntities.spawnTravelingWelcomer(server.getCommandSource());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QuestActionEventManager.savePlayerStatsOnExit(handler.getPlayer());
            ModEntities.despawnTravelingWelcomer(server.getCommandSource());
        });
    }


    // TODO close mongo connection on close

}