package bor.samsara.questing.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

@Deprecated
public class QuestVillager extends VillagerEntity {

    public QuestVillager(EntityType<? extends VillagerEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void tick() {
        //super.tick();
        this.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 10, 0, false, false));
    }

    // This method can be overridden if you need specific spawn conditions
    public static boolean canSpawn(EntityType<QuestVillager> type, ServerWorldAccess world,
                                   SpawnReason spawnReason, net.minecraft.util.math.BlockPos pos,
                                   java.util.Random random) {
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
    }
}
