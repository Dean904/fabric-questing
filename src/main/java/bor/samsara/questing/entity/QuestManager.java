package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;

import java.util.HashMap;
import java.util.Map;

public class QuestManager {

    private static QuestManager singleton;

    Map<String, MongoNpc> npcMap = new HashMap<>(); // Static NPC data probably shouldnt live in memory
    Map<String, MongoPlayer> playerMap = new HashMap<>(); // Player stats between onJoin and onLeave? Good mem

    private QuestManager() {}

    public static QuestManager getInstance() {
        if (singleton == null) {
            singleton = new QuestManager();
        }
        return singleton;
    }

    public String getNextDialogue(String playerUuid, String questNpcUuid) {
        MongoPlayer playerState = getOrFindPlayer(playerUuid);
        MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getActiveQuestForNpc(questNpcUuid);

        MongoNpc npc = getOrFindNpc(questNpcUuid);
        MongoNpc.Quest staticQuest = npc.getQuests().get(activeQuestForNpc.getSequence());

        if (null != staticQuest) {
            long dialogueOffset = activeQuestForNpc.getDialogueOffset();
            activeQuestForNpc.setDialogueOffset((dialogueOffset + 1) % staticQuest.getDialogue().size());
            return staticQuest.getDialogue().get((int) dialogueOffset);
        }

        return "";
    }

    public boolean incrementQuestObjectiveCount(String playerUuid, String questNpcUuid) {
        MongoPlayer playerState = getOrFindPlayer(playerUuid);
        MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getActiveQuestForNpc(questNpcUuid);
        int objectiveCount = activeQuestForNpc.getObjectiveCount() + 1;
        activeQuestForNpc.setObjectiveCount(objectiveCount);

        MongoNpc npc = getOrFindNpc(questNpcUuid);
        MongoNpc.Quest staticQuest = npc.getQuests().get(activeQuestForNpc.getSequence());

        if (null != staticQuest && staticQuest.getObjective().getRequiredCount() <= objectiveCount) {
            activeQuestForNpc.setComplete(true);
            // TODO when should we increment to next active quest sequence? Now or on is_complete right click? Finer conversation controls?
            // TODO remove register from Event Manager
        }

        PlayerMongoClient.updatePlayer(playerState);

        return true;
    }

    private MongoPlayer getOrFindPlayer(String playerUuid) {
        if (playerMap.containsKey(playerUuid))
            return playerMap.get(playerUuid);

        MongoPlayer playerByUuid = PlayerMongoClient.getPlayerByUuid(playerUuid);
        playerMap.put(playerUuid, playerByUuid);
        return playerByUuid;
    }

    private MongoNpc getOrFindNpc(String questNpcUuid) {
        if (npcMap.containsKey(questNpcUuid))
            return npcMap.get(questNpcUuid);

        MongoNpc playerByUuid = NpcMongoClient.getNpc(questNpcUuid);
        npcMap.put(questNpcUuid, playerByUuid);
        return playerByUuid;
    }


}

