package bor.samsara.questing.events;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.book.QuestConfigBook;
import bor.samsara.questing.book.QuestLogBook;
import bor.samsara.questing.book.QuestProgressBook;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuestCreationEventRegisters {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    // TODO quest delete, rename or disable = true?

    public static @NotNull UseItemCallback updateQuestLogWhenOpened() {
        return (player, world, hand) -> {
            ItemStack itemStack = player.getStackInHand(hand);
            NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
            if (itemStack.getItem() == Items.WRITTEN_BOOK && hasPlayerUuidTag(customData)) {
                if (hasSpecifcQuestUuidTag(customData)) {
                    String playerUuid = customData.getNbt().get(QuestProgressBook.PLAYER_UUID).asString().orElseThrow();
                    String questUuid = customData.getNbt().get(QuestProgressBook.QUEST_UUID).asString().orElseThrow();
                    int playerProgress = customData.getNbt().getInt(QuestProgressBook.PLAYER_PROGRESS).orElseThrow();

                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(playerUuid);
                    // TODO move this currentCount aggregation into a method in MongoPlayer.. also use it in the QuestProgressBook .createTrackingBook function
                    if (playerState.getQuestPlayerProgressMap().containsKey(questUuid) && playerState.getQuestPlayerProgressMap().get(questUuid)
                            .getObjectiveProgressions().stream().mapToInt(MongoPlayer.QuestProgress.ObjectiveProgress::getCurrentCount).sum() != playerProgress) {
                        MongoQuest quest = QuestMongoClient.getQuestByUuid(questUuid);
                        WrittenBookContentComponent t = QuestProgressBook.getWrittenBookContentComponent(quest, playerState, itemStack);
                        itemStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, t);
                        itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(updateQuestProgressTags(questUuid, playerUuid, playerState)));
                    }
                    return ActionResult.PASS;
                } else if (hasPlayerStateTag(customData)) {
                    String playerUuid = customData.getNbt().get(QuestLogBook.PLAYER_UUID).asString().orElseThrow();
                    Integer playerStateHash = customData.getNbt().get(QuestLogBook.PLAYER_STATE).asInt().orElseThrow();

                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(playerUuid);
                    if (playerState.getQuestPlayerProgressMap().hashCode() != playerStateHash) {
                        List<RawFilteredPair<Text>> content = QuestLogBook.getWrittenBookContentComponent(playerState);
                        itemStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT.withPages(content));
                        itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(updateQuestLogTags(playerUuid, playerState)));
                    }

                    return ActionResult.PASS;
                }
            }

            return ActionResult.PASS;
        };
    }

    private static NbtCompound updateQuestProgressTags(String questUuid, String playerUuid, MongoPlayer playerState) {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(QuestProgressBook.PLAYER_UUID, playerUuid);
        nbtCompound.putString(QuestProgressBook.QUEST_UUID, questUuid);
        nbtCompound.putInt(QuestProgressBook.PLAYER_PROGRESS, playerState.getQuestPlayerProgressMap().get(questUuid)
                .getObjectiveProgressions().stream().mapToInt(MongoPlayer.QuestProgress.ObjectiveProgress::getCurrentCount).sum());
        return nbtCompound;
    }

    private static NbtCompound updateQuestLogTags(String playerUuid, MongoPlayer playerState) {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(QuestLogBook.PLAYER_UUID, playerUuid);
        nbtCompound.putInt(QuestLogBook.PLAYER_STATE, playerState.getQuestPlayerProgressMap().hashCode());
        return nbtCompound;
    }

    private static boolean hasPlayerUuidTag(NbtComponent customData) {
        if (customData != null) {
            NbtCompound nbt = customData.getNbt();
            return nbt != null && (nbt.contains(QuestProgressBook.PLAYER_UUID) || nbt.contains(QuestLogBook.PLAYER_UUID)); // TODO should be standardized to a single tag
        }
        return false;
    }

    private static boolean hasSpecifcQuestUuidTag(NbtComponent customData) {
        if (customData != null) {
            NbtCompound nbt = customData.getNbt();
            return nbt != null && nbt.contains(QuestProgressBook.QUEST_UUID);
        }
        return false;
    }

    private static boolean hasPlayerStateTag(NbtComponent customData) {
        if (customData != null) {
            NbtCompound nbt = customData.getNbt();
            return nbt != null && nbt.contains(QuestLogBook.PLAYER_STATE);
        }
        return false;
    }

    public static @NotNull CommandRegistrationCallback createNpc() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(literal("add")
                                .then(literal("npc")
                                        // ────── Branch A: bool + name ──────
                                        .then(argument("isStartNode", BoolArgumentType.bool())
                                                .then(argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            boolean isStart = BoolArgumentType.getBool(ctx, "isStartNode");
                                                            String name = StringArgumentType.getString(ctx, "name");
                                                            return ModEntities.createQuestNPC(ctx.getSource(), name, isStart);
                                                        })
                                                )
                                        )
                                        // ────── Branch B: name only ──────
                                        .then(argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    // no bool provided → default false
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    return ModEntities.createQuestNPC(ctx.getSource(), name, /*isStart=*/false);
                                                })
                                        )
                                )
                        )
                        .then(literal("spawn")
                                .then(literal("npc")
                                        .then(argument("uuid", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String uuid = StringArgumentType.getString(ctx, "uuid");
                                                    return ModEntities.spawnEntityFromUUID(ctx.getSource(), uuid);
                                                })
                                        )
                                ))
        );
    }

    public static @NotNull CommandRegistrationCallback openCommandBookForNpc() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(literal("config")
                                .then(literal("npc")
                                        .then(argument("name", greedyString())
                                                .executes(context -> {
                                                            String villagerName = getString(context, "name");
                                                            return QuestConfigBook.open(context.getSource(), villagerName);
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

    public static @NotNull CommandRegistrationCallback setQuestTrigger() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .requires(Permissions.require("samsara.quest.admin", 2))
                        .then(literal("config")
                                .then(literal("trigger")
                                        .then(argument("questUuid", StringArgumentType.string())
                                                .suggests((context, builder) -> QuestMongoClient.getAllQuestUuid(builder))
                                                .then(argument("eventTrigger", StringArgumentType.string())
                                                        .suggests((context, builder) -> {
                                                            builder.suggest("on_complete");
                                                            builder.suggest("on_start");
                                                            return builder.buildFuture();
                                                        })
                                                        .then(argument("command", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                    MongoQuest.Trigger trigger = new MongoQuest.Trigger();
                                                                    trigger.setEvent(MongoQuest.Trigger.Event.valueOf(getString(context, "eventTrigger").toUpperCase()));
                                                                    trigger.setCommand(getString(context, "command"));
                                                                    MongoQuest quest = QuestMongoClient.getQuestByUuid(getString(context, "questUuid"));
                                                                    quest.setTrigger(trigger);
                                                                    QuestMongoClient.updateQuest(quest);
                                                                    return 0;
                                                                })
                                                        ))))));
    }

    public static @NotNull CommandRegistrationCallback openQuestLogForPlayer() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("questLog")
                        .executes(context -> {
                                    String targetPlayerName = context.getSource().getPlayerOrThrow().getName().getString();
                                    return QuestLogBook.open(context.getSource(), targetPlayerName);
                                }
                        )
                        .then(literal("click")
                                .then(argument("name", StringArgumentType.string())
                                        .then(argument("questUuid", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    try {
                                                        String targetPlayerName = getString(context, "name");
                                                        String questUuid = getString(context, "questUuid");
                                                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                        MongoQuest quest = QuestMongoClient.getQuestByUuid(questUuid);
                                                        MongoPlayer mongoPlayer = PlayerMongoClient.getPlayerByName(targetPlayerName);

                                                        ItemStack progressBook = QuestProgressBook.createTrackingBook(quest, mongoPlayer);
                                                        ItemStack questLog = player.getMainHandStack();
                                                        player.setStackInHand(player.getActiveHand(), progressBook);
                                                        SamsaraFabricQuesting.questRunnables.add(() -> {
                                                            player.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(player.getActiveHand()));
                                                            player.setStackInHand(player.getActiveHand(), questLog);
                                                        });
                                                    } catch (Exception e) {
                                                        log.warn("Failed to create quest book: {}", e.getMessage());
                                                    }
                                                    return 1;
                                                })
                                        )))
                        .then(literal("view")
                                .requires(Permissions.require("samsara.quest.admin", 2))
                                .then(argument("name", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            List.of(context.getSource().getServer().getPlayerNames()).forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                                    String targetPlayerName = getString(context, "name");
                                                    return QuestLogBook.open(context.getSource(), targetPlayerName);
                                                }
                                        )
                                )));
    }

}



