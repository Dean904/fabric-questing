package bor.samsara.questing;

import bor.samsara.questing.entity.BookStateUtil;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.entity.PlayerQuestState;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class EventRegisters {

    // == Config

    public static @NotNull CommandRegistrationCallback createNpc() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .then(literal("add")
                                .then(literal("npc")
                                        .then(argument("name", greedyString())
                                                .executes(context -> {
                                                            String villagerName = getString(context, "name");
                                                            return ModEntities.createQuestNPC(context.getSource(), villagerName);
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

    public static @NotNull CommandRegistrationCallback openCommandBookForNpc() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .then(literal("config")
                                .then(literal("npc")
                                        .then(argument("name", greedyString())
                                                .executes(context -> {
                                                            String villagerName = getString(context, "name");
                                                            return BookStateUtil.open(context.getSource(), villagerName);
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

    @Deprecated
    // TODO delete, no longer supported or needed now that sign and close book mixin implemented
    public static @NotNull CommandRegistrationCallback closeCommandBookForNpc() {
        return (dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("quest")
                        .then(literal("config")
                                .then(literal("close")
                                        .then(argument("name", greedyString())
                                                .executes(context -> {
                                                            String villagerName = getString(context, "name");
                                                            return BookStateUtil.close(context.getSource(), villagerName);
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

    // TODO quest delete, rename or disable = true?


    // == Progress

    public static @NotNull UseEntityCallback rightClickQuestNpc() {
        return (PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) -> {
            if (null != hitResult && entity.getCommandTags().contains("questNPC")) {
                String playerUuid = player.getUuid().toString();
                String questNpcUuid = entity.getUuid().toString();

                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(playerUuid);
                Integer activeQuestForNpc = playerState.getActiveQuestForNpc(questNpcUuid);

                MongoNpc npc = NpcMongoClient.getNpc(questNpcUuid);
                MongoNpc.Quest activeQuest = npc.getQuests().get(activeQuestForNpc);

                if (null != activeQuest) {
                    int dialogueOffset = PlayerQuestState.getDialogueOffset(playerUuid, activeQuestForNpc);
                    String dialogue = activeQuest.getDialogue().get(dialogueOffset % activeQuest.getDialogue().size());
                    player.sendMessage(Text.literal(dialogue), false);
                }

                player.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        };
    }

}
