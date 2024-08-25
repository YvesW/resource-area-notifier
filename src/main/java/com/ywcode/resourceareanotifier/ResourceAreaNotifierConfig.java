package com.ywcode.resourceareanotifier;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import java.awt.*;

@ConfigGroup("ResourceAreaNotifier")
public interface ResourceAreaNotifierConfig extends Config {

	@ConfigItem(
			keyName = "notifyOnGateOpen",
			name = "RL notification when gate opens",
			description = "Sends a RL notification when the gate opens. Adheres to the RL config for notifications.<br>" +
					"Thus the settings below will not have any effect on this notification. Configure it in the RuneLite config.",
			position = 0
	)
	default boolean notifyOnGateOpen() {
		return false;
	}

	@ConfigItem(
			keyName = "notificationOverlay",
			name = "Notification overlay",
			description = "Adds an overlay when the gate opens. Does NOT require the 'RL notification when gate opens' setting to be enabled.",
			position = 1
	)
	default NotificationOverlay notificationOverlay() {
		return NotificationOverlay.BoxFlash;
	}

	@Alpha
	@ConfigItem(
			keyName = "solidFlashColor",
			name = "Solid/flash color",
			description = "Configures the color for the 'flash' and 'solid' overlay option.",
			position = 2
	)
	default Color solidFlashColor()
	{
		return new Color(255, 0, 0, 70);
	}

	@Alpha
	@ConfigItem(
			keyName = "boxColorPrimary",
			name = "Box color primary",
			description = "Configures the color for the 'box' overlay option (primary).",
			position = 3
	)
	default Color boxColorPrimary()
	{
		return new Color(255, 0, 0, 150);
	}

	@Alpha
	@ConfigItem(
			keyName = "boxColorSecondary",
			name = "Box color secondary",
			description = "Configures the color for the 'box' overlay option (secondary when using 'box flash').",
			position = 4
	)
	default Color boxColorSecondary()
	{
		return new Color(70, 61, 50, 150);
	}

	@ConfigItem(
			keyName = "boxHeight",
			name = "Box height",
			description = "Configures the height for the 'box' overlay option.",
			position = 5
	)
	@Range(
			min = 1
	)
	default int boxHeight() {
		return 1;
	}

	@ConfigItem(
			keyName = "boxWidth",
			name = "Box width",
			description = "Configures the width for the 'box' overlay option.",
			position = 6
	)
	@Units(
			Units.PIXELS
	)
	default int boxWidth() {
		return ComponentConstants.STANDARD_WIDTH;
	}

	@ConfigItem(
			keyName = "minimumNotificationOverlayDuration",
			name = "Minimum overlay duration",
			description = "Minimum overlay duration in ticks.",
			position = 7
	)
	@Units(
			Units.TICKS
	)
	default int minimumNotificationOverlayDuration() {
		return 1;
	}

	@ConfigItem(
			keyName = "maximumNotificationOverlayDuration",
			name = "Maximum overlay duration",
			description = "Maximum overlay duration in ticks.",
			position = 8
	)
	@Units(
			Units.TICKS
	)
	default int maximumNotificationOverlayDuration() {
		return 16;
	}

	@ConfigItem(
			keyName = "removeOverlayInteracting",
			name = "Remove overlay on interaction",
			description = "Remove the overlay when interacting with the client. Respects 'minimum overlay duration'.",
			position = 9
	)
	default boolean removeOverlayInteracting() {
		return true;
	}

	@ConfigItem(
			keyName = "removeOverlayIgnore",
			name = "Remove overlay on ignored player",
			description = "Remove the overlay when an ignored player (e.g. friends) enters the resource area. False-positives are a possibility.",
			position = 10
	)
	default boolean removeOverlayIgnore() {
		return true;
	}

	@ConfigItem(
			keyName = "notificationSound",
			name = "Notification sound",
			description = "Plays a sound when the gate opens. Does NOT require the 'RL notify when gate opens' setting to be enabled.",
			position = 11
	)
	default Sound notificationSound() {
		return Sound.WAKE_UP;
	}

	@ConfigItem(
			keyName = "notificationSoundVolume",
			name = "Notification sound volume",
			description = "Notification sound volume.",
			position = 12
	)
	@Range(
			max = 200
	)
	@Units(
			Units.PERCENT
	)
	default int notificationSoundVolume() {
		return 100;
	}

	@ConfigItem(
			keyName = "ignoreVeryClose",
			name = "Ignore when very close to gate",
			description = "Don't notify when close to the gate. Prevents notification spam when you and your friends all hop worlds.",
			position = 13
	)
	default boolean ignoreVeryClose() {
		return true;
	}

	@ConfigItem(
			keyName = "ignoreClose",
			name = "Ignore when close to gate",
			description = "Don't notify when close-ish to the gate. Prevents notification spam when you and your friends all hop worlds.",
			position = 14
	)
	default boolean ignoreClose() {
		return false;
	}

	@ConfigItem(
			keyName = "ignoreFriends",
			name = "Ignore friends",
			description = "If your friends enter the resource area, they are ignored. The player must not appear as offline.",
			position = 15
	)
	default boolean ignoreFriends() {
		return true;
	}

	@ConfigItem(
			keyName = "ignoreFCMembers",
			name = "Ignore FC members",
			description = "If an FC member enters the resource area, they are ignored.",
			position = 16
	)
	default boolean ignoreFCMembers() {
		return true;
	}

	@ConfigItem(
			keyName = "ignoreCCMembers",
			name = "Ignore CC members",
			description = "If a CC member enters the resource area, they are ignored.",
			position = 17
	)
	default boolean ignoreCCMembers() {
		return true;
	}

	@ConfigItem(
			keyName = "playersToIgnore",
			name = "Players to ignore",
			description = "If these players enter the resource area, they are ignored. Comma,separated,input",
			position = 18
	)
	default String playersToIgnore()
	{
		return "DarkCrabGang";
	}
}