package bor.samsara.questing.events;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

public class SamsaraNoteBlockTunes {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void playZeldaGetQuestTune(PlayerEntity player) {
        executor.submit(() -> {
            try {
                BiConsumer<SoundEvent, Float> play = (s, p) ->
                        player.playSoundToPlayer(s, SoundCategory.PLAYERS, 1.0f, p);

                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.0f); // C5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 1.12f); // D5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.26f); // E5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.34f); // F5
                Thread.sleep(150);
                play.accept(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.5f); // G5
                Thread.sleep(200);

                // Optional sparkle
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playFinalFantasyVictoryFanfare(PlayerEntity player) {
        executor.submit(() -> {
            try {
                BiConsumer<SoundEvent, Float> play = (s, p) ->
                        player.playSoundToPlayer(s, SoundCategory.PLAYERS, 1.0f, p);

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

                // Optional: add celebration
                player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playEchoesOfElwynn(PlayerEntity player) {
        executor.submit(() -> {
            try {
                SoundEvent flute = SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value();
                BiConsumer<Float, Integer> play = (pitch, sleep) -> {
                    player.playSoundToPlayer(flute, SoundCategory.PLAYERS, 1.0f, pitch);
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                };

                play.accept(1.00f, 275); // C5
                play.accept(1.26f, 275); // E5
                play.accept(1.50f, 300); // G5
                play.accept(1.68f, 400); // A5
                play.accept(1.50f, 250); // G5
                play.accept(1.26f, 275); // E5
                play.accept(1.12f, 325); // D5
                play.accept(1.00f, 500); // C5

                // Optional: a gentle amethyst chime to let the moment hang
                player.playSoundToPlayer(SoundEvents.UI_HUD_BUBBLE_POP, SoundCategory.PLAYERS, 1.0f, 1.2f);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    public static void playUnderEchoesOfElwynn(PlayerEntity player) {
        executor.submit(() -> {
            try {
                // Notes for both voices
                float[] melody = {1.00f, 1.26f, 1.50f, 1.68f, 1.50f, 1.26f, 1.12f, 1.00f}; // C5 to C5
                float[] harmony = {0.75f, 0.95f, 0.67f, 0.84f}; // G4, B4, E4, F4 (repeat loop)
                long[] beatDurations = {275, 275, 300, 400, 250, 275, 325, 500};

                for (int i = 0; i < melody.length; i++) {
                    // Play melody note
                    player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0f, melody[i]);
                    Thread.sleep(beatDurations[i] / 2); // Half-beat

                    // Play harmony on off-beat (if available)
                    player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.6f, harmony[i % harmony.length]);
                    Thread.sleep(beatDurations[i] / 2); // Next half-beat
                }

                // Final flourish
                player.playSoundToPlayer(SoundEvents.UI_HUD_BUBBLE_POP, SoundCategory.PLAYERS, 1.0f, 1.2f);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playMarioOneUpArpeggio(PlayerEntity player) {
        executor.submit(() -> {
            try {
                // G5 – E5 – C5 – G5
                float[] pitches = {1.50f, 1.26f, 1.00f, 1.50f};
                SoundEvent[] instruments = {
                        SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(),
                        SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()
                };

                long[] beatDurations = {150, 150, 150, 250};

                for (int i = 0; i < pitches.length; i++) {
                    player.playSoundToPlayer(instruments[i], SoundCategory.PLAYERS, 1.0f, pitches[i]);
                    Thread.sleep(beatDurations[i]);
                }

                // Optional sparkle finale
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.4f, 1.5f);

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
                    player.playSoundToPlayer(instruments[i], SoundCategory.PLAYERS, 1.0f, pitches[i]);
                    Thread.sleep(durations[i]);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playSonicChaosEmerald(PlayerEntity player) {
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
                long[] durations = {120, 120, 120, 120, 200, 250};

                for (int i = 0; i < pitches.length; i++) {
                    player.playSoundToPlayer(instruments[i], SoundCategory.PLAYERS, 1.0f, pitches[i]);
                    Thread.sleep(durations[i]);
                }

                // Final reward sound
                player.playSoundToPlayer(SoundEvents.UI_HUD_BUBBLE_POP, SoundCategory.PLAYERS, 1.0f, 1.0f);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void playOrchestra(PlayerEntity player) {
        executor.submit(() -> {
            try {
                // Sounds
                SoundEvent flute = SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value();
                SoundEvent pling = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                SoundEvent harp = SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
                SoundEvent bass = SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
                SoundEvent chime = SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();

                // Melody (FLUTE)
                new Thread(() -> {
                    float[] melody = {1.00f, 1.26f, 1.50f, 1.68f, 1.50f, 1.26f, 1.12f, 1.00f};
                    try {
                        for (float pitch : melody) {
                            player.playSoundToPlayer(flute, SoundCategory.PLAYERS, 1.0f, pitch);
                            Thread.sleep(275);
                        }
                    } catch (InterruptedException e) {}
                }).start();

                // Counterpoint (PLING)
                new Thread(() -> {
                    float[] harmony = {0.75f, 0.95f, 0.67f, 0.84f, 0.67f, 0.75f, 0.63f, 0.67f};
                    try {
                        Thread.sleep(137); // offset
                        for (float pitch : harmony) {
                            player.playSoundToPlayer(pling, SoundCategory.PLAYERS, 0.7f, pitch);
                            Thread.sleep(275);
                        }
                    } catch (InterruptedException e) {}
                }).start();

                // Harmony chords (HARP)
                new Thread(() -> {
                    float[][] chords = {
                            {1.00f, 1.26f, 1.50f},  // Cmaj
                            {0.84f, 1.00f, 1.26f},  // Am
                            {0.67f, 0.84f, 1.00f},  // Fmaj
                            {0.75f, 0.95f, 1.12f}   // Gmaj
                    };
                    try {
                        for (float[] chord : chords) {
                            for (float pitch : chord) {
                                player.playSoundToPlayer(harp, SoundCategory.PLAYERS, 0.6f, pitch);
                            }
                            Thread.sleep(550); // hold 2 beats
                        }
                    } catch (InterruptedException e) {}
                }).start();

                // Bassline
                new Thread(() -> {
                    float[] bassline = {0.50f, 0.42f, 0.34f, 0.38f}; // C2, A1, F1, G1 approx
                    try {
                        for (float pitch : bassline) {
                            player.playSoundToPlayer(bass, SoundCategory.PLAYERS, 0.8f, pitch);
                            Thread.sleep(550);
                        }
                    } catch (InterruptedException e) {}
                }).start();

                // Finishing chime
                new Thread(() -> {
                    try {
                        Thread.sleep(2300);
                        player.playSoundToPlayer(chime, SoundCategory.PLAYERS, 1.0f, 1.3f);
                    } catch (InterruptedException e) {}
                }).start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }


}
