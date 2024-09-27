package com.ywcode.resourceareanotifier;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

//Copyright (c) 2021, m0bilebtw
//Opted to largely use m0bilebtw's SoundEngine/SoundFileManager because sounds so often cause client stutters and this code has been tested quite often plus is easy to repurpose.

@Slf4j
public abstract class SoundFileManager {

    @Getter(AccessLevel.PACKAGE)
    private static final File DOWNLOAD_DIR = new File(RuneLite.RUNELITE_DIR.getPath() + File.separator + "resource-area-notifier-sounds");
    private static final String DELETE_WARNING_FILENAME = "EXTRA_FILES_WILL_BE_DELETED_BUT_FOLDERS_WILL_REMAIN"; //Warning for in the DOWNLOAD_DIR folder
    private static final File DELETE_WARNING_FILE = new File(DOWNLOAD_DIR, DELETE_WARNING_FILENAME);
    private static final HttpUrl RAW_GITHUB = HttpUrl.parse("https://raw.githubusercontent.com/YvesW/resource-area-notifier/sounds-only");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void ensureDownloadDirectoryExists() {
        if (!DOWNLOAD_DIR.exists()) {
            DOWNLOAD_DIR.mkdirs();
        }
        try {
            DELETE_WARNING_FILE.createNewFile();
        } catch (IOException ignored) {
        }
    }

    public static void downloadAllMissingSounds(final OkHttpClient okHttpClient) {
        //Get set of existing files in our dir - existing sounds will be skipped, unexpected files (not dirs, some sounds depending on config [edit: removed this option for now]) will be deleted
        final Set<String> filesPresent = getFilesPresent();

        //Download any sounds that are not yet present but desired
        for (Sound sound : getDesiredSoundList()) {
            final String fileNameToDownload = sound.getResourceName();
            if (filesPresent.contains(fileNameToDownload) || fileNameToDownload.equals(Sound.DISABLED.getResourceName())) { //Don't try to download a file called Disabled_v1.0.wav
                filesPresent.remove(fileNameToDownload);
                continue;
            }

            if (RAW_GITHUB == null) {
                //Hush intellij, it's okay, the potential NPE can't hurt you now
                log.error("Resource Area Notifications could not download sounds due to an unexpected null RAW_GITHUB value");
                return;
            }
            final HttpUrl soundUrl = RAW_GITHUB.newBuilder().addPathSegment(fileNameToDownload).build();
            final Path outputPath = Paths.get(DOWNLOAD_DIR.getPath(), fileNameToDownload);
            okHttpClient.newCall(new Request.Builder().url(soundUrl).build()).enqueue(new Callback() {
                //Even though it doesn't matter because it's submitted to the executorService, I'm using enqueue instead of execute. I hope Ron is proud of me.
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Resource Area Notifications could not download sounds (okHttp onFailure)", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.body() != null) {
                        try {
                            Files.copy(new BufferedInputStream(response.body().byteStream()), outputPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.error("Resource Area Notifications could not download sounds (okHttp onResponse)", e);
                        }
                    }
                }
            });
        }

        //filesPresent now contains only files in our directory that weren't desired
        //(e.g. old versions of sounds, now removed files)
        //We now delete them to avoid cluttering up disk space
        //We leave dirs behind (getFilesPresent ignores dirs) as we aren't creating those anyway, so they won't build up over time
        for (String filename : filesPresent) {
            final File toDelete = new File(DOWNLOAD_DIR, filename);
            //noinspection ResultOfMethodCallIgnored
            toDelete.delete();
        }
    }

    private static Set<String> getFilesPresent() {
        final File[] downloadDirFiles = DOWNLOAD_DIR.listFiles();
        if (downloadDirFiles == null || downloadDirFiles.length == 0) {
            return new HashSet<>();
        }

        return Arrays.stream(downloadDirFiles)
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .filter(filename -> !DELETE_WARNING_FILENAME.equals(filename))
                .collect(Collectors.toSet());
    }

    private static Set<Sound> getDesiredSoundList() { //Could also filter disabled here probably
        return Arrays.stream(Sound.values())
                .collect(Collectors.toSet());
    }

    public static InputStream getSoundStream(Sound sound) throws FileNotFoundException {
        return new FileInputStream(new File(DOWNLOAD_DIR, sound.getResourceName()));
    }
}