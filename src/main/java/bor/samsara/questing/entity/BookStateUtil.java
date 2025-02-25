package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class BookStateUtil {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

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
     */
    private static ItemStack createConversationBook(MongoNpc npc) {
        ItemStack bookStack = new ItemStack(Items.WRITABLE_BOOK);

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString("npcName", npc.getName());
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        List<RawFilteredPair<String>> pages = new ArrayList<>();
        for (MongoNpc.Quest quest : npc.getQuests().values()) {
            List<String> lines = quest.getDialogue();
            // Combine all lines for this stage into one page
            // You could do more complicated page-splitting if lines are too long.
            StringBuilder pageText = new StringBuilder("Quest ").append(quest.getSequence()).append(":\n");
            for (String line : lines) {
                pageText.append(line).append("\n");
                pages.add(new RawFilteredPair<>(line, Optional.of(line)));
            }
        }

        WritableBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITABLE_BOOK_CONTENT, WritableBookContentComponent.DEFAULT);
        WritableBookContentComponent t = bookContent.withPages(pages);

        bookStack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT, t);
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NPC Conversation Editor"));

        return bookStack;
    }

    public static int close(ServerCommandSource source, String name) {
        try {
            MongoNpc npc = NpcMongoClient.getFirstNpcByName(name); // load from DB
            ItemStack mainHandItemStack = source.getPlayer().getInventory().getMainHandStack();
            Map<Integer, MongoNpc.Quest> quests = readQuestsFromBook(mainHandItemStack);
            npc.setQuests(quests);

            NpcMongoClient.updateNpc(npc);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }
        return 1;
    }

    /**
     * example book config = "##0;;hello;;world;;"
     */
    public static Map<Integer, MongoNpc.Quest> readQuestsFromBook(ItemStack bookStack) {
        // "pages" is an NBTList of JSON strings
        if (bookStack.getItem() instanceof WrittenBookItem) {
            WrittenBookContentComponent signedBookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
            StringBuilder sb = new StringBuilder();
            signedBookContent.pages().forEach(pair -> sb.append(pair.get(false).getString()));
            List<String> questStrings = new ArrayList<>(List.of(sb.toString().split("##")));
            questStrings.removeIf(StringUtils::isEmpty);
            return questStrings.stream().map(s -> {
                List<String> allQuestData = new ArrayList<>(Arrays.asList(s.split(";;")));
                MongoNpc.Quest q = new MongoNpc.Quest();
                q.setSequence(Integer.parseInt(allQuestData.getFirst()));
                allQuestData.removeFirst();
                q.setDialogue(allQuestData);
                return q;
            }).collect(Collectors.toMap(MongoNpc.Quest::getSequence, o -> o));
        }

        log.error("ReadQuestsFromBook failed, item is not a WrittenBookItem: {}", bookStack);
        return new HashMap<>(); // Not a book
    }

}
