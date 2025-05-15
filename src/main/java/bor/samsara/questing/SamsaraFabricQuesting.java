package bor.samsara.questing;

import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.events.QuestActionEventManager;
import bor.samsara.questing.events.concrete.CollectItemSubject;
import bor.samsara.questing.events.concrete.KillSubject;
import bor.samsara.questing.events.concrete.TalkToNpcSubject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class SamsaraFabricQuesting implements ModInitializer {

    public static final String MOD_ID = "samsara";
    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final KillSubject killSubject = new KillSubject();
    public static final CollectItemSubject collectItemSubject = new CollectItemSubject();
    public static final TalkToNpcSubject talkToNpcSubject = new TalkToNpcSubject();

    // BIG TODO optionally render invisibile item frame wearing quest ! / ? for players based on quest status

    @Override
    public void onInitialize() {
        log.info("Initializing SamsaraFabricQuesting !!!");

        // ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        // scheduler.scheduleAtFixedRate(new QuestRunnable(), 0, 1, TimeUnit.MINUTES);

        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.createNpc());
        CommandRegistrationCallback.EVENT.register(QuestCreationEventRegisters.openCommandBookForNpc());

        UseEntityCallback.EVENT.register(QuestActionEventManager.rightClickQuestNpc());
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(killSubject.hook());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            QuestActionEventManager.getOrMakePlayerOnJoin(handler.getPlayer());
            ModEntities.spawnWelcomingTraveler(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QuestActionEventManager.savePlayerStatsOnExit(handler.getPlayer());
            ModEntities.despawnTravelingWelcomer(handler.getPlayer());
        });
    }

}


// TODO close mongo connection on close
