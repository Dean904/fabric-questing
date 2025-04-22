package bor.samsara.questing.events.concrete;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.events.QuestListener;
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

    public boolean isNpcActiveForPlayer(String playerUuid, String questNpcUuid) {
        MongoPlayer playerState = getOrFindPlayer(playerUuid);
        return playerState.getNpcActiveQuestMap().containsKey(questNpcUuid);
    }

    public void registerNpcForPlayer(String playerUuid, String questNpcUuid) {
        MongoPlayer playerState = getOrFindPlayer(playerUuid);
        playerState.getNpcActiveQuestMap().put(questNpcUuid, new MongoPlayer.ActiveQuest(0));
        PlayerMongoClient.updatePlayer(playerState);

        MongoNpc npc = getOrFindNpc(questNpcUuid);
        MongoNpc.Quest firstQuest = npc.getQuests().get(0);
        SamsaraFabricQuesting.killSubject.attach(new QuestListener(playerUuid, questNpcUuid, firstQuest.getObjective()));
    }

    public void activatePlayer(String playerUuid, MongoPlayer mongoPlayer) {
        playerMap.put(playerUuid, mongoPlayer);
        for (Map.Entry<String, MongoPlayer.ActiveQuest> activeQuestKv : mongoPlayer.getNpcActiveQuestMap().entrySet()) {
            String questNpcUuid = activeQuestKv.getKey();
            MongoNpc questNpc = getOrFindNpc(questNpcUuid);
            MongoNpc.Quest quest = questNpc.getQuests().get(activeQuestKv.getValue().getSequence());
            SamsaraFabricQuesting.killSubject.attach(new QuestListener(playerUuid, questNpcUuid, quest.getObjective()));
        }
    }

    public void deactivatePlayer(String playerUuid) {
        PlayerMongoClient.updatePlayer(getOrFindPlayer(playerUuid));
        SamsaraFabricQuesting.killSubject.detachPlayer(playerUuid);
        playerMap.remove(playerUuid);
    }

    public String getNextDialogue(String playerUuid, String questNpcUuid) {
        MongoPlayer playerState = getOrFindPlayer(playerUuid);
        MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getNpcActiveQuestMap().get(questNpcUuid);

        MongoNpc npc = getOrFindNpc(questNpcUuid);
        MongoNpc.Quest staticQuest = npc.getQuests().get(activeQuestForNpc.getSequence());

        if (null != staticQuest) {
            long dialogueOffset = activeQuestForNpc.getDialogueOffset();
            activeQuestForNpc.setDialogueOffset((dialogueOffset + 1) % staticQuest.getDialogue().size());
            return staticQuest.getDialogue().get((int) dialogueOffset);
        }

        return "";
    }

    public boolean incrementQuestObjectiveCount(QuestListener listener) {
        MongoPlayer playerState = getOrFindPlayer(listener.getPlayerUuid());
        MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getNpcActiveQuestMap().get(listener.getQuestUuid());
        int objectiveCount = activeQuestForNpc.getObjectiveCount() + 1;
        activeQuestForNpc.setObjectiveCount(objectiveCount);

        MongoNpc npc = getOrFindNpc(listener.getQuestUuid());
        MongoNpc.Quest staticQuest = npc.getQuests().get(activeQuestForNpc.getSequence());

        if (null != staticQuest && staticQuest.getObjective().getRequiredCount() <= objectiveCount) {
            // TODO when should we increment to next active quest sequence? Now or on is_complete right click? Finer conversation controls?
            activeQuestForNpc.setComplete(true);
            PlayerMongoClient.updatePlayer(playerState);
            return true;
        }

        PlayerMongoClient.updatePlayer(playerState);
        return false;
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

