package bor.samsara.questing.events;

import bor.samsara.questing.entity.QuestConfigBook;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.entity.QuestLogBook;
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
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.NotNull;

import static bor.samsara.questing.entity.QuestLogBook.getWrittenBookContentComponent;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuestCreationEventRegisters {

    // TODO quest delete, rename or disable = true?

    public static @NotNull UseItemCallback updateQuestLogWhenOpened() {
        return (player, world, hand) -> {
            ItemStack itemStack = player.getStackInHand(hand);
            if (itemStack.getItem() == Items.WRITTEN_BOOK && hasTrackingNbtTags(itemStack)) {
                NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
                String playerUuid = customData.getNbt().get(QuestLogBook.PLAYER_UUID).asString().orElseThrow();
                String questUuid = customData.getNbt().get(QuestLogBook.QUEST_UUID).asString().orElseThrow();
                int playerProgress = customData.getNbt().getInt(QuestLogBook.PLAYER_PROGRESS).orElseThrow();

                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(playerUuid);
                if (playerState.getQuestPlayerProgressMap().containsKey(questUuid) && playerState.getQuestPlayerProgressMap().get(questUuid).getObjectiveCount() != playerProgress) {
                    MongoQuest quest = QuestMongoClient.getQuestByUuid(questUuid);
                    WrittenBookContentComponent t = getWrittenBookContentComponent(quest, playerState, itemStack);
                    itemStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, t);
                    itemStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(updateQuestTags(questUuid, playerUuid, playerState)));
                }
                return ActionResult.PASS;
            }

            return ActionResult.PASS;
        };
    }

    private static NbtCompound updateQuestTags(String questUuid, String playerUuid, MongoPlayer playerState) {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(QuestLogBook.QUEST_UUID, questUuid);
        nbtCompound.putString(QuestLogBook.PLAYER_UUID, playerUuid);
        nbtCompound.putInt(QuestLogBook.PLAYER_PROGRESS, playerState.getQuestPlayerProgressMap().get(questUuid).getObjectiveCount());
        return nbtCompound;
    }

    private static boolean hasTrackingNbtTags(ItemStack itemStack) {
        NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            NbtCompound nbt = customData.getNbt();
            return nbt != null && nbt.contains(QuestLogBook.QUEST_UUID) && nbt.contains(QuestLogBook.PLAYER_UUID);
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

}
