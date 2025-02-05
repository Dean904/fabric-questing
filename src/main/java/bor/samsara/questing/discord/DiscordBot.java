package bor.samsara.questing.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class DiscordBot {

    public static final Logger LOGGER = LoggerFactory.getLogger("DiscordBot");

    public static final String MC_ACTIVITY_CHANNEL = "1276340482439647263";
    public static final String MC_REPORT_CHANNEL = "1276351296256868434";

    private JDA jda;
    private TextChannel channel;

    public DiscordBot(String token, String channelId) {
        try {
            jda = JDABuilder.createDefault(token).build();
            jda.awaitReady();
            channel = jda.getTextChannelById(channelId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        channel.sendMessage(message).queue();
    }

    public void sendFileToChannel(String channelId, File file, String message) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).addFiles(FileUpload.fromData(file)).queue();
        } else {
            LOGGER.warn("Channel with ID " + channelId + " not found.");
        }
    }

}

