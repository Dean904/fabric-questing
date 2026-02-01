package bor.samsara.questing.events;

import java.util.UUID;

public record ActionSubscription(String playerUuid, String questUuid, String objectiveTarget, UUID objectiveUuid) {}
