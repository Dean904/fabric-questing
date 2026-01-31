package bor.samsara.questing.events;

import bor.samsara.questing.Sounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class SamsaraNoteBlockTunes {

    private static final ExecutorService executor = Executors.newThreadPerTaskExecutor(runnable -> new Thread(runnable, "SamsaraNoteBlockTunes-Thread"));

    public static void playZeldaTune(PlayerEntity player) {
        executor.submit(() -> {
            try {
                BiConsumer<SoundEvent, Float> play = (s, p) ->
                        Sounds.aroundPlayer(player, s, 1.0f, p);

                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.0f); // C5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.12f); // D5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.26f); // E5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.34f); // F5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.5f); // G5
                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playFinalFantasyVictoryFanfare(PlayerEntity player) {
        executor.submit(() -> {
            try {
                BiConsumer<SoundEvent, Float> play = (s, p) ->
                        Sounds.aroundPlayer(player, s, 1.0f, p);

                SoundEvent pling = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();

                // "C5 – C5 – C5 – G4"
                play.accept(pling, 1.0f);
                Thread.sleep(150);
                play.accept(pling, 1.0f);
                Thread.sleep(150);
                play.accept(pling, 1.0f);
                Thread.sleep(200);
                play.accept(pling, 0.75f); // G4
                Thread.sleep(300);

                // "A4 – F4 – G4 – C5"
                play.accept(pling, 0.84f); // A4
                Thread.sleep(150);
                play.accept(pling, 0.67f); // F4
                Thread.sleep(150);
                play.accept(pling, 0.75f); // G4
                Thread.sleep(200);
                play.accept(pling, 1.0f);  // C5
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playZeldaPuzzleSolved(PlayerEntity player) {
        executor.submit(() -> {
            try {
                float[] pitches = {1.00f, 1.12f, 1.34f, 1.50f, 2.00f};
                SoundEvent[] instruments = {
                        SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME
                };
                long[] durations = {180, 180, 160, 160, 400};

                for (int i = 0; i < pitches.length; i++) {
                    Sounds.aroundPlayer(player, instruments[i], 1.0f, pitches[i]);
                    Thread.sleep(durations[i]);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playChaosEmerald(PlayerEntity player) {
        executor.submit(() -> {
            try {
                float[] pitches = {1.26f, 1.50f, 2.00f, 1.34f, 1.68f, 1.26f}; // E5, G5, C6, F5, A5, E5
                SoundEvent[] instruments = {
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                };
                long[] durations = {120, 120, 120, 120, 200, 275};

                for (int i = 0; i < pitches.length; i++) {
                    Sounds.aroundPlayer(player, instruments[i], 1.0f, pitches[i]);
                    Thread.sleep(durations[i]);
                }

                // Final reward sound
                Sounds.aroundPlayer(player, SoundEvents.UI_HUD_BUBBLE_POP);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playOrchestra(ServerPlayerEntity player) {
        executor.submit(() -> {
            try {
                SoundEvent flute = SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value();
                SoundEvent pling = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                SoundEvent harp = SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
                SoundEvent bass = SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
                SoundEvent chime = SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();

                float[] melody = {1.00f, 1.26f, 1.50f, 1.68f, 1.50f, 1.26f, 1.12f, 1.00f};
                float[] harmony = {0.75f, 0.95f, 0.67f, 0.84f, 0.67f, 0.75f, 0.63f, 0.67f};
                float[][] chords = {
                        {1.00f, 1.26f, 1.50f},
                        {0.84f, 1.00f, 1.26f},
                        {0.67f, 0.84f, 1.00f},
                        {0.75f, 0.95f, 1.12f}
                };
                float[] bassline = {0.50f, 0.42f, 0.34f, 0.38f};

                int melodyLen = melody.length;
                int harmonyLen = harmony.length;
                int chordsLen = chords.length;
                int bassLen = bassline.length;
                int maxLen = Math.max(Math.max(melodyLen, harmonyLen), Math.max(chordsLen, bassLen));

                for (int i = 0; i < maxLen; i++) {
                    // Melody
                    if (i < melodyLen) {
                        Sounds.toOnlyPlayer(player, flute, 0.5f, melody[i]);
                    }
                    // Harmony (offset by 137ms)
                    if (i < harmonyLen) {
                        if (i == 0) Thread.sleep(137);
                        Sounds.toOnlyPlayer(player, pling, 0.3f, harmony[i]);
                    }
                    // Chords
                    if (i < chordsLen) {
                        for (float pitch : chords[i]) {
                            Sounds.toOnlyPlayer(player, harp, 0.3f, pitch);
                        }
                    }
                    // Bassline
                    if (i < bassLen) {
                        Sounds.toOnlyPlayer(player, bass, 0.4f, bassline[i]);
                    }
                    Thread.sleep(275);
                }

                // Wait for last chord/bass to finish
                Thread.sleep(205);

                // Finishing chime
                Sounds.toOnlyPlayer(player, chime, 0.7f, 1.3f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
