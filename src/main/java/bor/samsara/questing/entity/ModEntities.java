package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class ModEntities {


    public static int createQuestNPC(ServerCommandSource source, String name) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getWorld();
        Vec3d pos = player.getPos();

        try {
            VillagerEntity villager = makeVillagerEntity(world, pos, player, name);
            String uuid = villager.getUuid().toString();
            MongoNpc mongoNpc = new MongoNpc(uuid, name);
            NpcMongoClient.createNpc(mongoNpc);
            world.spawnEntity(villager);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned a Quest NPC!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull VillagerEntity makeVillagerEntity(World world, Vec3d pos, ServerPlayerEntity player, String name) {
        VillagerEntity villager = EntityType.VILLAGER.create(world);
        villager.refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
        villager.setCustomName(Text.literal(name));
        villager.addCommandTag("questNPC");
        villager.setAiDisabled(true);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setNoGravity(true);
        return villager;
    }
    public static void spawnTravelingWelcomer(ServerCommandSource source) {
        // TODO spawn a traveling villager to follow player until welcome convo complete
    }

    public static void despawnTravelingWelcomer(ServerCommandSource source) {
    }

}

