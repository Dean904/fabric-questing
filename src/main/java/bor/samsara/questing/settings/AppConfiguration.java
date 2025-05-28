package bor.samsara.questing.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class AppConfiguration {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    public static final String MONGO_URI = "MONGO_URI";


    private static final File configFile = new File("./config/questing/fabric_quest.config");
    private static final Properties appProperties = new Properties();


    private AppConfiguration() {
        // TODO add a command to /reload Properties from file
    }

    public static void loadConfiguration() {
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                log.info("Created configuration file at: {}", configFile.getAbsolutePath());
                appProperties.put(MONGO_URI, "mongodb://localhost:27017");
                try (FileWriter fileWriter = new FileWriter(configFile)) {
                    appProperties.store(fileWriter, "Samsara Fabric Questing Configuration");
                    appProperties.load(configFile.toURI().toURL().openStream());
                }
            } catch (IOException e) {
                log.error("Failed to create configuration file: {}", e.getMessage(), e);
            }
        } else {
            try (FileWriter writer = new FileWriter(configFile, true)) {
                appProperties.load(configFile.toURI().toURL().openStream());
            } catch (IOException e) {
                log.error("Failed to load configuration file: {}", e.getMessage(), e);
            }
        }


    }

    public static String getConfiguration(String key) {
        return appProperties.getProperty(key);
    }

}
