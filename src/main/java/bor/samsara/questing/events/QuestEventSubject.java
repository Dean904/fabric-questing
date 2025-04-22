package bor.samsara.questing.events;

public abstract class QuestEventSubject {

    public abstract void attach(QuestListener ps);

    public abstract void detach(QuestListener ps);

    public abstract void detachPlayer(String playerUuid);

    public abstract Object hook();

}
