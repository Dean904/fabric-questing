package bor.samsara.questing.events;

import bor.samsara.questing.hearth.HearthStoneEventRegisters;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class ItemStackFactory {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private ItemStackFactory() {}

    //   /give @p bundle[minecraft:bundle_contents=[
    //      {id:"minecraft:diamond_sword",count:1},
    //      {id:"minecraft:golden_apple",count:2},
    //      {id:"minecraft:torch",count:16}
    //    ]]
    //
    //   /give @p compass[minecraft:lodestone_tracker={target:{pos:[-711,155,529],dimension:"minecraft:overworld"}}]
    //
    //   /give @p filled_map[minecraft:map_id=1]
    //
    //  /give @p diamond_sword[minecraft:enchantments=
    //     {"minecraft:sweeping_edge":3,
    //     "minecraft:sharpness":3,
    //     "minecraft:looting":2}
    //    ]

    public static @NotNull ItemStack getRewardItemStack(MongoQuest.Reward reward, World world) {
        String itemDefinition = reward.getItemName();
        int count = reward.getCount();
        return constructDecoratedItemStack(itemDefinition, count, world);
    }

    private static @NotNull ItemStack constructDecoratedItemStack(String itemDefinition, int count, World world) {
        String itemName = extractItemName(itemDefinition);
        Identifier itemId = getTranslatedId(itemName);
        String componentString = extractComponentConfigs(itemDefinition);
        ItemStack reward = switch (itemName) {
            case "minecraft:compass" -> createLodestone(componentString, itemId);
            case "minecraft:filled_map" -> createMap(componentString, itemId);
            case "minecraft:bundle" -> createBundle(componentString, itemId, world);
            case "hearthstone" -> createHearthstone(componentString);
            default -> new ItemStack(Registries.ITEM.get(itemId), count);
        };

        if (itemDefinition.contains("enchants=")) {
            String enchantmentString = itemDefinition.substring(itemDefinition.indexOf("[") + 1, itemDefinition.indexOf("]")).replace("enchants=", "");
            String[] enchantmentDefinitions = enchantmentString.split(";");
            for (String enchant : enchantmentDefinitions) {
                String[] pair = enchant.split(":");
                String enchantmentId = pair[0];
                int level = Integer.parseInt(pair[1]);
                RegistryWrapper<Enchantment> enchantRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                RegistryKey<Enchantment> enchantKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla(enchantmentId));
                RegistryEntry<Enchantment> enchantEntry = enchantRegistry.getOrThrow(enchantKey);
                reward.addEnchantment(enchantEntry, level);
            }
        }

        return reward;
    }

    private static @NotNull String extractComponentConfigs(String itemDefinition) {
        if (!itemDefinition.contains("{")) {
            return "";
        }
        return itemDefinition.substring(itemDefinition.indexOf('{') + 1, itemDefinition.indexOf('}'));
    }

    private static String extractItemName(String itemDefinition) {
        if (itemDefinition.contains("{")) {
            return itemDefinition.substring(0, itemDefinition.indexOf('{'));
        }
        if (itemDefinition.contains("[")) {
            return itemDefinition.substring(0, itemDefinition.indexOf('['));
        }
        return itemDefinition;
    }

    private static @NotNull Identifier getTranslatedId(String itemName) {
        if (StringUtils.equalsIgnoreCase("Hearthstone", itemName)) {
            return Identifier.ofVanilla("carrot_on_a_stick");
        }
        return Identifier.of(itemName);
    }

    // hearthstone{spawnhenge,warp_x,warp_y,warp_z,dimension}
    private static ItemStack createHearthstone(String componentString) {
        String[] components = componentString.split(",");
        String name = components[0];
        BlockPos pos = new BlockPos(Integer.parseInt(components[1]), Integer.parseInt(components[2]), Integer.parseInt(components[3]));
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla(components[4])); // e.g., "overworld", "the_nether",
        return HearthStoneEventRegisters.createHearthstoneItem(name, GlobalPos.create(dimension, pos));
    }

    // minecraft:compass{-1883,319,1455,overworld}
    private static ItemStack createLodestone(String componentString, Identifier translatedId) {
        String[] components = componentString.split(",");
        BlockPos pos = new BlockPos(Integer.parseInt(components[0]), Integer.parseInt(components[1]), Integer.parseInt(components[2]));
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla(components[3])); // e.g., "overworld", "the_nether", "the_end"
        GlobalPos globalPos = GlobalPos.create(dimension, pos);
        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.of(globalPos), false);

        ItemStack lodestone = new ItemStack(Registries.ITEM.get(translatedId), 1);
        lodestone.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        return lodestone;
    }

    // minecraft:bundle{minecraft:copper_sword,1,minecraft:golden_apple,2,minecraft:torch,16}
    private static ItemStack createBundle(String componentString, Identifier translatedId, World world) {
        String[] contents = componentString.split(",");
        List<ItemStack> bundleItems = new ArrayList<>();
        for (int i = 0; i < contents.length; i += 2) {
            bundleItems.add(constructDecoratedItemStack(contents[i], Integer.parseInt(contents[i + 1]), world));
        }
        ItemStack bundle = new ItemStack(Registries.ITEM.get(translatedId), 1);
        bundle.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(bundleItems));
        return bundle;
    }

    // minecraft:filled_map{1}
    private static ItemStack createMap(String componentString, Identifier translatedId) {
        ItemStack filledMap = new ItemStack(Registries.ITEM.get(translatedId), 1);
        filledMap.set(DataComponentTypes.MAP_ID, new MapIdComponent(Integer.parseInt(componentString)));
        return filledMap;
    }

}
