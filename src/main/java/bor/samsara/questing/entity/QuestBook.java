package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class BookStateUtil {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String DIV = ";;";

    public static int open(ServerCommandSource source, String name) {
        try {
            MongoNpc npc = NpcMongoClient.getFirstNpcByName(name); // load from DB
            ItemStack book = BookStateUtil.createConversationBook(npc);
            ServerPlayerEntity player = source.getPlayerOrThrow();
            if (player.getInventory().insertStack(book)) {
                source.sendFeedback(() -> Text.literal("Given conversation book for NPC " + npc.getName()), false);
            } else {
                // If inventory is full, drop it on the ground
                player.dropItem(book, false);
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }
        return 1;
    }

    /**
     * Convert an NPC's stageConversationMap into a WRITABLE_BOOK ItemStack
     * Ex1 = ##0;;hello;;world;;kill=zombie=5;;minecraft:emerald=3=100;;
     * Ex2 = ##0;;heyyy;;slaya!;;how are you today?;;collect=minecraft:rotten_flesh=5;;minecraft:emerald=3=100;;
     */
    private static ItemStack createConversationBook(MongoNpc npc) {
        ItemStack bookStack = new ItemStack(Items.WRITABLE_BOOK);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString("npcName", npc.getName());
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        List<RawFilteredPair<String>> pages = new ArrayList<>();
        for (int questId : npc.getQuestIds()) {
            // TODO complicated page-splitting if lines are too long.
            MongoQuest quest = QuestMongoClient.getQuestById(questId);
            StringBuilder pageText = new StringBuilder("##").append(quest.getSequence()).append(DIV);
            for (String line : quest.getDialogue()) {
                pageText.append(line).append(DIV);
            }

            MongoQuest.Objective objective = quest.getObjective();
            pageText.append(objective.getType().name().toLowerCase()).append("=")
                    .append(objective.getTarget()).append("=")
                    .append(objective.getRequiredCount()).append(DIV);

            MongoQuest.Reward reward = quest.getReward();
            pageText.append(reward.getItemName().toLowerCase()).append("=")
                    .append(reward.getCount()).append("=")
                    .append(reward.getXpValue()).append(";;");

            pages.add(new RawFilteredPair<>(pageText.toString(), Optional.of(pageText.toString())));
        }

        WritableBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITABLE_BOOK_CONTENT, WritableBookContentComponent.DEFAULT);
        WritableBookContentComponent t = bookContent.withPages(pages);

        bookStack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT, t);
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NPC Conversation Editor"));

        return bookStack;
    }

    public static List<MongoQuest> readQuestsFromBook(ItemStack bookStack) {
        if (bookStack.getItem() instanceof WrittenBookItem) {
            WrittenBookContentComponent signedBookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
            StringBuilder sb = new StringBuilder();
            signedBookContent.pages().forEach(pair -> sb.append(pair.get(false).getString()));
            List<String> questStrings = new ArrayList<>(List.of(sb.toString().split("##")));
            questStrings.removeIf(StringUtils::isBlank);
            return questStrings.stream().map(questString -> {
                LinkedList<String> allQuestData = new LinkedList<>(Arrays.asList(questString.split(DIV)));
                allQuestData.removeIf(s -> StringUtils.isBlank(s) || StringUtils.equals(s.trim(), "\n"));
                MongoQuest q = new MongoQuest();
                q.setId(Integer);
                q.setSequence(Integer.parseInt(allQuestData.pollFirst()));
                q.setReward(parseReward(allQuestData.pollLast()));
                q.setObjective(parseObjective(allQuestData.pollLast()));
                q.setDialogue(allQuestData);
                return q;
            }).collect(Collectors.toList());
        }

        log.error("ReadQuestsFromBook failed, item is not a WrittenBookItem: {}", bookStack);
        return new ArrayList<>(); // Not a book
    }

    private static MongoQuest.Reward parseReward(String rewardToken) {
        String[] split = rewardToken.split("=");
        if (split.length != 3) throw new IllegalStateException("Parsing reward split size != 3: " + rewardToken);
        return new MongoQuest.Reward(split[0], Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }

    private static MongoQuest.@NotNull Objective parseObjective(String objectiveToken) {
        String[] split = objectiveToken.split("=");
        if (split.length != 3) throw new IllegalStateException("Parsing objective split size != 3: " + objectiveToken);
        MongoQuest.Objective.Type type = MongoQuest.Objective.Type.valueOf(StringUtils.upperCase(split[0]));
        String target = StringUtils.lowerCase(split[1]).trim();

        return new MongoQuest.Objective(type, target, Integer.parseInt(split[2]));
    }

}
