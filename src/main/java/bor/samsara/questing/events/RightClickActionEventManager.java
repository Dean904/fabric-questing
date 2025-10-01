package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.book.QuestProgressBook;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static bor.samsara.questing.events.SamsaraNoteBlockTunes.*;

public class RightClickActionEventManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, Integer> playerDialogueOffsetMap = new WeakHashMap<>();

    private RightClickActionEventManager() {}

    public static @NotNull UseEntityCallback rightClickQuestNpc() {
        return (PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {
            if (null != hitResult && entity.getCommandTags().contains(ModEntities.QUEST_NPC)) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuid().toString());
                MongoNpc npc = NpcMongoClient.getNpc(entity.getUuid().toString());

                if (!playerState.hasPlayerProgressedNpc(npc.getUuid()) && entity.getCommandTags().contains(ModEntities.QUEST_START_NODE)) {
                    MongoQuest firstQuest = initializeFirstNpcQuestForPlayer(player, npc, playerState);
                    if (null != firstQuest.getTrigger() && MongoQuest.Trigger.Event.ON_START == firstQuest.getTrigger().getEvent()) {
                        executeTriggerCommand(player, playerState, firstQuest);
                    }
                }

                String currentActiveQuestId = playerState.getCurrentQuestForNpc(npc.getUuid());
                MongoQuest quest = QuestMongoClient.getQuestByUuid(currentActiveQuestId);

                String playerNpcKey = playerState.getUuid() + npc.getUuid();
                playerDialogueOffsetMap.putIfAbsent(playerNpcKey, 0);
                int dialogueOffset = playerDialogueOffsetMap.get(playerNpcKey);
                playerDialogueOffsetMap.put(playerNpcKey, (dialogueOffset + 1) % quest.getDialogue().size());

                String dialogue = quest.getDialogue().get(dialogueOffset);
                if (StringUtils.isNotBlank(dialogue)) {
                    player.sendMessage(Text.literal(dialogue), false);
                    player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }

                if (!playerState.isQuestComplete(currentActiveQuestId)) {
                    MongoPlayer.ActiveQuestState activeQuestState = playerState.getProgressForQuest(currentActiveQuestId);
                    handleCollectionSubmissionForCompletion(player, hand, quest, activeQuestState, npc, playerState);

                    if (activeQuestState.areAllObjectivesComplete()) {
                        rewardPlayer(player, world, quest);
                        playerState.markQuestComplete(quest.getUuid());
                        playerDialogueOffsetMap.put(playerNpcKey, 0);
                        playOrchestra(player); //playChaosEmerald(player);
                        if (null != quest.getTrigger() && MongoQuest.Trigger.Event.ON_COMPLETE == quest.getTrigger().getEvent()) {
                            executeTriggerCommand(player, playerState, quest);
                        }

                        int nextQuestSequence = quest.getSequence() + 1;
                        String nextQuestId = npc.getQuestIds().get(nextQuestSequence);
                        quest = QuestMongoClient.getQuestByUuid(nextQuestId);
                        playerState.setActiveQuestForNpc(npc.getUuid(), quest.getUuid());

                        if (quest.getCategory() != MongoQuest.Category.END) {
                            activeQuestState = new MongoPlayer.ActiveQuestState(quest);
                            playerState.attachActiveQuestState(activeQuestState);
                            SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, quest);
                        }
                        if (null != quest.getTrigger() && MongoQuest.Trigger.Event.ON_START == quest.getTrigger().getEvent()) {
                            executeTriggerCommand(player, playerState, quest);
                        }
                        PlayerMongoClient.updatePlayer(playerState);
                        log.debug("Progressing {} to next quest sequence of {}, {}, for {}", playerState.getName(), quest.getCategory(), nextQuestSequence, npc.getName());
                    }

                    if (!activeQuestState.hasReceivedQuestBook() && quest.doesProvideQuestBook() && dialogueOffset + 1 == quest.getDialogue().size()) {
                        QuestProgressBook.open(player, quest, playerState);
                        activeQuestState.setReceivedQuestBook(true);
                        playZeldaPuzzleSolved(player);
                        PlayerMongoClient.updatePlayer(playerState);
                    }
                }

                SamsaraFabricQuesting.talkToNpcSubject.talkedToQuestNpc(player, world, hand, hitResult, playerState, npc);
                return ActionResult.SUCCESS; // prevents other actions from performing.
            }

            return ActionResult.PASS;
        };
    }

    private static MongoQuest initializeFirstNpcQuestForPlayer(PlayerEntity player, MongoNpc npc, MongoPlayer playerState) {
        String firstQuestId = npc.getQuestIds().getFirst();
        MongoQuest firstQuest = QuestMongoClient.getQuestByUuid(firstQuestId);
        playerState.setActiveQuestForNpc(npc.getUuid(), firstQuestId);
        playerState.attachActiveQuestState(new MongoPlayer.ActiveQuestState(firstQuest));
        PlayerMongoClient.updatePlayer(playerState);

        log.debug("Registering {} to quest for {}", playerState.getName(), npc.getName());
        SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, firstQuest);
        SamsaraNoteBlockTunes.playChaosEmerald(player); //playZeldaPuzzleSolved(player);//playOrchestra(player);
        return firstQuest;
    }

    private static void executeTriggerCommand(PlayerEntity player, MongoPlayer playerState, MongoQuest quest) {
        log.debug("Executing command for {} triggering quest {} completion: {}", playerState.getName(), quest.getTitle(), quest.getTrigger().getCommand());
        CommandManager commandManager = Objects.requireNonNull(player.getServer()).getCommandManager();
        ServerCommandSource commandSource = player.getServer().getCommandSource();
        commandManager.executeWithPrefix(commandSource, quest.getTrigger().getCommand());
    }

    private static void handleCollectionSubmissionForCompletion(PlayerEntity player, Hand hand, MongoQuest quest, MongoPlayer.ActiveQuestState activeQuestState, MongoNpc npc, MongoPlayer playerState) {
        AtomicBoolean wasUpdated = new AtomicBoolean(false);
        quest.getObjectives().forEach(objective -> {
            if (MongoQuest.Objective.Type.COLLECT == objective.getType() && activeQuestState.hasReceivedQuestBook()) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isEmpty() || !StringUtils.equalsIgnoreCase(stack.getItem().toString(), objective.getTarget()) || stack.getCount() < objective.getRequiredCount()) {
                    player.sendMessage(Text.literal("You need to give " + npc.getName() + " " + objective.getRequiredCount() + " [" + objective.getTarget() + "]!"), true);
                } else {
                    stack.decrement(objective.getRequiredCount());
                    player.setStackInHand(hand, stack);
                    activeQuestState.getObjectiveProgressions().stream().filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), objective.getTarget())).findFirst().ifPresent(op -> {
                        op.setComplete(true);
                        wasUpdated.set(true);
                        boolean isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                    });
                }
            }
        });
        if (wasUpdated.get()) {
            PlayerMongoClient.updatePlayer(playerState);
        }
    }

    private static void rewardPlayer(PlayerEntity player, World world, MongoQuest quest) {
        MongoQuest.Reward reward = quest.getReward();
        if (reward != null) {
            player.addExperience(reward.getXpValue());
            if (!StringUtils.equalsAnyIgnoreCase(reward.getItemName(), "none", "na")) {
                ItemStack stack = getRewardItemStack(reward, world);
                boolean added = player.giveItemStack(stack);
                if (!added) {
                    player.dropItem(stack, false);
                }
            }
        }
    }

    private static @NotNull ItemStack getRewardItemStack(MongoQuest.Reward reward, World world) {
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

    public static UseBlockCallback evaporateBucketInNether() {
        return (player, world, hand, hitResult) -> {
            ItemStack itemStack = player.getStackInHand(hand);
            if (itemStack.getItem() == Items.WATER_BUCKET && world.getBiome(hitResult.getBlockPos()).isIn(net.minecraft.registry.tag.BiomeTags.IS_NETHER)) {
                world.playSound(null, hitResult.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 2.6f);
                player.setStackInHand(hand, new ItemStack(Items.BUCKET));
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        };
    }

}
