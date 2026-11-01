package com.example.donutqueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Plays the bundled lets_go.mp3 through plain javax.sound.sampled, decoded
 * via the mp3spi library bundled into the mod jar. Deliberately does NOT
 * go through Minecraft's own sound system - plain Java audio APIs are
 * stable across Minecraft versions, unlike everything else in this mod.
 *
 * Waits for the clip's actual STOP event (fired when playback finishes on
 * its own) instead of a fixed sleep duration, so the whole sound plays out
 * regardless of any quirks in how its length gets reported.
 */
public class SoundPlayer {

    private static final String RESOURCE_PATH = "/assets/donutqueue/sounds/lets_go.mp3";

    public static void playCelebrationSound() {
        Thread thread = new Thread(SoundPlayer::playBlocking, "DonutQueue-Sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playBlocking() {
        try (InputStream in = SoundPlayer.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                System.err.println("[DonutQueue] Couldn't find bundled celebration sound at " + RESOURCE_PATH);
                return;
            }
            try (AudioInputStream mp3Stream = AudioSystem.getAudioInputStream(new BufferedInputStream(in))) {
                AudioFormat baseFormat = mp3Stream.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false
                );
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(decodedFormat, mp3Stream)) {
                    Clip clip = AudioSystem.getClip();

                    CountDownLatch finished = new CountDownLatch(1);
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            finished.countDown();
                        }
                    });

                    clip.open(decoded);

                    // Turn the volume down a bit - full volume was too loud.
                    // -8 dB is roughly half as loud to the ear.
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        float quieterDb = Math.max(gain.getMinimum(), -8f);
                        gain.setValue(quieterDb);
                    }

                    clip.start();

                    // Wait for the clip to genuinely finish playing (STOP event),
                    // with a generous timeout as a safety net in case that event
                    // never fires for some reason.
                    finished.await(60, TimeUnit.SECONDS);
                    clip.close();
                }
            }
        } catch (Exception e) {
            System.err.println("[DonutQueue] Failed to play celebration sound: " + e);
        }
    }
}
