package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.events.concrete.QuestManager;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
            if (null != hitResult && entity.getCommandTags().contains(ModEntities.QUEST_NPC)) {
                String playerUuid = player.getUuid().toString();
                String questNpcUuid = entity.getUuid().toString();
                QuestManager questManager = QuestManager.getInstance();

                // TODO A Quest NPC needs 2 or 3 states per player ?
                //          , uninitiated giver, target npc (dependent quest), finished?

                SamsaraFabricQuesting.talkToNpcSubject.talkedToQuestNpc(player, world, hand, entity, hitResult);

                if (!questManager.isNpcActiveForPlayer(playerUuid, questNpcUuid) && entity.getCommandTags().contains(ModEntities.QUEST_START_NODE)) {
                    questManager.registerNpcForPlayer(playerUuid, questNpcUuid);
                    player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }

                if (questManager.isNpcActiveForPlayer(playerUuid, questNpcUuid)) {
                    if (questManager.isQuestCompleteForPlayer(playerUuid, questNpcUuid)) {
                        MongoNpc.Quest.Reward reward = questManager.getQuestReward(playerUuid, questNpcUuid);
                        if (!StringUtils.equals(reward.getItemName(), "none")) {
                            player.addExperience(reward.getXpValue());
                            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                            ItemStack stack = getItemStack(reward);
                            boolean added = player.giveItemStack(stack);
                            if (!added) {
                                player.dropItem(stack, false);
                            }
                        }
                        questManager.progressPlayerToNextQuestSequence(playerUuid, questNpcUuid);
                    }

                    String dialogue = questManager.getNextDialogue(playerUuid, questNpcUuid);
                    if (StringUtils.isNotBlank(dialogue))
                        player.sendMessage(Text.literal(dialogue), false);

                    player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    return ActionResult.SUCCESS; // prevents other actions from performing.
                }
            }

            return ActionResult.PASS;
        };
    }

    private static @NotNull ItemStack getItemStack(MongoNpc.Quest.Reward reward) {
        Identifier id = Identifier.of(reward.getItemName());
        Item item = Registries.ITEM.get(id);
        return new ItemStack(item, reward.getCount());
    }

    public static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        QuestManager questManager = QuestManager.getInstance();
        questManager.deactivatePlayer(serverPlayer.getUuidAsString());
    }
}
