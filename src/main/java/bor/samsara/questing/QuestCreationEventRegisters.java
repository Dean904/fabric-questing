package bor.samsara.questing;

import bor.samsara.questing.entity.BookStateUtil;
import bor.samsara.questing.entity.ModEntities;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.jetbrains.annotations.NotNull;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class QuestCreationEventRegisters {

    // TODO quest delete, rename or disable = true?

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
                                                            return BookStateUtil.open(context.getSource(), villagerName);
                                                        }
                                                )
                                        )
                                )
                        )
        );
    }

}
