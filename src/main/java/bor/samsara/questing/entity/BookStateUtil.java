package bor.samsara.questing.entity;

import bor.samsara.questing.mongo.NpcMongoClientSingleton;
import bor.samsara.questing.mongo.models.MongoNpc;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WritableBookItem;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.*;

public class BookStateUtil {

    private static NpcMongoClientSingleton mongo = NpcMongoClientSingleton.getInstance();

    public static int open(ServerCommandSource source, String name) {
        try {
            MongoNpc npc = mongo.getFirstNpcByName(name); // load from DB
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
        for (Map.Entry<Integer, List<String>> entry : npc.getStageConversationMap().entrySet()) {
            int stageId = entry.getKey();
            List<String> lines = entry.getValue();
            // Combine all lines for this stage into one page
            // You could do more complicated page-splitting if lines are too long.
            StringBuilder pageText = new StringBuilder("Stage ").append(stageId).append(":\n");
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
            MongoNpc npc = mongo.getFirstNpcByName(name); // load from DB
            ItemStack mainHandItemStack = source.getPlayer().getInventory().getMainHandStack();
            Map<Integer, List<String>> stageConversationMap = readStageConversationsFromBook(mainHandItemStack);
            npc.setStageConversationMap(stageConversationMap);

            mongo.updateNpc(npc);
        } catch (Exception e) {
            source.sendError(Text.literal("Failed: " + e));
        }
        return 1;
    }

    public static Map<Integer, List<String>> readStageConversationsFromBook(ItemStack bookStack) {
        // "pages" is an NBTList of JSON strings
        if (bookStack.getItem() instanceof WrittenBookItem) {
            WrittenBookContentComponent signedBookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
            List<String> signedBookString = signedBookContent.pages().stream().map(pair -> (pair.get(false).getString())).toList();
            return Map.of(0, signedBookString);
        }
        if (bookStack.getItem() instanceof WritableBookItem) {
            WritableBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITABLE_BOOK_CONTENT, WritableBookContentComponent.DEFAULT);
            List<String> bookString = bookContent.pages().stream().map(pair -> pair.get(false)).toList();
            return Map.of(0, bookString);
        }

        return new HashMap<>(); // Not a book
    }

}
