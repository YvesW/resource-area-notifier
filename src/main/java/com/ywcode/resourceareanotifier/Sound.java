package com.ywcode.resourceareanotifier;

//Uploaded to a separate branch of the plugin that only contains the .wav files
//PM Files cannot be updated without a filename or version change!
//PM Replace spaces with underscores in file names and add a version number, e.g. "_v1.0"
//Including a version in some file details can be difficult to get with default sound libraries probably, and calculating the CRC file hash on every plugin startup is probably less desirable
//Alternatively just create a new branch and change the URL in SoundFileManager

//PM 32-bit audio is not supported! The samples can be either 8-bit or 16-bit, with sampling rate from 8 kHz to 48 kHz.
//PM format of the filenames = Wake_Up_v1.0.wav
public enum Sound {
    DISABLED("Disabled", 1.0),
    WAKE_UP("Wake Up", 1.0),
    BADOOB("Badoob", 1.0),
    RPG_LEVEL_UP("RPG Level Up", 1.0),
    RPG_OPEN_CHEST("RPG Open Chest", 1.0),
    ATTENTION("Attention", 1.0),
    SELECT("Select", 1.0),
    START_GAME("Start Game", 1.0),
    STRUM("Strum", 1.0),
    DINK_DONK("Dink Donk", 1.0),
    TASK_COMPLETED("Task Completed", 1.0),
    BEEP_BOOP("Beep Boop", 1.0),
    BRIGHT_BELL("Bright Bell", 1.0),
    WAZOOP("Wazoop", 1.0),
    CHIME("Chime", 1.0),
    PAGE_FORWARD_CHIME("Page Forward Chime", 1.0),
    GO_BACK("Go Back", 1.0),
    PICKED_COIN_ECHO("Picked Coin Echo", 1.0),
    ICE_AND_CHILI("Ice and Chili", 1.0),
    STOP_BLUE("Stop Blue", 1.0),
    CALL_YOU("Call You", 1.0),
    HOLD_ME_BACK("Hold Me Back", 1.0),
    SLOW_WEEK("Slow Week", 1.0);

    private final String resourceName;
    private final double versionNumber;

    Sound(String resNam, double versionNum) {
        resourceName = resNam;
        versionNumber = versionNum;
    }

    String getResourceName() {
        return resourceName.replace(' ', '_')+"_v"+versionNumber+".wav";
    }
}
