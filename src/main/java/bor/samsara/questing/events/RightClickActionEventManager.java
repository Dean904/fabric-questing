package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
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
import net.minecraft.sound.SoundCategory;
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

public class RightClickActionEventManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private RightClickActionEventManager() {}

    // TODO A Quest NPC needs 2 or 3 states per player ?
    //          , uninitiated giver, target npc (dependent quest), finished?

    public static @NotNull UseEntityCallback rightClickQuestNpc() {
        return (PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {
            if (null != hitResult && entity.getCommandTags().contains(ModEntities.QUEST_NPC)) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuid().toString());
                MongoNpc npc = NpcMongoClient.getNpc(entity.getUuid().toString());

                boolean hasPlayerProgressedNpc = playerState.getNpcQuestProgressMap().containsKey(npc.getUuid());
                if (!hasPlayerProgressedNpc && entity.getCommandTags().contains(ModEntities.QUEST_START_NODE)) {
                    initializeFirstNpcQuestForPlayer(player, npc, playerState);
                }

                MongoPlayer.QuestProgress questProgress = playerState.getNpcQuestProgressMap().get(npc.getUuid());
                MongoQuest quest = QuestMongoClient.getQuestByUuid(questProgress.getQuestUuid());

                if (questProgress.isComplete()) {
                    boolean completed = completesRequirements(player, hand, quest);
                    if (!completed) return ActionResult.SUCCESS; // if requirements not met, do not continue
                    rewardPlayer(player, world, quest);
                    player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.5f);
                    int nextQuestSequence = quest.getSequence() + 1;
                    if (nextQuestSequence < npc.getQuestIds().size()) {
                        String nextQuestId = npc.getQuestIds().get(nextQuestSequence);
                        quest = QuestMongoClient.getQuestByUuid(nextQuestId);
                        questProgress = new MongoPlayer.QuestProgress(nextQuestId, quest.getTitle(), nextQuestSequence);
                        playerState.getNpcQuestProgressMap().put(npc.getUuid(), questProgress);
                        PlayerMongoClient.updatePlayer(playerState);
                        SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, npc, quest.getObjective());
                        log.debug("Progressing {} to next quest sequence, {}, for {}", playerState.getName(), nextQuestSequence, npc.getName());
                    }
                }

                String dialogue = getNextDialogue(questProgress, quest);
                if (StringUtils.isNotBlank(dialogue)) {
                    player.sendMessage(Text.literal(dialogue), false);
                    player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }

                SamsaraFabricQuesting.talkToNpcSubject.talkedToQuestNpc(player, world, hand, hitResult, playerState, npc);
                return ActionResult.SUCCESS; // prevents other actions from performing.
            }

            return ActionResult.PASS;
        };
    }

    private static boolean completesRequirements(PlayerEntity player, Hand hand, MongoQuest quest) {
        if (MongoQuest.Objective.Type.COLLECT == quest.getObjective().getType()) {
            // consume from selected slot or send message to gimme
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || !StringUtils.equalsIgnoreCase(stack.getItem().toString(), quest.getObjective().getTarget()) || stack.getCount() < quest.getObjective().getRequiredCount()) {
                player.sendMessage(Text.literal("You need to give me " + quest.getObjective().getRequiredCount() + " [" + quest.getObjective().getTarget() + "]!"), false);
                return false;
            } else {
                stack.decrement(quest.getObjective().getRequiredCount());
                player.setStackInHand(hand, stack);
            }
        }
        return true;
    }

    private static void initializeFirstNpcQuestForPlayer(PlayerEntity player, MongoNpc npc, MongoPlayer playerState) {
        String firstQuestId = npc.getQuestIds().getFirst();
        MongoQuest firstQuest = QuestMongoClient.getQuestByUuid(firstQuestId);
        playerState.getNpcQuestProgressMap().put(npc.getUuid(), new MongoPlayer.QuestProgress(firstQuestId, firstQuest.getTitle(), 0));
        PlayerMongoClient.updatePlayer(playerState);

        log.debug("Registering {} to quest for {}", playerState.getName(), npc.getName());
        SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, npc, firstQuest.getObjective());
        SamsaraNoteBlockTunes.playChaosEmerald(player); //playZeldaPuzzleSolved(player);//playOrchestra(player);
    }

    private static void rewardPlayer(PlayerEntity player, World world, MongoQuest quest) {
        MongoQuest.Reward reward = quest.getReward();
        player.addExperience(reward.getXpValue());
        if (!StringUtils.equalsAnyIgnoreCase(reward.getItemName(), "none", "na")) {
            ItemStack stack = getItemStack(reward, world);
            boolean added = player.giveItemStack(stack);
            if (!added) {
                player.dropItem(stack, false);
            }
        }
    }

    private static String getNextDialogue(MongoPlayer.QuestProgress questProgressForNpc, MongoQuest staticQuest) {
        if (null != staticQuest) {
            long dialogueOffset = questProgressForNpc.getDialogueOffset();
            questProgressForNpc.setDialogueOffset((dialogueOffset + 1) % staticQuest.getDialogue().size());
            return staticQuest.getDialogue().get((int) dialogueOffset);
        }

        return "";
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


}
