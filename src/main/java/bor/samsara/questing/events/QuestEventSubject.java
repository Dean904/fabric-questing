package bor.samsara.questing.events;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public abstract class QuestEventSubject {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    protected Map<String, List<ActionSubscription>> playerSubsriptionMap = new HashMap<>();

    @Deprecated
    public abstract Object hook();

    public void attach(ActionSubscription listener) {
        log.debug("{} Attached for {} ", this.getClass().getSimpleName(), listener);
        playerSubsriptionMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.getPlayerUuid()).add(listener);
    }

    public void detach(ActionSubscription listener, Iterator<ActionSubscription> ite) {
        log.debug("{} Detached for {} ", this.getClass().getSimpleName(), listener);
        List<ActionSubscription> listeners = playerSubsriptionMap.get(listener.getPlayerUuid());
        ite.remove();
        if (CollectionUtils.isEmpty(listeners)) {
            log.debug("No listeners left for player, removing from map.");
            playerSubsriptionMap.remove(listener.getPlayerUuid());
        }
    }

    public void detachPlayer(String playerUuid) {
        playerSubsriptionMap.remove(playerUuid);
    }


}
