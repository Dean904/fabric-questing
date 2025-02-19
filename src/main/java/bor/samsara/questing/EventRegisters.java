package bor.samsara.questing;

import bor.samsara.questing.entity.BookStateUtil;
import bor.samsara.questing.entity.ModEntities;
import bor.samsara.questing.mongo.NpcMongoClientSingleton;
import bor.samsara.questing.mongo.models.MongoNpc;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
            if (entity.getCommandTags().contains("questNPC")) { // this could be an int flag on .get(0) instead of a list traversal
                String uuid = entity.getUuid().toString();
                MongoNpc npc = NpcMongoClientSingleton.getInstance().getNpc(uuid);



                player.sendMessage(Text.literal(npc.getName()), false);
                // TODO play villager noise for player
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        };
    }

}
