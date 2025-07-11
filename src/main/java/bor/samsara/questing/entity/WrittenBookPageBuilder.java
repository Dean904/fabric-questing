package bor.samsara.questing.entity;

import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WrittenBookPageBuilder {

    public static final int PAGE_LINE_COUNT = 14;
    private final List<RawFilteredPair<Text>> bookPages = new ArrayList<>();
    private MutableText pageText = Text.empty();
    private int currentLineCount = 1;

    public WrittenBookPageBuilder append(Text text) {
        String remaining = text.getString();
        Style style = text.getStyle();

        while (StringUtils.isNotBlank(remaining)) {
            String tempLine = remaining;

            int newLineCount = 0;
            if (tempLine.length() > 20) {
                newLineCount = remaining.length() / 20;
            }

            if (currentLineCount + newLineCount >= PAGE_LINE_COUNT) {
                int charsLeftOnPage = (PAGE_LINE_COUNT * 20) - (currentLineCount * 20);
                int take = Math.min(charsLeftOnPage, remaining.length()); // TODO is this necessary?
                pageText.append(text.asTruncatedString(take)); // Truncate the text to fit on the current page
                bookPages.add(new RawFilteredPair<>(pageText, Optional.of(pageText)));

                // Start a brand‐new page
                pageText = Text.empty();
                currentLineCount = 0;
                remaining = remaining.substring(take);
                text = Text.literal(remaining).styled(s -> style);
            } else {
                // Everything fits onto the current page in one shot:
                pageText.append(text);
                currentLineCount += newLineCount;
                remaining = "";
            }
        }

        return this;
    }

    public WrittenBookPageBuilder newLine() {
        pageText.append("\n");
        currentLineCount++;

        if (currentLineCount >= PAGE_LINE_COUNT) {
            bookPages.add(new RawFilteredPair<>(pageText, Optional.of(pageText)));
            pageText = Text.empty();
            currentLineCount = 0;
        }

        return this;
    }

    public List<RawFilteredPair<Text>> build() {
        if (!pageText.getString().isEmpty()) {
            bookPages.add(new RawFilteredPair<>(pageText, Optional.of(pageText)));
        }
        return bookPages;
    }

}
