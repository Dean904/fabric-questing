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
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
                    log.debug("Registering {} to first quest for {}", playerState.getName(), entity.getStringifiedName());
                    SamsaraNoteBlockTunes.playChaosEmerald(player); //playZeldaPuzzleSolved(player);//playOrchestra(player);
                    progressPlayerToNextIncompleteQuest(player, playerState, NpcMongoClient.getNpc(npcUuid));
                }

                String currentTargetQuestId = playerState.getCurrentQuestForNpc(npcUuid);
                if (currentTargetQuestId != null) {  // If the NPC is not a start node and have not been 'introduced' by TALK objective then no dialogue.
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(currentTargetQuestId);
                    String playerNpcKey = playerState.getUuid() + npcUuid;
                    playerDialogueOffsetMap.putIfAbsent(playerNpcKey, new TemporalDialogueOffset(0, Instant.now()));

                    if (playerState.getActiveQuestProgressionMap().containsKey(currentTargetQuestId)) { // END quests will not have progress
                        MongoPlayer.ActiveQuestState activeQuestState = playerState.getProgressForQuest(currentTargetQuestId);
                        handleCollectionSubmissionForCompletion(player, hand, quest, activeQuestState, playerState, entity.getStringifiedName());

                        if (activeQuestState.areAllObjectivesComplete() && isQuestCompletable(activeQuestState, quest, entity.getStringifiedName())) {
                            rewardPlayer(player, quest);
                            playerState.markQuestComplete(quest.getUuid());
                            PlayerMongoClient.updatePlayer(playerState);
                            SamsaraFabricQuesting.doQuestSubject.processQuestCompletion(player, playerState, quest);
                            playOrchestra((ServerPlayerEntity) player); //playChaosEmerald(player);
                            executeTriggerCommands(player, entity, quest.getTriggers(MongoQuest.EventTrigger.ON_COMPLETE));
                        }

                        if (!activeQuestState.hasReceivedQuestBook() && quest.doesProvideQuestBook() && playerDialogueOffsetMap.get(playerNpcKey).dialogueOffset + 1 == quest.getDialogue().size()) {
                            executeTriggerCommands(player, entity, quest.getTriggers(MongoQuest.EventTrigger.ON_START));
                            QuestProgressBook.open(player, quest, playerState);
                            activeQuestState.setReceivedQuestBook(true);
                            PlayerMongoClient.updatePlayer(playerState);
                            playZeldaPuzzleSolved(player);
                        }
                    }

                    if (playerState.isQuestComplete(currentTargetQuestId)) { // END quests cannot be completed
                        log.debug("Progressing {} to next quest sequence of {}, '{}'", playerState.getName(), quest.getCategory(), quest.getTitle());
                        playerDialogueOffsetMap.put(playerNpcKey, new TemporalDialogueOffset(0, Instant.now()));
                        quest = progressPlayerToNextIncompleteQuest(player, playerState, NpcMongoClient.getNpc(npcUuid));
                    }

                    int dialogueOffset = playerDialogueOffsetMap.get(playerNpcKey).dialogueOffset;
                    String dialogue = quest.getDialogue().get(dialogueOffset);
                    if (StringUtils.isNotBlank(dialogue)) {
                        int nextDialogueOffset = (dialogueOffset + 1) % quest.getDialogue().size();
                        playerDialogueOffsetMap.put(playerNpcKey, new TemporalDialogueOffset(nextDialogueOffset, Instant.now()));
                        player.sendMessage(Text.literal("[" + entity.getName().getString() + "] ").styled(style -> style.withColor(Formatting.YELLOW))
                                .append(Text.literal(dialogue).styled(style -> style.withColor(Formatting.WHITE))), false);
                        Sounds.toOnlyPlayer((ServerPlayerEntity) player, SoundEvents.ITEM_BOOK_PAGE_TURN);
                        if (nextDialogueOffset == 0) {
                            player.removeStatusEffect(StatusEffects.SLOWNESS);
                            executeTriggerCommands(player, entity, quest.getTriggers(MongoQuest.EventTrigger.ON_DIALOGUE_DONE));
                        }
                    }

                    return ActionResult.CONSUME;
                }
            }

            return ActionResult.PASS;
        };
    }

    private static boolean isQuestCompletable(MongoPlayer.ActiveQuestState activeQuestState, MongoQuest quest, String npcName) {
        return (activeQuestState.hasReceivedQuestBook() || !quest.doesProvideQuestBook())
                && (StringUtils.isEmpty(quest.getSubmissionTarget()) || Strings.CI.equals(quest.getSubmissionTarget(), npcName));
    }

    public static MongoQuest progressPlayerToNextIncompleteQuest(PlayerEntity player, MongoPlayer playerState, MongoNpc npc) {
        String nextQuestId = npc.getQuestIds().stream().filter(q -> !playerState.isQuestComplete(q)).findFirst()
                .orElseThrow(() -> new IllegalStateException("NPC '%s' is missing END quest.".formatted(npc.getName())));
        MongoQuest quest = QuestMongoClient.getQuestByUuid(nextQuestId);
        playerState.setCurrentQuestForNpc(npc.getUuid(), quest.getUuid());
        if (quest.getCategory() != MongoQuest.CategoryEnum.END) {
            if (StringUtils.isNotBlank(quest.getSubmissionTarget()) && !Strings.CI.equals(npc.getName(), quest.getSubmissionTarget())) {
                // This will overwrite the current active quest for the submission target if it exists, natural quest progress resumes after completion 
                MongoNpc targetNpc = NpcMongoClient.getNpcByName(quest.getSubmissionTarget());
                playerState.setCurrentQuestForNpc(targetNpc.getUuid(), quest.getUuid());
            }
            if (playerState.getProgressForQuest(quest.getUuid()) == null)
                playerState.attachActiveQuestState(new MongoPlayer.ActiveQuestState(quest));
            SamsaraFabricQuesting.attachQuestListenerToPertinentSubject(playerState, quest);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 24000, 5, false, false));
        }
        PlayerMongoClient.updatePlayer(playerState);
        return quest;
    }

    private static void executeTriggerCommands(PlayerEntity player, Entity npc, List<String> commands) {
        if (CollectionUtils.isNotEmpty(commands)) {
            log.debug("Executing command for {} triggering quest: {}", player.getStringifiedName(), commands);
            CommandManager commandManager = Objects.requireNonNull(player.getEntityWorld().getServer()).getCommandManager();
            ServerCommandSource commandSource = player.getEntityWorld().getServer().getCommandSource();
            for (String rawCommand : commands) {
                if (rawCommand.startsWith("/reward")) {
                    String[] rewardArgs = rawCommand.split(" ");
                    ItemStack reward = ItemStackFactory.getRewardItemStack(rewardArgs[1], Integer.parseInt(rewardArgs[2]), player.getEntityWorld());
                    player.giveOrDropStack(reward);
                } else {
                    String command = getScriptedString(player, npc, rawCommand);
                    commandManager.parseAndExecute(commandSource, command);
                }
            }
        }
    }

    private static @NotNull String getScriptedString(PlayerEntity player, Entity npc, String rawCommand) {
        Vec3d npcPos = npc.getEntityPos();
        BlockPos playerPos = player.getBlockPos();
        String command = rawCommand.replaceAll("@npcLoc", npcPos.x + " " + npcPos.y + 1 + " " + npcPos.z);
        command = command.replaceAll("@pLoc", playerPos.getX() + " " + playerPos.getY() + 1 + " " + playerPos.getZ());
        command = command.replaceAll("@npc", npc.getUuidAsString());
        command = command.replaceAll("@p", player.getStringifiedName());
        return command;
    }

    private static void handleCollectionSubmissionForCompletion(PlayerEntity player, Hand hand, MongoQuest quest, MongoPlayer.ActiveQuestState activeQuestState, MongoPlayer playerState, String npcName) {
        AtomicBoolean wasUpdated = new AtomicBoolean(false);
        quest.getObjectives().forEach(objective -> {
            // TODO as isNotComplete check
            if (MongoQuest.Objective.Type.COLLECT == objective.getType() && isQuestCompletable(activeQuestState, quest, npcName)) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isEmpty() || !Strings.CI.equals(stack.getItem().toString(), objective.getTarget()) || stack.getCount() < objective.getRequiredCount()) {
                    player.sendMessage(Text.literal("Right click with " + objective.getRequiredCount() + " [" + objective.getTarget() + "]!"), true);
                } else {
                    stack.decrement(objective.getRequiredCount());
                    player.setStackInHand(hand, stack);
                    activeQuestState.getObjectiveProgressions().stream().filter(op -> Strings.CI.equalsAny(op.getObjective().getTarget(), objective.getTarget())).findFirst().ifPresent(op -> {
                        op.setComplete(true);
                        wasUpdated.set(true);
                        boolean isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                    });
                }
            }
        });
        if (wasUpdated.get()) {
            PlayerMongoClient.updatePlayer(playerState); // TODO playerState param is needed JUST for the .update ... can we refactor to reduce params?
        }
    }

    private static void rewardPlayer(PlayerEntity player, MongoQuest quest) {
        MongoQuest.Reward reward = quest.getReward();
        if (reward != null) {
            player.addExperience(reward.getXpValue());
            if (!Strings.CI.equalsAny(reward.getItemName(), "none", "na")) {
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
