package bor.samsara.questing.events.concrete;

import bor.samsara.questing.SamsaraFabricQuesting;
import bor.samsara.questing.events.QuestListener;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestManager {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private QuestManager() {}

    public static void attachQuestListenerToPertinentSubject(MongoPlayer playerState, MongoNpc npc, MongoQuest.Objective questObjective) {
        QuestListener questListener = new QuestListener(playerState.getUuid(), npc.getUuid(), questObjective);
        MongoQuest.Objective.Type objectiveType = questObjective.getType();
        switch (objectiveType) {
            case KILL -> SamsaraFabricQuesting.killSubject.attach(questListener);
            case COLLECT -> SamsaraFabricQuesting.collectItemSubject.attach(questListener);
            case TALK -> SamsaraFabricQuesting.talkToNpcSubject.attach(questListener);
            case FIN -> {}
            default -> log.warn("Unknown Objective Type '{}' when registering NPC {} for Player {}", objectiveType, npc.getName(), playerState.getName());
        }
    }

}

