package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.events.concrete.QuestManager;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestActionEventManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    // TODO do these functions belong here? Or in QuestManager? bad abstraction
    public static MongoPlayer getOrMakePlayerOnJoin(ServerPlayerEntity serverPlayer) {
        try {
            QuestManager questManager = QuestManager.getInstance();
            MongoPlayer playerByUuid = PlayerMongoClient.getPlayerByUuid(serverPlayer.getUuidAsString());
            questManager.activatePlayer(serverPlayer.getUuidAsString(), playerByUuid); // TODO activatePlayer throwing exception and dupping players if NPC does not exist...
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
                SoundEvent rightClickSoundEffect = SoundEvents.ITEM_BOOK_PAGE_TURN; // default sound effect
                boolean playedSound = false;

                if (!questManager.isNpcActiveForPlayer(playerUuid, questNpcUuid) && entity.getCommandTags().contains(ModEntities.QUEST_START_NODE)) {
                    questManager.registerNpcForPlayer(playerUuid, questNpcUuid);
                    //playOrchestra(player);
                    //playZeldaPuzzleSolved(player);
                    SamsaraNoteBlockTunes.playSonicChaosEmerald(player);
                    playedSound = true;
                }

                if (questManager.isNpcActiveForPlayer(playerUuid, questNpcUuid)) {
                    if (questManager.isQuestCompleteForPlayer(playerUuid, questNpcUuid)) {
                        MongoQuest.Reward reward = questManager.getQuestReward(playerUuid, questNpcUuid);
                        if (!StringUtils.equals(reward.getItemName(), "none")) {
                            player.addExperience(reward.getXpValue());
                            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.5f);
                            ItemStack stack = getItemStack(reward, world);
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

                    if (!playedSound)
                        player.playSoundToPlayer(rightClickSoundEffect, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return ActionResult.SUCCESS; // prevents other actions from performing.
                }
            }

            return ActionResult.PASS;
        };
    }

    private static @NotNull ItemStack getItemStack(MongoQuest.Reward reward, World world) {
        String itemDefinition = reward.getItemName();
        Identifier id = Identifier.of(extractItemName(itemDefinition));
        Item item = Registries.ITEM.get(id);
        ItemStack itemStack = new ItemStack(item, reward.getCount());
        if (itemDefinition.contains("{")) {
            // TODO generic NBT handling
            String nbtCoords = itemDefinition.substring(itemDefinition.indexOf('{') + 1, itemDefinition.indexOf('}'));
            String[] pos = nbtCoords.split(",");
            GlobalPos globalPos = GlobalPos.create(world.getRegistryKey(), new BlockPos(Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2])));
            LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.of(globalPos), false);
            itemStack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        }

        return itemStack;
    }

    private static String extractItemName(String itemDefinition) {
        if (itemDefinition.contains("{")) {
            return itemDefinition.substring(0, itemDefinition.indexOf('{'));
        }
        return itemDefinition;
    }

    public static void savePlayerStatsOnExit(ServerPlayerEntity serverPlayer) {
        QuestManager questManager = QuestManager.getInstance();
        questManager.deactivatePlayer(serverPlayer.getUuidAsString());
    }


}
