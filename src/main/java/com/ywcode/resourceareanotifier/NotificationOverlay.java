package com.ywcode.resourceareanotifier;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum NotificationOverlay {
    Disabled("Disabled"),
    BoxSolid("Box solid"),
    BoxFlash("Box flash"),
    Solid("Solid"),
    Flash("Flash");

    private final String option;

    @Override
    public String toString() {
        return option;
    }

}