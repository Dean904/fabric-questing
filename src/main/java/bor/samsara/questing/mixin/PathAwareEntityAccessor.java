package bor.samsara.questing.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Deprecated
@Mixin(MobEntity.class)
public class PathAwareEntityAccessor {

    @Final
    @Shadow
    protected GoalSelector goalSelector;

    @Unique
    public GoalSelector getGoalSelector() {
        return goalSelector;
    }


}
