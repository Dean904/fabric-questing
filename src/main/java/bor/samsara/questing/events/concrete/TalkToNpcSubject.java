package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TalkToNpcSubject extends QuestEventSubject {

    Map<String, List<QuestListener>> playerSubscriberMap = new HashMap<>();

    @Override
    public void attach(QuestListener listener) {
        playerSubscriberMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubscriberMap.get(listener.getPlayerUuid()).add(listener);
    }

    @Override
    public void detach(QuestListener listener) {
        playerSubscriberMap.get(listener.getPlayerUuid()).remove(listener);

    }

    @Override
    public void detachPlayer(String playerUuid) {
        playerSubscriberMap.remove(playerUuid);
    }

    @Override
    public Object hook() {
        // TODO whats the 'talk' workflow? Quest to Talk to NPC = COmpletion, opens dialogue on target ?
        return null;
    }
}
