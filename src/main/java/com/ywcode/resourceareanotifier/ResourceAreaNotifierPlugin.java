package com.ywcode.resourceareanotifier;

import com.google.inject.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.events.*;
import net.runelite.client.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.*;
import okhttp3.*;

import javax.annotation.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@PluginDescriptor(
		name = "Resource Area Notifier",
		description = "Notifies when the gate in the wilderness Resource Area opens.",
		tags = {"crabs,dark crab,crab,dark crab gang,darkcrabgang,dcg,wilderness,resource,area,crab pen,piles,gate,notifier,notification"}
)
public class ResourceAreaNotifierPlugin extends Plugin {

	// ------------- Wall of config vars -------------
	// Vars are quite heavily cached so could probably just config.configKey(). However, the best practice behavior in plugins is to have a bunch of variables to store the results of the config methods, and check it in startUp/onConfigChanged. It feels redundant, but it's better than hitting the reflective calls every frame. --LlemonDuck
	private static boolean notifyOnGateOpen;
	@Getter
	private static NotificationOverlay notificationOverlay;
	@Getter
	private static Color solidFlashColor;
	@Getter
	private static Color boxColorPrimary;
	@Getter
	private static Color boxColorSecondary;
	@Getter
	private static int boxHeight;
	@Getter
	private static int boxWidth;
	private static int minimumNotificationOverlayDuration;
	private static int maximumNotificationOverlayDuration;
	private static boolean removeOverlayInteracting;
	private static boolean removeOverlayIgnore;
	private static Sound notificationSound;
	@Getter
	private static int notificationSoundVolume;
	private static boolean ignoreVeryClose;
	private static boolean ignoreClose;
	private static boolean ignoreFriends;
	private static boolean ignoreFCMembers;
	private static boolean ignoreCCMembers;
	private static final List<String> playersToIgnore = new ArrayList<>();
	// ------------- End of wall of config vars -------------

	private static final int gateOpenFloorId = 38848;
	private static final WorldPoint gateOpenFloorWorldPoint = new WorldPoint(3184, 3944, 0);
	private static final WorldPoint outsideGateWorldPoint = new WorldPoint(3184, 3945, 0);
	private static final int resourceAreaX1 = 3174;
	private static final int resourceAreaX2 = 3196;
	private static final int resourceAreaY1 = 3924;
	private static final int resourceAreaY2 = 3944;
	private static final int resourceAreaY2Plus3 = 3947; //3944 is resource area end, this is +3. Makes caching players a bit easier probably
	private static final int gateTilesVisibleY = 3930;
	private static final int gateVeryCloseY = 3939;
	//private static final WorldArea resourceAreaWorldArea = new WorldArea(resourceAreaX1, resourceAreaY1, resourceAreaX2-resourceAreaX1+1, resourceAreaY2-resourceAreaY1+1, 0);
	private static final WorldArea resourceAreaYPlus3WorldArea = new WorldArea(resourceAreaX1, resourceAreaY1, resourceAreaX2-resourceAreaX1+1, resourceAreaY2Plus3-resourceAreaY1+1, 0); //Location x, y with width 1 and height 1 would be a 1x1 tile, but x-x and y-y would be 0 so +1
	private static final WorldArea resourceAreaPlayerSpawnedWorldArea = new WorldArea(resourceAreaX1, gateTilesVisibleY, resourceAreaX2-resourceAreaX1+1, resourceAreaY2-gateTilesVisibleY+1, 0); //Location x, y with width 1 and height 1 would be a 1x1 tile, but x-x and y-y would be 0 so +1
	private static final List<Player> currentTickOutsidePlayers = new ArrayList<>();
	private static final List<Player> currentTickInsidePlayers = new ArrayList<>();
	private static final List<Player> previousTickOutsidePlayers = new ArrayList<>();
	private static final List<Player> previousTickInsidePlayers = new ArrayList<>();
	private static boolean listsEmpty = true;
	private static boolean gateOpenedThisTick; //The default value for a boolean (primitive) is false.
	private static boolean overlayActive; //The default value for a boolean (primitive) is false. To prevent >1 overlay active at the same time
	private static int overlayActiveTime; //int fields default to 0 already
	private static boolean forceOverlayRemovalAfterMinDuration; //The default value for a boolean (primitive) is false.
	private static int gateOpenedAmount; //int fields default to 0 already
	private static long nanoOverlayStart;
	private static boolean localPlayerIsInResourceAreaAndYPlus3; //The default value for a boolean (primitive) is false.

	@Inject
	private Client client;

	@Inject
	private ResourceAreaNotifierConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private SoundEngine soundEngine;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BoxNotificationOverlay boxNotificationOverlay;

	@Inject
	private ScreenNotificationOverlay screenNotificationOverlay;

	@Inject
	private ConfigManager configManager;

	@Override
	public void startUp() throws Exception {
		updateConfig(true);
		//Check if download dir exists/create it and download missing soundfiles
		executor.submit(() -> {
			SoundFileManager.ensureDownloadDirectoryExists();
			SoundFileManager.downloadAllMissingSounds(okHttpClient);
		});
	}

	@Override
	public void shutDown() throws Exception {
		clearCacheLists();
		soundEngine.close(); //Prevent memory leak
		removeNotificationOverlays(); //Remove all overlays on shutdown
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged) {
		if (configChanged.getGroup().equals("ResourceAreaNotifier")) {
			//Update config values
			updateConfig(false);

			String configKey = configChanged.getKey();
			//Update playersToIgnore separately, only when specifically that key is changed. Probably doesn't matter that much, but maybe some weirdo puts in 10k values and makes it somewhat impactful to run?
			if (configKey.equals("playersToIgnore")) {
				convertCommaSeparatedConfigStringToList(config.playersToIgnore(), playersToIgnore);
			}

			//Alternatively use the old code. See code at the end if you ever decide to swap back to it.
			if (client.getGameState() != GameState.STARTING && client.getGameState() != GameState.UNKNOWN) {
				switch (configKey) {
					//Remove overlays when user disables overlay or change the overlay if it's active
					case "notificationOverlay":
					case "solidFlashColor":
					case "boxColorPrimary":
					case "boxColorSecondary":
					case "boxHeight":
					case "boxWidth":
						//Remove and readd overlay if active since I decided to make the Box and Flash notification a different class + show overlay since I want the user to see the changes
						removeNotificationOverlays();
						addNotificationOverlays(); //Doesn't add if notificationOverlay == NotificationOverlay.Disabled
						break;
					case "notificationSound":
						//Play sound when user selects it, so they know what sound it is
						playNotificationSound();
						break;
				}
			}
		}
	}

	private void updateConfig(boolean updateList) {
		notifyOnGateOpen = config.notifyOnGateOpen();
		notificationOverlay = config.notificationOverlay();
		solidFlashColor = config.solidFlashColor();
		boxColorPrimary = config.boxColorPrimary();
		boxColorSecondary = config.boxColorSecondary();
		boxHeight = config.boxHeight();
		boxWidth = config.boxWidth();
		minimumNotificationOverlayDuration = config.minimumNotificationOverlayDuration();
		maximumNotificationOverlayDuration = config.maximumNotificationOverlayDuration();
		removeOverlayInteracting = config.removeOverlayInteracting();
		removeOverlayIgnore = config.removeOverlayIgnore();
		notificationSound = config.notificationSound();
		notificationSoundVolume = config.notificationSoundVolume();
		ignoreVeryClose = config.ignoreVeryClose();
		ignoreClose = config.ignoreClose();
		ignoreFriends = config.ignoreFriends();
		ignoreFCMembers = config.ignoreFCMembers();
		ignoreCCMembers = config.ignoreCCMembers();
		if (updateList) { //Probably doesn't matter that much, but maybe some weirdo puts in 10k values and makes it somewhat impactful to run?
			convertCommaSeparatedConfigStringToList(config.playersToIgnore(), playersToIgnore);
		}
		//In case maximum overlay duration is less than minimum duration, set maximum to minimum
		if (maximumNotificationOverlayDuration < minimumNotificationOverlayDuration) {
			maximumNotificationOverlayDuration = minimumNotificationOverlayDuration;
			configManager.setConfiguration("ResourceAreaNotifier", "maximumNotificationOverlayDuration", minimumNotificationOverlayDuration);
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged) {
		removeNotificationOverlays(); //Tbh idk if this needed; OverlayManager handles some of it already and if it flips the plugin off, it's already removed anyway but enfin
		updateConfig(true);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			//Remove overlays when hopping/logging out
			removeNotificationOverlays();
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned wallObjectSpawned) {
		//If object = gate open ID, and location = gate location, and local player is in resource area YPlus3, then set flag
		if (localPlayerIsInResourceAreaAndYPlus3 && wallObjectSpawned.getWallObject().getId() == gateOpenFloorId && wallObjectSpawned.getTile().getWorldLocation().equals(gateOpenFloorWorldPoint)) {
			gateOpenedThisTick = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		//Tbh don't think caching will improve performance that much, but so it's only called every gametick instead of every gametick, when a new player spawns etc I guess.
		localPlayerIsInResourceAreaAndYPlus3 = isInResourceAreaAndYPlus3();

		//Events fire like this: tick 1 onGameTick WorldPoint BEFORE moving through gate, tick 2 onWallObjectSpawned, tick 2 onGameTick WorldPoint AFTER moving through gate. So moving takes 1 tick but onGameTick always fires as the last event of the tick (as it should).
		if (localPlayerIsInResourceAreaAndYPlus3) { //if localPlayer = in resource area +3
			int localPlayerY = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getY();
			//Alternatively use client.getLocalPlayer().getWorldLocation().getY() => this would result in the same outcome in this case. The currently used method just accounts for instances (which is irrelevant when in the resource area, I guess)
			if (localPlayerY < gateTilesVisibleY) { //Players potentially not visible anymore (lists might be/are incorrect), clear lists.
				clearCacheLists();
			} else { //aka if (localPlayerY >= gateTilesVisibleY) {
				cachePlayers(); //Cache current players. This also updates the previousTick lists
			}

			if (gateOpenedThisTick) { //finally, if the gate opened this tick, (potentially) notify
				notifyGateOpened();
			}

			//if player is not in resource area YPlus3
		} else if (!listsEmpty) { //Clear lists when these are not empty and when outside resource area. Doubt the boolean optimization instead of just calling clearCacheArrayLists() every time is really an optimization and/or significant but enfin.
			clearCacheLists();
		}

		//Conditionally remove overlays, based on config such as min/max durations, interacting with the client etc.
		conditionalRemoveOverlay();
		gateOpenedThisTick = false; //Reset flag
		increaseOverlayTimer(); //Already checks if overlay is active
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned playerSpawned) {
		//Players can't log in to the resource area

		if (localPlayerIsInResourceAreaAndYPlus3 && gateOpenedAmount > 0) {
			Player spawnedPlayer = playerSpawned.getPlayer();
			String spawnedPlayerUsername = spawnedPlayer.getName();

			//Check if ignore options are enabled and if so, reduce gateOpenedAmount when ignored player spawns
			if ((ignoreFriends && spawnedPlayer.isFriend()) ||
					(ignoreFCMembers && spawnedPlayer.isFriendsChatMember()) ||
					(ignoreCCMembers && spawnedPlayer.isClanMember()) ||
					(spawnedPlayerUsername != null && playersToIgnore.contains(Text.standardize(spawnedPlayerUsername)))) {

				//Only check spawns inside the Northern-ish part of the resource area
				//alternatively:
				/*WorldPoint spawnedPlayerWorldPoint = spawnedPlayer.getWorldLocation();
				int spawnedPlayerX = spawnedPlayerWorldPoint.getX();
				int spawnedPlayerY = spawnedPlayerWorldPoint.getY();
				if (spawnedPlayerX >= resourceAreaX1 && spawnedPlayerX <= resourceAreaX2 && spawnedPlayerY >= gateTilesVisibleY && spawnedPlayerY <= resourceAreaY2) {*/
				if (resourceAreaPlayerSpawnedWorldArea.contains(spawnedPlayer.getWorldLocation())) {
					gateOpenedAmount--;
				}
			}
		}
	}

	private void clearCacheLists() {
		//Clear the cache lists, e.g. when they are not reliable anymore/when the player is too far away. Set boolean used for probably irrelevant optimization to true.
		currentTickInsidePlayers.clear();
		previousTickInsidePlayers.clear();
		currentTickOutsidePlayers.clear();
		previousTickOutsidePlayers.clear();
		listsEmpty = true;
	}

	private void removeNotificationOverlays() {
		//Remove overlays
		overlayManager.remove(boxNotificationOverlay);
		overlayManager.remove(screenNotificationOverlay);
		//Reset all (overlay related) flags
		gateOpenedAmount = 0;
		overlayActive = false;
		overlayActiveTime = 0;
		forceOverlayRemovalAfterMinDuration = false;
		//Resetting the idle long is probably not necessary but can't hurt
		nanoOverlayStart = 0;
	}

	private void convertCommaSeparatedConfigStringToList(String configString, List<String> listToConvertTo) { //Value can be inlined since only used once, but I'd like to keep it useful for other lists in the future
		//Convert a CSV config string to a list
		listToConvertTo.clear();
		listToConvertTo.addAll(Text.fromCSV(Text.standardize(configString)));
	}

	private void addNotificationOverlays() {
		//Don't add overlay if it's disabled (see case: Disabled) or if it's already active. Also don't add it when client is starting, logged out, hopping, disconnected etc.
		if (client.getGameState() == GameState.LOGGED_IN || client.getGameState() == GameState.LOADING) {
			if (!overlayActive) { //Separate here because I want e.g. overlayActiveTime to always be set to 0 when loggedin/loading, also when overlayActive = true
				switch (notificationOverlay) {
					case Disabled:
						return; //Returns so overlayActive is never set to true
					//Box notifications
					case BoxSolid:
					case BoxFlash:
						overlayManager.add(boxNotificationOverlay);
						break;
					//Screen notifications
					case Solid:
					case Flash:
						overlayManager.add(screenNotificationOverlay);
						break;
				}
				//Set boolean that overlay is active
				overlayActive = true;
			}
			//Reset the timer in case the notification is still active while add overlays is called
			overlayActiveTime = 0;
			//Reset force removal flag in case a new notification got sent while the flag had already been set but the notification hadn't been removed yet (e.g. due to minimum duration not having passed yet), thus having not been reset yet
			forceOverlayRemovalAfterMinDuration = false;
			//Set nanoTime at start of overlay, so it can be used later on in a comparison to check if a user is still idle or interacted with the client.
			nanoOverlayStart = System.nanoTime();
		}
	}

	private void playNotificationSound() {
		//Don't play sound when it's disabled in config, otherwise submit to executor
		if (notificationSound == Sound.DISABLED) {
			return;
		}
		executor.submit(() -> { //Not sure if submitting to an executor is required here, but better safe than sorry with all the client stuttering due to audio. Otherwise it'd also probably e.g. keep the config dropdown box stuck open if it's slowly loading the clips, which is not nice.
			soundEngine.playClip(notificationSound);
		});
	}

	private boolean isInResourceAreaAndYPlus3() { //Is local player in resource area (Y+3)?
		Player player = client.getLocalPlayer();
		if (player == null) {
			return false;
		}
		WorldPoint playerWorldPoint = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return resourceAreaYPlus3WorldArea.contains(playerWorldPoint);
		//Alternatively:
		//int localPlayerX = playerWorldPoint.getX();
		//int localPlayerY = playerWorldPoint.getY();
		//return localPlayerX >= resourceAreaX1 && localPlayerX <= resourceAreaX2 && localPlayerY >= resourceAreaY1 && localPlayerY <= resourceAreaY2Plus3;
	}

	private void cachePlayers() {
		listsEmpty = false;
		updateLists(previousTickOutsidePlayers, currentTickOutsidePlayers); //Clear previous list and addAll elements of current
		updateLists(previousTickInsidePlayers, currentTickInsidePlayers);
		//Populate currentTickLists
		List<Player> players = client.getPlayers();
		for (Player player : players) {
			WorldPoint playerWorldPoint = player.getWorldLocation();
			if (playerWorldPoint.equals(gateOpenFloorWorldPoint)) { //Player standing inside
				currentTickInsidePlayers.add(player);
				continue; //Go to next iteration because a player can't be on multiple tiles on the same tick
			}
			if (playerWorldPoint.equals(outsideGateWorldPoint)) { //Player standing outside
				currentTickOutsidePlayers.add(player);
			}
		}
	}

	private void updateLists(List<Player> listToCopyTo, List<Player> listToCopyFrom) {
		//Clear the new list, then add all elements from the old list, then clear the old list
		listToCopyTo.clear();
		listToCopyTo.addAll(listToCopyFrom);
		listToCopyFrom.clear();
	}

	private void notifyGateOpened() {
		//If player should be notified, notify and increase gateOpenedAmount
		if (shouldNotify()) {
			gateOpenedAmount++;
			if (notifyOnGateOpen) {
				notifier.notify("Wilderness Resource Area gate opened!");
			}
			addNotificationOverlays(); //Method already checks if this is set to NotificationOverlay.Disabled
			playNotificationSound(); //Method already checks if this is set to NotificationSound.Disabled
		}
	}

	private boolean shouldNotify() {
		Player player = client.getLocalPlayer();
		if (player == null) {
			return false;
		}

		int localPlayerY = WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getY();
		if (localPlayerY < gateTilesVisibleY) { //No cached players, notify in all cases
			return true;
		}

		//if (localPlayerY >= gateTilesVisibleY) { //From here out this is true since !(localPlayerY < gateTilesVisibleY)
		if (ignoreClose || (ignoreVeryClose && localPlayerY >= gateVeryCloseY)) { //Ignore when player is close config value (no Y check needed because see line above) or ignore when very close as defined by config
			return false;
		}

		Player gatingPlayer = getPlayerThatGated();

		if (gatingPlayer == null) { //If none of the cached players went in or out, cache is incorrect or incomplete => always notify. E.g. when just running in vision on the tick that the person enters.
			return true;
		}

		//Local player & cached players, check ignore settings first. gatingPlayer can't be null since that already returned
		if ((gatingPlayer == player) ||
				(ignoreFriends && gatingPlayer.isFriend()) ||
				(ignoreFCMembers && gatingPlayer.isFriendsChatMember()) ||
				(ignoreCCMembers && gatingPlayer.isClanMember())) {
			return false;
		}

		//Ignore player if username is entered in config Text.standardized ignore CSV string
		String gatingPlayerUsername = gatingPlayer.getName();
		if (gatingPlayerUsername != null && playersToIgnore.contains(Text.standardize(gatingPlayerUsername))) {
			return false;
		}

		//Ignore player that left the resource area
		if (!getIfPlayerEntered(gatingPlayer)) { //This if statement could be simplified a bit (replace if statement with return getIfPlayerEntered(gatingPlayer);) but kept here for ease of reading. Should probably look into cleaning up this tree of if statements in the future...
			return false;
		}
		return true;
	}

	@Nullable
	private Player getPlayerThatGated() {
		//Compare lists that contain players from the previous tick and current tick.
		//If current tick player is on previous tick list, then he gated (entered or left) => return the player.
		//Only one player can gate per tick, so returning the first (and in practice only possible) match is fine!
		//If no one gated/if this is not cached, return null
		if (getPlayerEnteredOrLeft(previousTickOutsidePlayers, currentTickInsidePlayers) != null) {
			return getPlayerEnteredOrLeft(previousTickOutsidePlayers, currentTickInsidePlayers);
		}
		if (getPlayerEnteredOrLeft(previousTickInsidePlayers, currentTickOutsidePlayers) != null) {
			//This might be irrelevant in the grand picture. It's used further in shouldNotify to e.g. get if the player left or entered but
			//since we don't notify for leaving players anyway, I could maybe also just remove this if statement and just return null
			//Right now it's used as an "uh oh, something potentially went wrong, let's notify to be sure"
			//However, since gatingPlayer would then be null, it could also just return false then since no one gated or someone left, assuming that the caching would work perfectly without any edge cases I haven't thought of...
			//Probably don't change this though; there might be some unaccounted edge cases like when just running in vision on the tick that the person enters.
			return getPlayerEnteredOrLeft(previousTickInsidePlayers, currentTickOutsidePlayers);
		}
		return null;
	}

	@Nullable
	private Player getPlayerEnteredOrLeft(List<Player> previousTick, List<Player> currentTickOpposite) {
		//Compare lists that contain players from the previous tick and current tick.
		//If current tick player is on previous tick list, then he gated (entered or left) => return the player.
		//Only one player can gate per tick, so returning the first (and in practice only possible) match is fine!
		//If no one gated/if this is not cached, return null
		for (Player player : previousTick) {
			if (currentTickOpposite.contains(player)) {
				return player;
			}
		}
		return null;
	}

	private boolean getIfPlayerEntered(Player player) { //Player left = false, player entered = true
		return !player.getWorldLocation().equals(outsideGateWorldPoint); //Check if player is standing outside on that tick, if not, he entered
	}

	private void conditionalRemoveOverlay() {
		//if overlay = active
		if (overlayActive) {
			//Remove overlays if duration >= maximum duration
			if (overlayActiveTime >= maximumNotificationOverlayDuration) {
				removeNotificationOverlays(); //Alternatively remove return and set flag like I've done below
				return;
			}

			//Remove overlays if ignore is enabled and condition is met. Ignore this when outside of resource area since users might just want to test/set their config then
			if (removeOverlayIgnore && gateOpenedAmount == 0 && isInResourceAreaAndYPlus3()) {
				//Set flag for removal and code at the end of this method will check if it should be removed or should wait till minimum duration has passed
				forceOverlayRemovalAfterMinDuration = true;
			}

			//Remove overlay when interacting if the config setting is enabled
			//Any interaction with the client since the notification started will set the flag for removal
			long mouseIdleNanoTime = (long) client.getMouseIdleTicks() * Constants.CLIENT_TICK_LENGTH * 1000000;
			long keyboardIdleNanoTime = (long) client.getKeyboardIdleTicks() * Constants.CLIENT_TICK_LENGTH * 1000000;
			long currentNanoTime = System.nanoTime();
			//RL notifier has:
			/*if ((client.getMouseIdleTicks() < MINIMUM_FLASH_DURATION_TICKS
						|| client.getKeyboardIdleTicks() < MINIMUM_FLASH_DURATION_TICKS
						|| client.getMouseLastPressedMillis() > mouseLastPressedMillis) && clientUI.isFocused())*/
			//However during experimentation, it looks like client.getMouseIdleTicks does not only respond to mouse movement like the documentation notes, but also to mouse clicks
			//Thus I did not include client.getMouseLastPressedMillis()
			//I also don't see how getMouse/KeyboardIdleTicks could update if the client is not focused, so I've skipped that part as well
			//Edit: looked like that client.getMouseLastPressedMillis() was maybe used since it returns millis since unix epoch.
			//E.g. client.getKeyboardIdleTicks() < keyboardIdleTicksOverlayStart where keyboardIdleTicksOverlayStart = client.getKeyboardIdleTicks()
			//was problematic since you could be typing right when it happened, thus resulting in a very low keyboardIdleTicksOverlayStart aka making it very difficult to get below this
			//Thus I'll be comparing System.nanoTime when the overlay is added plus idle ticks in nanos to current nanoTime to detect if the user stopped being idle after the overlay was added
			//Using System.nanoTime is recommended over currentTimeMillis for durations, see https://stackoverflow.com/questions/37067929/best-approach-for-dealing-with-time-measures Instant.Now() probably uses currentTimeMillis...

			if (removeOverlayInteracting &&
					( ((nanoOverlayStart + mouseIdleNanoTime) < currentNanoTime)
					|| ((nanoOverlayStart + keyboardIdleNanoTime) < currentNanoTime) )) { //Yes, I do like superfluous parentheses

				//Set flag for removal and code at the end of this method will check if it should be removed or should wait till minimum duration has passed
				forceOverlayRemovalAfterMinDuration = true;
			}

			//Remove overlays if minimum duration has passed and flag is set, should basically always be last in this method
			if (forceOverlayRemovalAfterMinDuration && (overlayActiveTime >= minimumNotificationOverlayDuration)) {
				removeNotificationOverlays();
			}
		}
	}

	private void increaseOverlayTimer() {
		if (overlayActive) {
			overlayActiveTime++; //If overlay = active, count the amount of actually occurred GameTicks (even if they are 5 minutes or 0.3s in length)
			//Initially I was using more fancy stuff, but with GameTicks sometimes differing in duration etc. I was not a fan of it.
		}
	}

	@Provides
	ResourceAreaNotifierConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ResourceAreaNotifierConfig.class);
	}
}

//Old code onConfigChanged if you ever decide to swap back to this
				/*
				private static final List<String> overlayConfigOptions = ImmutableList.of("notificationOverlay", "solidFlashColor", "boxColorPrimary", "boxColorSecondary", "boxHeight", "boxWidth"); //Alternatively: use a switch statement

				//Remove overlays when user disables overlay or change the overlay if it's active
				if (overlayConfigOptions.contains(configKey)) {
					//Remove and readd if active since I decided to make the Box and Flash notification a different class + show overlay since I want the user to see the changes
					removeNotificationOverlays();
					addNotificationOverlays(); //Doesn't add if notificationOverlay == NotificationOverlay.Disabled
				}
				//Play sound when user selects it, so they know what sound it is
				if (configKey.equals("notificationSound")) {
					playNotificationSound();
				}
				*/