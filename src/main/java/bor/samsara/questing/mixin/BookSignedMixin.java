package bor.samsara.questing.mixin;

import bor.samsara.questing.entity.BookStateUtil;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(ServerPlayNetworkHandler.class)
public class BookSignedMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);


    /**
     * We inject after vanilla finishes processing BookUpdateC2SPacket,
     * so we can see if a book was just signed.
     */
    @Inject(method = "onBookUpdate", at = @At("TAIL"))
    private void afterBookSigned(BookUpdateC2SPacket packet, CallbackInfo ci) {
        // `this` is the ServerPlayNetworkHandler, so we cast to access 'player'
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        ServerPlayerEntity player = handler.player;
        log.info("afterBookSigned: {}", packet);

        if (packet.title().isPresent()) {
            int slotIndex = packet.slot();
            ItemStack writtenBook = player.getInventory().getStack(slotIndex);

            if (writtenBook.getItem() == Items.WRITTEN_BOOK) {

                NbtComponent bookStackCustomData = writtenBook.get(DataComponentTypes.CUSTOM_DATA);
                log.info("written book custom data: {}", bookStackCustomData);

                // 1) Load the NPC from DB
                try {
                    String encodedNpcName = bookStackCustomData.getNbt().get("npcName").asString();
                    MongoNpc npc = NpcMongoClient.getFirstNpcByName(encodedNpcName);

                    ItemStack mainHandItemStack = player.getInventory().getMainHandStack();
                    Map<Integer, MongoNpc.Quest> questMap = BookStateUtil.readQuestsFromBook(mainHandItemStack);
                    npc.setQuests(questMap);
                    NpcMongoClient.updateNpc(npc);
                    player.getInventory().removeOne(writtenBook);
                    player.sendMessage(Text.literal("Successfully updated NPC: " + "TODO"), false);
                    log.info("Updated {} conversation map {}", encodedNpcName, questMap);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("[Samsara] Failed to update NPC from signed book: " + e), false);
                    ItemStack writableBook = convertWrittenBookToWritableBook(writtenBook);
                    player.getInventory().removeOne(writtenBook);
                    player.getInventory().insertStack(writableBook);
                    log.info("Failed to update NPC from signed book: {}", e.getMessage());
                }
            }
        }
    }

    private ItemStack convertWrittenBookToWritableBook(ItemStack writtenBook) {
        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK);

        NbtComponent bookStackCustomData = writtenBook.get(DataComponentTypes.CUSTOM_DATA);
        String encodedNpcName = bookStackCustomData.getNbt().get("npcName").asString();

        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString("npcName", encodedNpcName);
        writable.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        WrittenBookContentComponent signedBookContent = writtenBook.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        List<RawFilteredPair<String>> pages = signedBookContent.pages().stream().map(textPair -> new RawFilteredPair<>(textPair.get(false).getString(), Optional.of(textPair.get(false).getString()))).toList();

        WritableBookContentComponent bookContent = writable.getOrDefault(DataComponentTypes.WRITABLE_BOOK_CONTENT, WritableBookContentComponent.DEFAULT);

        writable.set(DataComponentTypes.WRITABLE_BOOK_CONTENT, bookContent.withPages(pages));
        writable.set(DataComponentTypes.CUSTOM_NAME, Text.literal("NPC " + encodedNpcName + " Conversation Editor"));

        return writable;
    }
}
