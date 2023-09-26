package com.ywcode.resourceareanotifier;

import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;

import javax.inject.*;
import java.awt.*;

public class ScreenNotificationOverlay extends OverlayPanel {

    private final Client client;

    @Inject
    private ScreenNotificationOverlay(Client client) {
        this.client = client;

        setPosition(OverlayPosition.DYNAMIC);
        //Alternatively use other OverlayLayers to e.g. not render on top of the inventory, chatbox, minimap etc
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        //If flash, set the color to flash
        if (ResourceAreaNotifierPlugin.getNotificationOverlay() == NotificationOverlay.Flash) {
            if (client.getGameCycle() % 40 >= 20) {
                graphics.setColor(ResourceAreaNotifierPlugin.getSolidFlashColor());
            } else {
                graphics.setColor(new Color(0, 0, 0, 0));
            }
        } else {
            //If solid
            graphics.setColor(ResourceAreaNotifierPlugin.getSolidFlashColor());
        }
        //Fill the rectangle using the client width and height
        graphics.fillRect(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
        return null;
    }
}