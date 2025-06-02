package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestLogBook {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String DIV = ";;";
    public static final String QUEST_UUID = "npcName";
    public static final String PLAYER_UUID = "questIds";

    public static int open(PlayerEntity player, MongoQuest quest, MongoPlayer mongoPlayer) {
        try {
            ItemStack book = QuestLogBook.createTrackingBook(quest, mongoPlayer);
            if (player.getInventory().insertStack(book)) {
                log.debug("Given quest book for {}", quest.getTitle());
            } else {
                // If inventory is full, drop it on the ground
                player.sendMessage(Text.literal("You dropped your quest book! You should pick that up."), true);
                player.dropItem(book, true);
            }
        } catch (Exception e) {
            log.error("Failed to create quest book: {}", e.getMessage(), e);
        }
        return 1;
    }

    private static ItemStack createTrackingBook(MongoQuest quest, MongoPlayer player) {
        ItemStack bookStack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(QUEST_UUID, quest.getUuid());
        nbtCompound.putString(PLAYER_UUID, player.getUuid());
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        WrittenBookContentComponent t = getWrittenBookContentComponent(quest, player, bookStack);
        bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, t);
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(quest.getTitle()));

        return bookStack;
    }

    public static @NotNull WrittenBookContentComponent getWrittenBookContentComponent(MongoQuest quest, MongoPlayer player, ItemStack bookStack) {

        WrittenBookPageBuilder bookBuilder = new WrittenBookPageBuilder();
        bookBuilder.append(Text.literal(quest.getTitle()).styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(true))).newLine();

        if (StringUtils.isNotBlank(quest.getSummary()))
            bookBuilder.append(Text.literal(quest.getSummary())).newLine().newLine();

        MongoQuest.Objective objective = quest.getObjective();
        MongoPlayer.QuestProgress questProgress = player.getQuestPlayerProgressMap().get(quest.getUuid());
        int current = questProgress.getObjectiveCount();
        int required = objective.getRequiredCount();

        Formatting progressColor = (current >= required) ? Formatting.GREEN
                : (current == 0) ? Formatting.RED
                : Formatting.DARK_AQUA;

        bookBuilder.append(Text.literal(current + "/" + required + " ").styled(style -> style.withColor(progressColor).withBold(true)))
                .append(Text.literal(objective.getType().name()).formatted(Formatting.GRAY)).newLine()
                .append(Text.literal(formatMinecraftString(objective.getTarget())).formatted(Formatting.DARK_GRAY)).newLine();
        bookBuilder.append(Text.literal("___________________").formatted(Formatting.DARK_GRAY)).newLine();

        // Description
        if (StringUtils.isNotBlank(quest.getDescription()))
            bookBuilder.append(Text.literal(quest.getDescription())).newLine().newLine();

        // Rewards
        MongoQuest.Reward reward = quest.getReward();
        bookBuilder.append(Text.literal("Reward:").formatted(Formatting.BOLD, Formatting.DARK_GREEN)).newLine()
                .append(Text.literal(formatMinecraftString(reward.getItemName().toLowerCase()) + " x " + reward.getCount()).formatted(Formatting.GREEN)).newLine()
                .append(Text.literal(reward.getXpValue() + " XP").formatted(Formatting.GREEN));

        if (questProgress.isComplete()) {
            bookBuilder.append(Text.literal("\n[Complete]").formatted(Formatting.DARK_GREEN, Formatting.BOLD));
        }

        // Update the book content
        WrittenBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        return bookContent.withPages(bookBuilder.build());
    }

    @Deprecated
    public static @NotNull WrittenBookContentComponent getWrittenBookContentComponentOld(MongoQuest quest, MongoPlayer player, ItemStack bookStack) {
        MutableText bookText = Text.empty();
        bookText.append(Text.literal(quest.getTitle()).styled(style -> style.withColor(Formatting.GOLD)
                .withBold(true)
                .withItalic(false)
                .withUnderline(true)
                .withShadowColor(Formatting.YELLOW.getColorIndex())
        )).append("\n").append(Text.literal(quest.getSummary() + "\n\n")); // Bold & Gold

        MongoQuest.Objective objective = quest.getObjective();
        MongoPlayer.QuestProgress questProgress = player.getQuestPlayerProgressMap().get(quest.getUuid());
        int current = questProgress.getObjectiveCount();
        int required = objective.getRequiredCount();

        Formatting progressColor = (current >= required) ? Formatting.GREEN
                : (current >= required / 2) ? Formatting.YELLOW
                : Formatting.RED;

        bookText.append(Text.literal(current + "/" + required).styled(style -> style.withColor(progressColor).withBold(true)))
                .append(Text.literal(" "))
                .append(Text.literal(objective.getType().name()).formatted(Formatting.GRAY))
                .append(Text.literal("\n"))
                .append(Text.literal(formatMinecraftString(objective.getTarget()) + "\n").formatted(Formatting.DARK_GRAY));

        bookText.append(Text.literal("_____________\n").formatted(Formatting.DARK_GRAY));

        // Description
        bookText.append(Text.literal(quest.getDescription() + "\n\n"));

        // Rewards
        MongoQuest.Reward reward = quest.getReward();
        bookText.append(Text.literal("Reward:").formatted(Formatting.BOLD, Formatting.DARK_GREEN)).append("\n")
                .append(Text.literal(formatMinecraftString(reward.getItemName().toLowerCase()) + " x " + reward.getCount()).formatted(Formatting.GREEN)).append("\n")
                .append(Text.literal(reward.getXpValue() + " XP").formatted(Formatting.GREEN));

        if (questProgress.isComplete()) {
            bookText.append(Text.literal("\n[Complete]").formatted(Formatting.DARK_GREEN, Formatting.BOLD));
        }

        String fullText = bookText.getSiblings().stream().map(Text::getString).collect(Collectors.joining());
        List<String> chunks = splitOnWordBoundary(fullText, 256);
        List<RawFilteredPair<Text>> bookPagesOld = new ArrayList<>();
        for (String chunk : chunks) {
            Text pageText = Text.literal(chunk);
            bookPagesOld.add(new RawFilteredPair<>(pageText, Optional.of(pageText)));
        }

        // Update the book content
        WrittenBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        return bookContent.withPages(bookPagesOld);
    }

    public static List<String> splitOnWordBoundary(String text, int maxLength) {
        List<String> result = new ArrayList<>();
        int index = 0;

        while (index < text.length()) {
            int end = Math.min(index + maxLength, text.length());

            // Look for the last space or newline before maxLength
            int lastSpace = text.lastIndexOf(' ', end);
            int lastBreak = text.lastIndexOf('\n', end);
            int splitPoint = Math.max(lastSpace, lastBreak);

            if (splitPoint <= index || end == text.length()) {
                splitPoint = end; // force hard split if no good point found
            }

            result.add(text.substring(index, splitPoint).trim());
            index = splitPoint;
        }

        return result;
    }

    private static String formatMinecraftString(String itemName) {
        return Arrays.stream(itemName.replace("minecraft:", "").split("_"))
                .map(StringUtils::capitalize).collect(Collectors.joining(" "));
    }


}