package bor.samsara.questing.book;

import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class QuestLogBook {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final List<RawFilteredPair<Text>> INTRO_PAGES = buildIntroPages();

    public static final String PLAYER_UUID = "playerUuid";
    public static final String PLAYER_STATE = "playerQuestState";

    public static int open(ServerCommandSource source, String targetPlayerName) {
        try {
            MongoPlayer mongoPlayer = PlayerMongoClient.getPlayerByName(targetPlayerName);
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ItemStack book = createLogBook(player, mongoPlayer);

            if (player.getInventory().insertStack(book)) {
                log.debug("Giving {} quest log book.", player.getName().getString());
            } else {
                // If inventory is full, drop it on the ground
                player.sendMessage(Text.literal("You dropped your quest book! You should pick that up."), false);
                player.dropItem(book, true);
            }
        } catch (Exception e) {
            log.error("Failed to create quest log: {}", e.getMessage(), e);
        }
        return 1;
    }

    private static ItemStack createLogBook(ServerPlayerEntity serverPlayerEntity, MongoPlayer player) {
        ItemStack bookStack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(PLAYER_UUID, player.getUuid());
        nbtCompound.putInt(PLAYER_STATE, player.getQuestPlayerProgressMap().hashCode());
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        WrittenBookContentComponent bookContent = bookStack.getOrDefault(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent.withPages(getWrittenBookContentComponent(serverPlayerEntity, player, bookStack)));
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(player.getName() + "'s Quest Log")
                .styled(style -> style.withColor(Formatting.GOLD).withBold(true)));

        return bookStack;
    }

    public static @NotNull List<RawFilteredPair<Text>> getWrittenBookContentComponent(ServerPlayerEntity serverPlayerEntity, MongoPlayer player, ItemStack bookStack) {
        WrittenBookPageBuilder bookBuilder = new WrittenBookPageBuilder(buildIntroPages());
        bookBuilder.append(Text.literal(" ፠ Active Quests ፠").styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(false))).newLine();
        bookBuilder.append(Text.literal("〰〰〰〰〰〰〰〰〰〰〰〰").styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(false))).newLine();
        //bookBuilder.append(Text.literal("Active Quests").styled(style -> style.withColor(Formatting.DARK_GRAY).withItalic(true))).newLine();

        for (MongoPlayer.QuestProgress activeQuest : player.getQuestPlayerProgressMap().values().stream().filter(q -> !q.isComplete()).toList()) {
            bookBuilder
                    .append(Text.literal(" ⋙ ").styled(style -> style.withColor(Formatting.BLACK).withBold(true)))
                    .append(Text.literal(activeQuest.getQuestTitle())
                            .styled(style -> style.withColor(Formatting.DARK_AQUA)
                                    .withClickEvent(new ClickEvent.RunCommand("/questLog click %s %s".formatted(player.getName(), activeQuest.getQuestUuid())))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to view quest: ").styled(hoverStyle -> hoverStyle.withColor(Formatting.YELLOW))
                                            .append(Text.literal(activeQuest.getQuestTitle()).styled(hoverStyle -> hoverStyle.withColor(Formatting.AQUA)))
                                    ))))
                    .newLine();
        }

        return bookBuilder.build();
    }

    private static List<RawFilteredPair<Text>> buildIntroPages() {
        WrittenBookPageBuilder bookBuilder = new WrittenBookPageBuilder();

        UnaryOperator<Style> borderStyle = style -> style.withColor(Formatting.BLACK);
        bookBuilder.simpleAppend(Text.literal("╔══════════╗").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal(" ₪₪₪₪₪₪₪₪₪₪  ").styled(style -> style.withColor(Formatting.GOLD).withBold(false)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal("  ☯ Samsara ☯    ").styled(style -> style.withColor(Formatting.GOLD).withShadowColor(14073961).withBold(false)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal(" ₪₪₪₪₪₪₪₪₪₪  ").styled(style -> style.withColor(Formatting.GOLD).withBold(false)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║                       ║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal("   MC like you've   ").styled(style -> style.withColor(Formatting.DARK_AQUA).withItalic(true)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal("    never seen     ").styled(style -> style.withColor(Formatting.DARK_AQUA).withItalic(true)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║                       ║").styled(borderStyle)).newLine();
        bookBuilder.simpleAppend(Text.literal("║                       ║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal("`/questLog` if lost").styled(style -> style.withColor(Formatting.GRAY)));
        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║                       ║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║").styled(borderStyle));
        bookBuilder.simpleAppend(Text.literal(" ☁ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("Web").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.samsara.gg")))));
        bookBuilder.simpleAppend(Text.literal(" ✌ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("IG").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.instagram.com/samsara_mc/")))));
        bookBuilder.simpleAppend(Text.literal(" ⚅ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("YT").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.youtube.com/@boroxify")))));
        bookBuilder.simpleAppend(Text.literal(" ║").styled(borderStyle)).newLine();

        bookBuilder.simpleAppend(Text.literal("║                       ║").styled(borderStyle)).newLine();
        bookBuilder.simpleAppend(Text.literal("╚══════════╝").styled(borderStyle)).newLine();


        return bookBuilder.build();
    }

    /*
    https://www.reddit.com/r/Minecraft/comments/h9f3fz/the_character_limit_for_books_is_killing_me/


    You can actually insert images into written books using bitmap support from a custom font of a resource pack! Just replace a private use unicode
    character with a bitmap image (limited to 256x256) through the json file then paste that unicode character into a book and bam, full color images
    in game. Making a server resource pack is easy enough to ensure everybody can also view it. You could get a higher resolution image by stringing
    together more characters but that's a little more complicated.
     */


}
