package com.ywcode.resourceareanotifier;

import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;

import javax.inject.*;
import java.awt.*;

public class BoxNotificationOverlay extends OverlayPanel {

    private final Client client;

    @Inject
    private BoxNotificationOverlay(Client client) {
        this.client = client;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add((LineComponent.builder())
                .left("Wilderness Resource Area gate opened!" + System.lineSeparator().repeat(ResourceAreaNotifierPlugin.getBoxHeight()) + " ".repeat(ResourceAreaNotifierPlugin.getBoxWidth()))
                //This is such a bad and hacky solution... Apologies if you had to read this. Feel free to contact me for some eye bleach, NFC / Ron / Llemon / whoever reads this.
                //SetPreferredSize didn't like setting a height, and it was only set as 0 in core. The text didn't particularly like panelComponent.setBorder
                //So I added the hacky lineSeparators, but then that stopped working after a certain width unless I padded the string further. Thus, this terrible monster was born.
                //And no, "Yves".repeat(W) is not a thing
                .build());

        //Set width. PM setPreferredSize does not work on children of panelComponent IIRC
        panelComponent.setPreferredSize(new Dimension(ResourceAreaNotifierPlugin.getBoxWidth(), 0));

        //Flash the box when set to flash
        if (ResourceAreaNotifierPlugin.getNotificationOverlay() == NotificationOverlay.BoxFlash) {
            if (client.getGameCycle() % 40 >= 20) {
                panelComponent.setBackgroundColor(ResourceAreaNotifierPlugin.getBoxColorPrimary());
            } else {
                panelComponent.setBackgroundColor(ResourceAreaNotifierPlugin.getBoxColorSecondary());
            }
        }

        //Solid box
        if (ResourceAreaNotifierPlugin.getNotificationOverlay() == NotificationOverlay.BoxSolid) {
            panelComponent.setBackgroundColor(ResourceAreaNotifierPlugin.getBoxColorPrimary());
        }

        setPosition(OverlayPosition.BOTTOM_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS); //Renders under the right-click menu
        return panelComponent.render(graphics);
    }
}