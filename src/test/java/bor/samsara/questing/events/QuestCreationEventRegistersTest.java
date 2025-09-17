package bor.samsara.questing.events;

import bor.samsara.questing.book.QuestProgressBook;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestCreationEventRegistersTest {

    @Test
    void updateQuestLogWhenOpened_WithNonBookItem_ShouldPass() {
        // Arrange
        ItemStack nonBook = new ItemStack(Items.DIAMOND);
        var player = mock(PlayerEntity.class);
        when(player.getStackInHand(any())).thenReturn(nonBook);

        // Act
        ActionResult result = QuestCreationEventRegisters.updateQuestLogWhenOpened()
            .interact(player, mock(World.class), Hand.MAIN_HAND);

        // Assert
        assertEquals(ActionResult.PASS, result);
    }

    @Test
    void updateQuestLogWhenOpened_WithBookWithoutTags_ShouldPass() {
        // Arrange
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        var player = mock(PlayerEntity.class);
        when(player.getStackInHand(any())).thenReturn(book);

        // Act
        ActionResult result = QuestCreationEventRegisters.updateQuestLogWhenOpened()
            .interact(player, mock(World.class), Hand.MAIN_HAND);

        // Assert
        assertEquals(ActionResult.PASS, result);
    }

    @Test
    void updateQuestLogWhenOpened_WithValidQuestBook_ShouldUpdateWhenProgressChanged() {
        // Arrange
        String questUuid = "quest-123";
        String playerUuid = "player-456";
        int oldProgress = 1;
        int newProgress = 2;

        ItemStack book = createQuestBook(questUuid, playerUuid, oldProgress);
        var player = mock(PlayerEntity.class);
        when(player.getStackInHand(any())).thenReturn(book);

        MongoPlayer.QuestProgress questProgress = mock(MongoPlayer.QuestProgress.class);
        when(questProgress.getObjectiveCount()).thenReturn(newProgress);

        MongoPlayer mongoPlayer = mock(MongoPlayer.class);
        Map<String, MongoPlayer.QuestProgress> progressMap = new HashMap<>();
        progressMap.put(questUuid, questProgress);
        when(mongoPlayer.getQuestPlayerProgressMap()).thenReturn(progressMap);

        // Mock the static MongoPlayer retrieval
        try (MockedStatic<PlayerMongoClient> clientMock = mockStatic(PlayerMongoClient.class)) {
            clientMock.when(() -> PlayerMongoClient.getPlayerByUuid(playerUuid))
                     .thenReturn(mongoPlayer);

            // Act
            ActionResult result = QuestCreationEventRegisters.updateQuestLogWhenOpened()
                .interact(player, mock(World.class), Hand.MAIN_HAND);

            // Assert
            assertEquals(ActionResult.PASS, result);
            NbtComponent updatedData = book.get(DataComponentTypes.CUSTOM_DATA);
            int updatedProgress = updatedData.getNbt().getInt(QuestProgressBook.PLAYER_PROGRESS).orElse(-1);
            assertEquals(newProgress, updatedProgress);
        }
    }

    private ItemStack createQuestBook(String questUuid, String playerUuid, int progress) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(QuestProgressBook.QUEST_UUID, questUuid);
        nbt.putString(QuestProgressBook.PLAYER_UUID, playerUuid);
        nbt.putInt(QuestProgressBook.PLAYER_PROGRESS, progress);
        book.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, WrittenBookContentComponent.DEFAULT);
        return book;
    }
}
