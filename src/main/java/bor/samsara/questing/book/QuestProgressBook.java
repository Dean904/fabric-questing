package bor.samsara.questing.book;

import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestProgressBook {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String QUEST_UUID = "questUuid";
    public static final String PLAYER_UUID = "playerUuid";
    public static final String PLAYER_PROGRESS = "playerProgress";
    public static final String IS_COMPLETE = "isComplete";

    public static int open(PlayerEntity player, MongoQuest quest, MongoPlayer mongoPlayer) {
        try {
            ItemStack book = QuestProgressBook.createTrackingBook(quest, mongoPlayer);
            if (player.getInventory().insertStack(book)) {
                log.debug("Giving {} quest book for {}", player.getName().getString(), quest.getTitle());
            } else {
                // If inventory is full, drop it on the ground
                player.sendMessage(Text.literal("You dropped your quest book! You should pick that up."), false);
                player.dropItem(book, true);
            }
        } catch (Exception e) {
            log.error("Failed to create quest book: {}", e.getMessage(), e);
        }
        return 1;
    }

    public static ItemStack createTrackingBook(MongoQuest quest, MongoPlayer player) {
        ItemStack bookStack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(QUEST_UUID, quest.getUuid());
        nbtCompound.putString(PLAYER_UUID, player.getUuid());
        nbtCompound.putInt(PLAYER_PROGRESS, player.getActiveQuestProgressionMap().get(quest.getUuid()).getObjectiveProgressions().hashCode());
        nbtCompound.putBoolean(QuestProgressBook.IS_COMPLETE, false);
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        WrittenBookContentComponent t = getWrittenBookContentComponent(quest, bookStack, player.getActiveQuestProgressionMap().get(quest.getUuid()));
        bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, t);
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(quest.getTitle()));

        return bookStack;
    }

    public static @NotNull WrittenBookContentComponent getWrittenBookContentComponent(MongoQuest quest, ItemStack bookStack, MongoPlayer.ActiveQuestState activeQuestState) {
        log.debug("Creating quest book content for quest: {}", quest.getTitle());
        WrittenBookPageBuilder bookBuilder = new WrittenBookPageBuilder();
        bookBuilder.append(Text.literal(quest.getTitle()).styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(true))).newLine();

        if (StringUtils.isNotBlank(quest.getSummary()))
            bookBuilder.append(Text.literal(quest.getSummary())).newLine().newLine();

        for (MongoQuest.Objective objective : quest.getObjectives()) {
            int current = objective.getRequiredCount();
            if (activeQuestState != null) {
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getProgress(objective);
                current = progress.getCurrentCount();
            }
            int required = objective.getRequiredCount();

            Formatting progressColor = (current >= required) ? Formatting.GREEN
                    : (current < 1) ? Formatting.RED
                    : Formatting.DARK_AQUA;
            bookBuilder.append(Text.literal(current + "/" + required + " ").styled(style -> style.withColor(progressColor).withBold(true)))
                    .append(Text.literal(objective.getType().name()).formatted(Formatting.GRAY)).newLine()
                    .append(Text.literal(formatMinecraftString(objective.getTarget())).formatted(Formatting.DARK_GRAY)).newLine();
        }
        bookBuilder.append(Text.literal("___________________").formatted(Formatting.DARK_GRAY)).newLine();

        // Description
        if (StringUtils.isNotBlank(quest.getDescription()))
            bookBuilder.append(Text.literal(quest.getDescription())).newLine().newLine();

        // Rewards
        MongoQuest.Reward reward = quest.getReward();
        if (reward != null && !Strings.CI.equalsAny(reward.getItemName(), "none", "na")) {
            bookBuilder.append(Text.literal("Reward:").formatted(Formatting.BOLD, Formatting.DARK_GREEN)).newLine()
                    .append(Text.literal(formatMinecraftString(reward.getItemName().toLowerCase()) + " x " + reward.getCount()).formatted(Formatting.GREEN)).newLine()
                    .append(Text.literal(reward.getXpValue() + " XP").formatted(Formatting.GREEN));
        }

        if (activeQuestState != null && activeQuestState.getObjectiveProgressions().values().stream().noneMatch(op -> op.getCurrentCount() < op.getObjective().getRequiredCount())) {
            bookBuilder.newLine().append(Text.literal("[Return to NPC]").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
        } else if (activeQuestState == null) {
            bookBuilder.newLine().append(Text.literal("[Complete]").formatted(Formatting.DARK_GREEN, Formatting.BOLD));
        }

        // Update the book content
        WrittenBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        return bookContent.withPages(bookBuilder.build());
    }

    private static String formatMinecraftString(String itemName) {
        return Arrays.stream(itemName.replace("minecraft:", "").split("_"))
                .map(StringUtils::capitalize).collect(Collectors.joining(" "));
    }


}