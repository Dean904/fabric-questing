package bor.samsara.questing;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public class Sounds {

    private Sounds() {}

    public static void toOnlyPlayer(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(RegistryEntry.of(sound), SoundCategory.PLAYERS,
                player.getX(), player.getY(), player.getZ(), volume, pitch, 1L));
    }

    public static void toOnlyPlayer(ServerPlayerEntity player, SoundEvent sound) {
        toOnlyPlayer(player, sound, 1.0f, 1.0f);
    }

    public static void aroundPlayer(PlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.getEntityWorld().playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void aroundPlayer(PlayerEntity player, SoundEvent sound) {
        aroundPlayer(player, sound, 1.0f, 1.0f);
    }
}
