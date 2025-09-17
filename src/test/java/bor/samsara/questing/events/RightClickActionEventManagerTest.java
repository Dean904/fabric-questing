package bor.samsara.questing.events;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RightClickActionEventManagerTest {

    @Test
    void evaporateBucketInNether_WhenUsingWaterInNetherBiome_ShouldEvaporateWater() {
        // Arrange
        PlayerEntity player = mock(PlayerEntity.class);
        World world = mock(World.class);
        BlockHitResult hitResult = mock(BlockHitResult.class);
        BlockPos blockPos = new BlockPos(0, 64, 0);
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);

        when(player.getStackInHand(Hand.MAIN_HAND)).thenReturn(waterBucket);
        when(hitResult.getBlockPos()).thenReturn(blockPos);
        when(world.getBiome(blockPos)).thenReturn(mockNetherBiome());

        // Act
        ActionResult result = RightClickActionEventManager.evaporateBucketInNether()
            .interact(player, world, Hand.MAIN_HAND, hitResult);

        // Assert
        assertEquals(ActionResult.CONSUME, result);
        verify(world).playSound(null, blockPos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
            SoundCategory.BLOCKS, 1.0f, 2.6f);
        verify(player).setStackInHand(Hand.MAIN_HAND, any(ItemStack.class));
    }

    @Test
    void evaporateBucketInNether_WhenUsingWaterInNormalBiome_ShouldAllowWaterPlacement() {
        // Arrange
        PlayerEntity player = mock(PlayerEntity.class);
        World world = mock(World.class);
        BlockHitResult hitResult = mock(BlockHitResult.class);
        BlockPos blockPos = new BlockPos(0, 64, 0);
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);

        when(player.getStackInHand(Hand.MAIN_HAND)).thenReturn(waterBucket);
        when(hitResult.getBlockPos()).thenReturn(blockPos);
        when(world.getBiome(blockPos)).thenReturn(mockNormalBiome());

        // Act
        ActionResult result = RightClickActionEventManager.evaporateBucketInNether()
            .interact(player, world, Hand.MAIN_HAND, hitResult);

        // Assert
        assertEquals(ActionResult.PASS, result);
        verify(world, never()).playSound(any(), any(), any(), any(), anyFloat(), anyFloat());
        verify(player, never()).setStackInHand(any(), any());
    }

    @Test
    void evaporateBucketInNether_WhenUsingNonWaterBucket_ShouldIgnore() {
        // Arrange
        PlayerEntity player = mock(PlayerEntity.class);
        World world = mock(World.class);
        BlockHitResult hitResult = mock(BlockHitResult.class);
        BlockPos blockPos = new BlockPos(0, 64, 0);
        ItemStack nonWaterItem = new ItemStack(Items.DIAMOND);

        when(player.getStackInHand(Hand.MAIN_HAND)).thenReturn(nonWaterItem);
        when(hitResult.getBlockPos()).thenReturn(blockPos);
        when(world.getBiome(blockPos)).thenReturn(mockNetherBiome());

        // Act
        ActionResult result = RightClickActionEventManager.evaporateBucketInNether()
            .interact(player, world, Hand.MAIN_HAND, hitResult);

        // Assert
        assertEquals(ActionResult.PASS, result);
        verify(world, never()).playSound(any(), any(), any(), any(), anyFloat(), anyFloat());
        verify(player, never()).setStackInHand(any(), any());
    }

    private RegistryEntry<Biome> mockNetherBiome() {
        @SuppressWarnings("unchecked")
        RegistryEntry<Biome> biome = mock(RegistryEntry.class);
        when(biome.isIn(BiomeTags.IS_NETHER)).thenReturn(true);
        return biome;
    }

    private RegistryEntry<Biome> mockNormalBiome() {
        @SuppressWarnings("unchecked")
        RegistryEntry<Biome> biome = mock(RegistryEntry.class);
        when(biome.isIn(BiomeTags.IS_NETHER)).thenReturn(false);
        return biome;
    }
}
