package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestConfigBook {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);
    public static final String DIV = ";;";
    public static final String NPC_NAME = "npcName";
    public static final String QUEST_IDS = "questIds";

    public static int open(ServerCommandSource source, String name) {
        try {
            MongoNpc npc = NpcMongoClient.getFirstNpcByName(name); // load from DB
            ItemStack book = QuestConfigBook.createConversationBook(npc);
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
     * Ex2 = ##0;;Go Gather;;hey slaya!;;how are you today?;;collect=minecraft:rotten_flesh=5;;minecraft:emerald=3=100;;
     */
    private static ItemStack createConversationBook(MongoNpc npc) {
        ItemStack bookStack = new ItemStack(Items.WRITABLE_BOOK);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(NPC_NAME, npc.getName());
        nbtCompound.putString(QUEST_IDS, StringUtils.join(npc.getQuestIds(), ","));
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        List<RawFilteredPair<String>> pages = new ArrayList<>();
        for (String questId : npc.getQuestIds()) {
            // TODO complicated page-splitting if lines are too long.
            MongoQuest quest = QuestMongoClient.getQuestByUuid(questId);
            StringBuilder pageText = new StringBuilder("##").append(quest.getSequence()).append(DIV);
            pageText.append(quest.getTitle()).append(DIV);
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


}
