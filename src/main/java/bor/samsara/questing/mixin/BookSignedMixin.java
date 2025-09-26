package bor.samsara.questing.mixin;

import bor.samsara.questing.book.QuestConfigBook;
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
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(ServerPlayNetworkHandler.class)
@Deprecated
public class BookSignedMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    static {
        log.info("Loading BookSignedMixin...");
    }

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
                if (null != bookStackCustomData.getNbt()) {
                    log.info("Signed custom book data: {}", bookStackCustomData.getNbt());

                    Optional<String> encodedNpcName = bookStackCustomData.getNbt().get(QuestConfigBook.NPC_NAME).asString();
                    if (encodedNpcName.isPresent()) {
                        try {
                            List<String> encodedQuestIds = getEncodedQuestUuids(bookStackCustomData);
                            MongoNpc npc = NpcMongoClient.getFirstNpcByName(encodedNpcName.get());
                            List<MongoQuest> quests = readQuestsFromBook(writtenBook, encodedQuestIds);
                            quests.forEach(QuestMongoClient::updateQuest);
                            npc.setQuestIds(quests.stream().map(MongoQuest::getUuid).toList());
                            NpcMongoClient.updateNpc(npc);
                            player.getInventory().removeOne(writtenBook);
                            player.sendMessage(Text.literal("Successfully updated NPC: " + encodedNpcName), false);
                            log.info("Updated {} encodedQuestIds {}", encodedNpcName, encodedQuestIds);
                        } catch (Exception e) {
                            player.sendMessage(Text.literal("[Samsara] Failed to update NPC from signed book: " + e), false);
                            ItemStack writableBook = convertWrittenBookToWritableBook(writtenBook, encodedNpcName.get());
                            player.getInventory().removeOne(writtenBook);
                            player.getInventory().insertStack(writableBook);
                            log.warn("Failed to update NPC from signed book: {}", e.getMessage(), e);
                        }
                    } else {
                        log.error("No encodedNpcName found on configuring quest book.");
                    }
                }
            }
        }
    }

    private static @NotNull List<String> getEncodedQuestUuids(NbtComponent bookStackCustomData) {
        List<String> encodedQuestIds = new ArrayList<>();
        if (null != bookStackCustomData.getNbt().get(QuestConfigBook.QUEST_IDS)) {
            encodedQuestIds = new ArrayList<>(Arrays.asList(bookStackCustomData.getNbt().get(QuestConfigBook.QUEST_IDS).asString().orElseThrow().split(",")));
        }
        encodedQuestIds.removeIf(StringUtils::isBlank);
        return encodedQuestIds;
    }

    /**
     * Ex1 = ##0;;Killer;;Go kill zombies;;kill=zombie=5;;minecraft:emerald=3=100;;##1;;Quest Complete;;Thanks!;;fin=na=0;;na=0=0;;
     *
     * <p>
     * Ex2   ##0;;Go Gather;;hey slaya!;;how are you today?;;collect=minecraft:rotten_flesh=5;;minecraft:emerald=3=100;;##1;;Quest Complete;;Thanks!;;fin=na=0;;na=0=0;;
     */
    private List<MongoQuest> readQuestsFromBook(ItemStack bookStack, List<String> questUuids) {
        if (bookStack.getItem() instanceof WrittenBookItem) {
            WrittenBookContentComponent signedBookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
            StringBuilder sb = new StringBuilder();
            signedBookContent.pages().forEach(pair -> sb.append(pair.get(false).getString()));
            List<String> questStrings = new ArrayList<>(List.of(sb.toString().split("##")));
            questStrings.removeIf(StringUtils::isBlank);
            return questStrings.stream().map(questString -> {
                LinkedList<String> allQuestData = new LinkedList<>(Arrays.asList(questString.split(QuestConfigBook.DIV)));
                allQuestData.removeIf(s -> StringUtils.isBlank(s) || StringUtils.equals(s.trim(), "\n"));
                MongoQuest q;
                int questSequence = Integer.parseInt(allQuestData.pollFirst());
                String questTitle = allQuestData.pollFirst();
                if (questSequence < questUuids.size()) {
                    q = new MongoQuest(questUuids.get(questSequence));
                } else {
                    q = new MongoQuest();
                    log.info("Adding new quest,'{}'  #{}", questTitle, q.getUuid());
                    QuestMongoClient.createQuest(q);
                }
                q.setSequence(questSequence);
                q.setTitle(questTitle);
                q.setReward(parseReward(allQuestData.pollLast()));
                //q.setObjective(parseObjective(allQuestData.pollLast()));
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


    private ItemStack convertWrittenBookToWritableBook(ItemStack writtenBook, String encodedNpcName) {
        ItemStack writable = new ItemStack(Items.WRITABLE_BOOK);

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
