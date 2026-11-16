package com.ftl2nd.nms;

import net.kyori.adventure.text.Component;

public record DialogSlotData(
    int slot,
    SlotState state,
    Component label,
    Component tooltip,
    String actionPath
) {
    public enum SlotState {
        SET,
        EMPTY,
        LOCKED
    }
}
