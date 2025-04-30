package bor.samsara.questing.events;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class QuestEventSubject {

    protected Map<String, List<QuestListener>> playerSubscriberMap = new HashMap<>();

    @Deprecated
    public abstract Object hook();

    public void attach(QuestListener listener) {
        playerSubscriberMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubscriberMap.get(listener.getPlayerUuid()).add(listener);
    }

    public void detach(QuestListener listener) {
        List<QuestListener> listeners = playerSubscriberMap.get(listener.getPlayerUuid());
        listeners.remove(listener);
        if (CollectionUtils.isEmpty(listeners)) {
            playerSubscriberMap.remove(listener.getPlayerUuid());
        }
    }

    public void detachPlayer(String playerUuid) {
        playerSubscriberMap.remove(playerUuid);
    }


}
