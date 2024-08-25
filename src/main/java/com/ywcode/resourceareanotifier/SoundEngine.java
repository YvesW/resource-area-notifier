package com.ywcode.resourceareanotifier;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;

//Copyright (c) 2021, m0bilebtw
//Opted to largely use m0bilebtw's SoundEngine/SoundFileManager because sounds so often cause client stutters and this code has been tested quite often plus is easy to repurpose.
//Plus it's (partially) based on RL's code and Llemon has at least looked at closing the clips.

@Singleton
@Slf4j
public class SoundEngine {

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private OkHttpClient okHttpClient;

    private static final long CLIP_MTIME_UNLOADED = -2;

    private long lastClipMTime = CLIP_MTIME_UNLOADED;
    private Clip clip = null;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean loadClip(Sound sound) {
        try (InputStream stream = new BufferedInputStream(SoundFileManager.getSoundStream(sound))) { //Potentially check out https://github.com/m0bilebtw/c-engineer-completed/commit/d251b6f790ca9dc1b83c03705225c22eceb793d7 if you want to change this a bit.
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(stream)) {
                clip.open(audioInputStream); //Liable to error with pulseaudio, works on windows, one user informed that mac works
            }
            return true;
        } catch (UnsupportedAudioFileException e) {
            //Beep when it fails to load the sound
            Toolkit.getDefaultToolkit().beep();
            log.warn("Failed to load Resource Area Notifier sound " + sound + ". Use 16-bit .wav files (not 32-bit) if trying to use custom files. Attempting to delete and re-download the file now.", e);
            //Delete problematic file
            File toDelete = new File(SoundFileManager.getDOWNLOAD_DIR(), sound.getResourceName());
            toDelete.delete();
            //Re-download just deleted file
            executor.submit(() -> {
                SoundFileManager.ensureDownloadDirectoryExists();
                SoundFileManager.downloadAllMissingSounds(okHttpClient);
            });
        } catch (IOException | LineUnavailableException e) {
            Toolkit.getDefaultToolkit().beep(); //Beep if file can't be loaded for some reason
            log.warn("Failed to load Resource Area Notifier sound " + sound, e);
        }
        return false;
    }

    public void playClip(Sound sound) {
        long currentMTime = System.currentTimeMillis(); //Maybe swap to nanoTime at some point?
        if (clip == null || currentMTime != lastClipMTime || !clip.isOpen()) {
            if (clip != null && clip.isOpen()) {
                clip.close();
            }

            try {
                clip = AudioSystem.getClip();
            } catch (LineUnavailableException e) {
                lastClipMTime = CLIP_MTIME_UNLOADED;
                Toolkit.getDefaultToolkit().beep(); //Beep if failed to get the clip
                log.warn("Failed to get clip for Resource Area Notifier sound " + sound, e);
                return;
            }

            lastClipMTime = currentMTime;
            if (!loadClip(sound)) {
                return;
            }
        }

        //User configurable volume
        FloatControl volume = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float gain = 20f * (float) Math.log10(ResourceAreaNotifierPlugin.getNotificationSoundVolume() / 100f);
        gain = Math.min(gain, volume.getMaximum());
        gain = Math.max(gain, volume.getMinimum());
        volume.setValue(gain);

        //From RuneLite base client Notifier class:
        //Using loop instead of start + setFramePosition prevents the clip
        //from not being played sometimes, presumably a race condition in the
        //underlying line driver
        clip.loop(0);
    }

    //Close so we don't leak memory when shutting down the plugin
    public void close() {
        if (clip != null && clip.isOpen()) {
            clip.close();
        }
    }
}