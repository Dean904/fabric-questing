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
            ItemStack book = createLogBook(mongoPlayer);

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

    private static ItemStack createLogBook(MongoPlayer player) {
        ItemStack bookStack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString(PLAYER_UUID, player.getUuid());
        nbtCompound.putInt(PLAYER_STATE, player.getActiveQuestProgressionMap().hashCode());
        bookStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));

        bookStack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT.withPages(getWrittenBookContentComponent(player)));
        bookStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(player.getName() + "'s Quest Log")
                .styled(style -> style.withColor(Formatting.GOLD).withBold(true)));

        return bookStack;
    }

    public static @NotNull List<RawFilteredPair<Text>> getWrittenBookContentComponent(MongoPlayer player) {
        WrittenBookPageBuilder bookBuilder = new WrittenBookPageBuilder(INTRO_PAGES);
        bookBuilder.append(Text.literal(" ፠ Active Quests ፠").styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(false))).newLine();
        bookBuilder.append(Text.literal("〰〰〰〰〰〰〰〰〰〰〰〰").styled(style -> style.withColor(Formatting.GOLD).withBold(true).withUnderline(false))).newLine();

        List<MongoPlayer.ActiveQuestState> displayedQuests = player.getActiveQuestProgressionMap().values().stream().filter(MongoPlayer.ActiveQuestState::rendersInQuestLog).toList();
        for (MongoPlayer.ActiveQuestState activeQuest : displayedQuests) {
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
        List<Style> rainbowStyles = List.of(
                Style.EMPTY.withColor(0xCDB608),
                Style.EMPTY.withColor(0x9CCE0F),
                Style.EMPTY.withColor(0x6AE717),
                Style.EMPTY.withColor(0x50E159),
                Style.EMPTY.withColor(0x37DA9B),
                Style.EMPTY.withColor(0x1DD4DD),
                Style.EMPTY.withColor(0x4A99D6),
                Style.EMPTY.withColor(0x775DCF),
                Style.EMPTY.withColor(0xA422C8),
                Style.EMPTY.withColor(0xBB2493)
        );

        UnaryOperator<Style> borderStyle = style -> style.withColor(Formatting.GOLD).withObfuscated(false);
        bookBuilder.simpleAppend(Text.literal("╔══════════╗").styled(borderStyle)).newLine();
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("      /questLog").styled(style -> style.withColor(Formatting.GRAY)));
        bookBuilder.newLine();
        bookBuilder.newLine();

        appendStyledText(bookBuilder, "   ₪₪₪₪₪₪₪₪₪₪  ", rainbowStyles, false);
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("    ☯ ").styled(s -> s.withColor(0xd700fd))); // Red for first glyph
        bookBuilder.simpleAppend(Text.literal("S").styled(s -> s.withColor(0x5bdb80).withBold(false)))
                .simpleAppend(Text.literal("a").styled(s -> s.withColor(0x6fb695).withBold(false)))
                .simpleAppend(Text.literal("m").styled(s -> s.withColor(0x8492aa).withBold(false)))
                .simpleAppend(Text.literal("s").styled(s -> s.withColor(0x996dbe).withBold(false)))
                .simpleAppend(Text.literal("a").styled(s -> s.withColor(0xae49d3).withBold(false)))
                .simpleAppend(Text.literal("r").styled(s -> s.withColor(0xc224e8).withBold(false)))
                .simpleAppend(Text.literal("a").styled(s -> s.withColor(0xd700fd).withBold(false)));
        bookBuilder.simpleAppend(Text.literal(" ☯").styled(s -> s.withColor(0x5bdb80)));
        bookBuilder.newLine();

        appendStyledText(bookBuilder, "   ₪₪₪₪₪₪₪₪₪₪  ", rainbowStyles.reversed(), false);
        bookBuilder.newLine();
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("     MC like you've   ").styled(style -> style.withColor(Formatting.GOLD).withItalic(true)));
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("      never seen     ").styled(style -> style.withColor(Formatting.GOLD).withItalic(true)));
        bookBuilder.newLine();
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("  ☁ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("Web").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.samsara.gg")))));
        bookBuilder.simpleAppend(Text.literal(" ✌ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("IG").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.instagram.com/samsara_mc/")))));
        bookBuilder.simpleAppend(Text.literal(" ⚅ ").styled(style -> style.withColor(Formatting.GOLD)));
        bookBuilder.simpleAppend(Text.literal("YT").styled(style -> style.withColor(3368652).withItalic(true).withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://www.youtube.com/@boroxify")))));
        bookBuilder.newLine();
        bookBuilder.newLine();

        bookBuilder.simpleAppend(Text.literal("╚══════════╝").styled(borderStyle)).newLine();

        return bookBuilder.build();
    }

    private static void appendStyledText(WrittenBookPageBuilder bookBuilder, String text, List<Style> rainbowStyles, boolean obfuscated) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Style style = rainbowStyles.get(i % rainbowStyles.size()); // loop if text is longer
            bookBuilder.simpleAppend(Text.literal(String.valueOf(c)).styled(s -> style.withObfuscated(obfuscated)));
        }
    }

    /*
    https://www.reddit.com/r/Minecraft/comments/h9f3fz/the_character_limit_for_books_is_killing_me/


    You can actually insert images into written books using bitmap support from a custom font of a resource pack! Just replace a private use unicode
    character with a bitmap image (limited to 256x256) through the json file then paste that unicode character into a book and bam, full color images
    in game. Making a server resource pack is easy enough to ensure everybody can also view it. You could get a higher resolution image by stringing
    together more characters but that's a little more complicated.
     */


}
