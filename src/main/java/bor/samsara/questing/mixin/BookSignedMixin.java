package bor.samsara.questing.mixin;

import bor.samsara.questing.entity.BookStateUtil;
import bor.samsara.questing.mongo.NpcMongoClientSingleton;
import bor.samsara.questing.mongo.models.MongoNpc;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(ServerPlayNetworkHandler.class)
public class BookSignedMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static NpcMongoClientSingleton mongo = NpcMongoClientSingleton.getInstance();

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
            ItemStack bookStack = player.getInventory().getStack(slotIndex);

            // TODO if statement not needed?
            if (bookStack.getItem() == Items.WRITTEN_BOOK) {

                NbtComponent bookStackCustomData = bookStack.get(DataComponentTypes.CUSTOM_DATA);
                log.info("written book custom data: {}", bookStackCustomData);

                // 1) Load the NPC from DB
                try {
                    String encodedNpcName = bookStackCustomData.getNbt().get("npcName").asString();
                    MongoNpc npc = mongo.getFirstNpcByName(encodedNpcName);

                    ItemStack mainHandItemStack = player.getInventory().getMainHandStack();
                    Map<Integer, List<String>> stageConversationMap = BookStateUtil.readStageConversationsFromBook(mainHandItemStack);
                    npc.setStageConversationMap(stageConversationMap);
                    mongo.updateNpc(npc);
                    log.info("Updated {} conversation map {}", encodedNpcName, stageConversationMap);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("[Samsara] Failed to update NPC from signed book: " + e), false);
                }

                // 5) If you want to remove the book from the player’s inventory:
                player.getInventory().removeOne(bookStack);

                // 6) Send feedback
                player.sendMessage(Text.literal("Successfully updated NPC: " + "TODO"), false);
            }
        }
    }
}
