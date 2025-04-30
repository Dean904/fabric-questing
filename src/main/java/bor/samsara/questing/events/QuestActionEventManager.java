package bor.samsara.questing.events;

import bor.samsara.questing.events.concrete.QuestManager;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
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

    // TODO do these functions belong here? Or in QuestManager? bad abstraction
    public static MongoPlayer getOrMakePlayerOnJoin(ServerPlayerEntity serverPlayer) {
        try {
            QuestManager questManager = QuestManager.getInstance();
            MongoPlayer playerByUuid = PlayerMongoClient.getPlayerByUuid(serverPlayer.getUuidAsString());
            questManager.activatePlayer(serverPlayer.getUuidAsString(), playerByUuid);
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
                // TODO Add && tag of QUEST_START_POINT ?
                String playerUuid = player.getUuid().toString();
                String questNpcUuid = entity.getUuid().toString();

                // A Quest NPC needs 2 or 3 states per player
                //          , uninitiated giver, target npc (dependent quest), finished?

                QuestManager questManager = QuestManager.getInstance();
                if (!questManager.isNpcActiveForPlayer(playerUuid, questNpcUuid)) {
                    questManager.registerNpcForPlayer(playerUuid, questNpcUuid);
                }

                String dialogue = questManager.getNextDialogue(playerUuid, questNpcUuid);
                if (StringUtils.isNotBlank(dialogue))
                    player.sendMessage(Text.literal(dialogue), false);

                player.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                return ActionResult.SUCCESS; // prevents other actions from performing.
            }
            return ActionResult.PASS;
        };
    }

    public static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        QuestManager questManager = QuestManager.getInstance();
        questManager.deactivatePlayer(serverPlayer.getUuidAsString());
    }
}
