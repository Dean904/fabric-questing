package bor.samsara.questing.events;

import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestActionEventManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    // TODO save stats on exit


    public static MongoPlayer getOrMakePlayerOnJoin(ServerPlayerEntity serverPlayer) {
        try {
            QuestManager questManager = QuestManager.getInstance();
            MongoPlayer playerByUuid = PlayerMongoClient.getPlayerByUuid(serverPlayer.getUuidAsString());
            questManager.playerMap.put(serverPlayer.getUuidAsString(), playerByUuid);
            return playerByUuid;
        } catch (IllegalStateException e) {
            String playerName = serverPlayer.getName().getLiteralString();
            log.info("{} joining for first time.", playerName);
            MongoPlayer p = new MongoPlayer(serverPlayer.getUuidAsString(), playerName);
            PlayerMongoClient.createPlayer(p);
            return p;
        }
    }

    public static @NotNull UseEntityCallback rightClickQuestNpc() {
        return (PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {
            if (null != hitResult && entity.getCommandTags().contains("questNPC")) {
                String playerUuid = player.getUuid().toString();
                String questNpcUuid = entity.getUuid().toString();

                String dialogue = QuestManager.getInstance().getNextDialogue(playerUuid, questNpcUuid);
                if (StringUtils.isNotBlank(dialogue))
                    player.sendMessage(Text.literal(dialogue), false);

                player.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        };
    }

    public static ServerEntityCombatEvents.AfterKilledOtherEntity afterKilledOtherEntity() {
        return (world, killer, killedEntity) -> {
            if (!(killer instanceof ServerPlayerEntity)) {
                return;
            }

            ServerPlayerEntity player = (ServerPlayerEntity) killer;
            String playerUuid = player.getUuid().toString();

            // Retrieve the player state from your QuestManager in-memory store.
            QuestManager questManager = QuestManager.getInstance();
            MongoPlayer mongoPlayer = questManager.playerMap.get(playerUuid); // TODO weird access

            // TODO Subscription based notification system for determining if an event is pertient to updating quests

            if (killedEntity instanceof ZombieEntity) {
                // For each active quest registered for this player, increment the objective count.
                for (String questNpcUuid : mongoPlayer.getNpcActiveQuest().keySet()) {
                    questManager.incrementQuestObjectiveCount(playerUuid, questNpcUuid);
                }
            }
        };
    }

    public static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        QuestManager questManager = QuestManager.getInstance();
        PlayerMongoClient.updatePlayer(questManager.playerMap.get(serverPlayer.getUuidAsString()));
    }
}
