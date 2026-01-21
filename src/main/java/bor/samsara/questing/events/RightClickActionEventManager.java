package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.Sounds;
import bor.samsara.questing.book.QuestProgressBook;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static bor.samsara.questing.events.SamsaraNoteBlockTunes.playOrchestra;
import static bor.samsara.questing.events.SamsaraNoteBlockTunes.playZeldaPuzzleSolved;

public class RightClickActionEventManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, TemporalDialogueOffset> playerDialogueOffsetMap = new HashMap<>();

    private RightClickActionEventManager() {}

    static {
        SamsaraFabricQuesting.scheduler.scheduleAtFixedRate(() -> {
            synchronized (playerDialogueOffsetMap) {
                playerDialogueOffsetMap.entrySet().removeIf(entry -> entry.getValue().isExpired());
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private record TemporalDialogueOffset(int dialogueOffset, Instant timestamp) {
        boolean isExpired() {
            return Instant.now().isAfter(timestamp.plusSeconds(33));
        }
    }

    public static @NotNull UseEntityCallback rightClickQuestNpc() {
        return (PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {

            if (null != hitResult && hand == Hand.MAIN_HAND && entity.getCommandTags().contains(ModEntities.QUEST_NPC)) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(player.getUuid().toString());
                String npcUuid = entity.getUuid().toString();
                entity.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, player.getEntityPos());
                SamsaraFabricQuesting.talkToNpcSubject.talkedToQuestNpc(player, world, hand, hitResult, playerState, npcUuid);

                if (!playerState.hasPlayerProgressedNpc(npcUuid) && entity.getCommandTags().contains(ModEntities.QUEST_START_NODE)) {
                    MongoNpc npc = NpcMongoClient.getNpc(npcUuid);
                    initializeFirstNpcQuestForPlayer(player, npc, playerState);
                }

                String currentTargetQuestId = playerState.getCurrentQuestForNpc(npcUuid);
                if (currentTargetQuestId != null) {  // If the NPC is not a start node and have not been 'introduced' by TALK objective then no dialogue.
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(currentTargetQuestId);
                    String playerNpcKey = playerState.getUuid() + npcUuid;
                    playerDialogueOffsetMap.putIfAbsent(playerNpcKey, new TemporalDialogueOffset(0, Instant.now()));

                    if (playerState.getActiveQuestProgressionMap().containsKey(currentTargetQuestId)) { // END quests will not have progress
                        MongoPlayer.ActiveQuestState activeQuestState = playerState.getProgressForQuest(currentTargetQuestId);
                        handleCollectionSubmissionForCompletion(player, hand, quest, activeQuestState, playerState);

                        if (isQuestReadyToComplete(activeQuestState, quest)) {
                            rewardPlayer(player, world, quest);
                            playerState.markQuestComplete(quest.getUuid());
                            PlayerMongoClient.updatePlayer(playerState);
                            SamsaraFabricQuesting.doQuestSubject.processQuestCompletion(player, playerState, quest);
                            playOrchestra((ServerPlayerEntity) player); //playChaosEmerald(player);
                            if (null != quest.getTrigger() && MongoQuest.Trigger.Event.ON_COMPLETE == quest.getTrigger().getEvent()) {
                                executeTriggerCommand(player, playerState, quest, entity);
                            }
                            return ActionResult.CONSUME;
                        }

                        if (!activeQuestState.hasReceivedQuestBook() && quest.doesProvideQuestBook() && playerDialogueOffsetMap.get(playerNpcKey).dialogueOffset + 1 == quest.getDialogue().size()) {
                            if (null != quest.getTrigger() && MongoQuest.Trigger.Event.ON_START == quest.getTrigger().getEvent()) {
                                executeTriggerCommand(player, playerState, quest, entity);
                            }
                            QuestProgressBook.open(player, quest, playerState);
                            activeQuestState.setReceivedQuestBook(true);
                            PlayerMongoClient.updatePlayer(playerState);
                            playZeldaPuzzleSolved(player);
                        }
                    }

                    if (playerState.isQuestComplete(currentTargetQuestId)) { // END quests cannot be completed
                        log.debug("Progressing {} to next quest sequence of {}, '{}'", playerState.getName(), quest.getCategory(), quest.getTitle());
                        playerDialogueOffsetMap.put(playerNpcKey, new TemporalDialogueOffset(0, Instant.now()));
                        quest = progressPlayerToNextIncompleteQuest(playerState, npcUuid);
                    }

                    int dialogueOffset = playerDialogueOffsetMap.get(playerNpcKey).dialogueOffset;
                    playerDialogueOffsetMap.put(playerNpcKey, new TemporalDialogueOffset((dialogueOffset + 1) % quest.getDialogue().size(), Instant.now()));

                    String dialogue = quest.getDialogue().get(dialogueOffset);
                    if (StringUtils.isNotBlank(dialogue)) {
                        player.sendMessage(Text.literal("[" + entity.getName().getString() + "] ").styled(style -> style.withColor(Formatting.YELLOW))
                                .append(Text.literal(dialogue).styled(style -> style.withColor(Formatting.WHITE))), false);
                        Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.ITEM_BOOK_PAGE_TURN);
                    }

                }
                return ActionResult.CONSUME;
            }

            return ActionResult.PASS;
        };
    }

    private static boolean isQuestReadyToComplete(MongoPlayer.ActiveQuestState activeQuestState, MongoQuest quest) {
        return activeQuestState.areAllObjectivesComplete() && (activeQuestState.hasReceivedQuestBook() || !quest.doesProvideQuestBook());
    }

    private static MongoQuest progressPlayerToNextIncompleteQuest(MongoPlayer playerState, String npcUuid) {
        MongoNpc npc = NpcMongoClient.getNpc(npcUuid);
        String nextQuestId = npc.getQuestIds().stream().filter(q -> !playerState.isQuestComplete(q)).findFirst().orElse(null);
        MongoQuest quest = QuestMongoClient.getQuestByUuid(nextQuestId);
        playerState.setCurrentQuestForNpc(npc.getUuid(), quest.getUuid());
        for (MongoQuest.Objective talkObjective : quest.getObjectives().stream().filter(o -> o.getType() == MongoQuest.Objective.Type.TALK).toList()) {
            MongoNpc targetNpc = NpcMongoClient.getNpcByName(talkObjective.getTarget());
            playerState.setCurrentQuestForNpc(targetNpc.getUuid(), quest.getUuid());
        }
        if (quest.getCategory() != MongoQuest.CategoryEnum.END) {
            playerState.attachActiveQuestState(new MongoPlayer.ActiveQuestState(quest));
            SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, quest);
        }
        PlayerMongoClient.updatePlayer(playerState);
        return quest;
    }

    private static MongoQuest initializeFirstNpcQuestForPlayer(PlayerEntity player, MongoNpc npc, MongoPlayer playerState) {
        String firstQuestId = npc.getQuestIds().getFirst();
        MongoQuest firstQuest = QuestMongoClient.getQuestByUuid(firstQuestId);
        playerState.setCurrentQuestForNpc(npc.getUuid(), firstQuestId);
        if (firstQuest.getCategory() != MongoQuest.CategoryEnum.END) {
            playerState.attachActiveQuestState(new MongoPlayer.ActiveQuestState(firstQuest));
            SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, firstQuest);
        }
        PlayerMongoClient.updatePlayer(playerState);

        log.debug("Registering {} to first quest for {}", playerState.getName(), npc.getName());
        SamsaraNoteBlockTunes.playChaosEmerald(player); //playZeldaPuzzleSolved(player);//playOrchestra(player);
        return firstQuest;
    }

    private static void executeTriggerCommand(PlayerEntity player, MongoPlayer playerState, MongoQuest quest, Entity npc) {
        log.debug("Executing command for {} triggering quest {} completion: {}", playerState.getName(), quest.getTitle(), quest.getTrigger().getCommands());
        CommandManager commandManager = Objects.requireNonNull(player.getEntityWorld().getServer()).getCommandManager();
        ServerCommandSource commandSource = player.getEntityWorld().getServer().getCommandSource();
        for (String rawCommand : quest.getTrigger().getCommands()) {
            Vec3d npcPos = npc.getEntityPos();
            String command = rawCommand.replaceAll(" @npcLoc ", npcPos.x + " " + npcPos.y + 1 + " " + npcPos.z);
            BlockPos playerPos = player.getBlockPos();
            command = command.replaceAll(" @pLoc ", playerPos.getX() + " " + playerPos.getY() + 1 + " " + playerPos.getZ());
            command = command.replaceAll(" @npc ", npc.getUuidAsString());
            command = command.replaceAll(" @p ", player.getStringifiedName());
            commandManager.parseAndExecute(commandSource, command);
        }
    }

    private static void handleCollectionSubmissionForCompletion(PlayerEntity player, Hand hand, MongoQuest quest, MongoPlayer.ActiveQuestState activeQuestState, MongoPlayer playerState) {
        AtomicBoolean wasUpdated = new AtomicBoolean(false);
        quest.getObjectives().forEach(objective -> {
            if (MongoQuest.Objective.Type.COLLECT == objective.getType() && activeQuestState.hasReceivedQuestBook()) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isEmpty() || !StringUtils.equalsIgnoreCase(stack.getItem().toString(), objective.getTarget()) || stack.getCount() < objective.getRequiredCount()) {
                    player.sendMessage(Text.literal("Right click with " + objective.getRequiredCount() + " [" + objective.getTarget() + "]!"), true);
                } else {
                    stack.decrement(objective.getRequiredCount());
                    player.setStackInHand(hand, stack);
                    activeQuestState.getObjectiveProgressions().stream().filter(op -> StringUtils.equalsAnyIgnoreCase(op.getObjective().getTarget(), objective.getTarget())).findFirst().ifPresent(op -> {
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
                ItemStack stack = ItemStackFactory.getRewardItemStack(reward, player.getEntityWorld());
                boolean added = player.giveItemStack(stack);
                if (!added) {
                    player.dropItem(stack, false);
                }
            }
        }
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
