package com.ftl2nd.nms;

import java.util.List;
import net.kyori.adventure.text.Component;

public record DialogViewData(
    Component title,
    List<StageData> stages,
    Component showMoreLabel,
    boolean useBedSprite
) {
    public record StageData(List<DialogSlotData> slots) {
    }
}
