package bor.samsara.questing.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public class PlayerQuestState {

    private static Map<String, Map<Integer, Integer>> playerQuestDialogueOffset = new ConcurrentHashMap<>();

    public static int getDialogueOffset(String playerUuid, Integer activeQuest) {
        playerQuestDialogueOffset.putIfAbsent(playerUuid, new HashMap<>());
        Map<Integer, Integer> questDialogueOffset = playerQuestDialogueOffset.get(playerUuid);

        questDialogueOffset.putIfAbsent(activeQuest, 0);
        Integer currentOffset = questDialogueOffset.get(activeQuest);
        questDialogueOffset.put(activeQuest, currentOffset + 1);
        return currentOffset;
    }


}
