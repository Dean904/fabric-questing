package bor.samsara.questing;

import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.scheduled.QuestRunnable;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
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

        CommandRegistrationCallback.EVENT.register(EventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(EventRegisters.openCommandBookForNpc());
        CommandRegistrationCallback.EVENT.register(EventRegisters.closeCommandBookForNpc());

        UseEntityCallback.EVENT.register(EventRegisters.rightClickQuestNpc());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            getOrMakePlayer(handler.getPlayer());
            // should the join event initialize quest trackers in an EventManager to minimize DB calls? I mean we're maybe talking ~60 players per instance?
            ModEntities.spawnTravelingWelcomer(server.getCommandSource());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ModEntities.despawnTravelingWelcomer(server.getCommandSource());
        });
    }

    private MongoPlayer getOrMakePlayer(ServerPlayerEntity serverPlayer) {
        try {
            return PlayerMongoClient.getPlayerByUuid(serverPlayer.getUuidAsString());
        } catch (IllegalStateException e) {
            String playerName = serverPlayer.getName().getLiteralString();
            log.info("{} joining for first time.", playerName);
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
        }
    }

    // TODO close mongo connection on close

}