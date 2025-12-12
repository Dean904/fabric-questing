package bor.samsara.questing.events;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;


public class ModEntities {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String QUEST_NPC = "questNPC";
    public static final String QUEST_START_NODE = "questStartNode";

    private ModEntities() {}

    public static int createQuestNPC(ServerCommandSource source, String name, String mobType, boolean isStartNode) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getEntityWorld();

        try {
            UUID villagerUuid = UUID.randomUUID();
            MobEntity villager = makeNpcEntity(world, villagerUuid, player, name, mobType);
            villager.addCommandTag(QUEST_NPC);
            if (isStartNode)
                villager.addCommandTag(QUEST_START_NODE);
            world.spawnEntity(villager);
            MongoNpc mongoNpc = new MongoNpc(villagerUuid.toString(), name);
            NpcMongoClient.createNpc(mongoNpc);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned a Quest NPC!"), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int spawnEntityFromUUID(ServerCommandSource source, String uuid) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        World world = player.getEntityWorld();

        try {
            MongoNpc mongoNpc = NpcMongoClient.getNpc(uuid);
            MobEntity npcEntity = makeNpcEntity(world, UUID.fromString(mongoNpc.getUuid()), player, mongoNpc.getName(), mongoNpc.getMobType());
            npcEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 5, false, false));
            npcEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, Integer.MAX_VALUE, 99, false, false));
            npcEntity.addCommandTag(QUEST_NPC);
            if (mongoNpc.isStartNode()) {
                npcEntity.addCommandTag(QUEST_START_NODE);
            }
            world.spawnEntity(npcEntity);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }

        source.sendFeedback(() -> Text.literal("Spawned NPC with UUID: " + uuid), true);
        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull MobEntity makeNpcEntity(World world, UUID uuid, ServerPlayerEntity player, String name, String mobType) {
        EntityType<? extends MobEntity> npcEntity = (EntityType<? extends MobEntity>) Registries.ENTITY_TYPE.get(Identifier.of(mobType));
        MobEntity npc = npcEntity.create(world, SpawnReason.TRIGGERED);
        npc.setUuid(uuid);
        npc.refreshPositionAndAngles(player.getEntityPos().x, player.getEntityPos().y, player.getEntityPos().z, player.getYaw(), player.getPitch());
        npc.setCustomName(Text.literal(name));
        npc.setCustomNameVisible(true);
        npc.setAiDisabled(true);
        npc.setSilent(true);
        npc.setInvulnerable(true);
        npc.setNoGravity(true);
        npc.setPersistent();
        npc.setCanPickUpLoot(false);
        return npc;
    }


}

