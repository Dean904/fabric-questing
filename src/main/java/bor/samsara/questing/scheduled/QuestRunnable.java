package bor.samsara.questing.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestRunnable implements Runnable {

    public static final Logger log = LoggerFactory.getLogger("QuestRunnable");


    public QuestRunnable() {
        log.info("Created QuestRunnable");
    }

    @Override
    public void run() {
        try {
            log.info("Running QuestRunnable");
        } catch (Exception e) {
            log.error("Failed to QuestRunnable", e);
        }
    }


}
