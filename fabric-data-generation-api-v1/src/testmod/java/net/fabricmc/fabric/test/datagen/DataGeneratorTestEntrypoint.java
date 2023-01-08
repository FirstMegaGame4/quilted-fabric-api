/*
 * Copyright 2016, 2017, 2018, 2019 FabricMC
 * Copyright 2022-2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.test.datagen;

import static net.fabricmc.fabric.test.datagen.DataGeneratorTestContent.BLOCK_WITHOUT_ITEM;
import static net.fabricmc.fabric.test.datagen.DataGeneratorTestContent.BLOCK_WITHOUT_LOOT_TABLE;
import static net.fabricmc.fabric.test.datagen.DataGeneratorTestContent.MOD_ID;
import static net.fabricmc.fabric.test.datagen.DataGeneratorTestContent.SIMPLE_BLOCK;
import static net.fabricmc.fabric.test.datagen.DataGeneratorTestContent.SIMPLE_ITEM_GROUP;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.criterion.OnKilledCriterion;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.recipe.Ingredient;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.SimpleFabricLootTableProvider;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.DefaultResourceConditions;

public class DataGeneratorTestEntrypoint implements DataGeneratorEntrypoint {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataGeneratorTestEntrypoint.class);
	private static final ConditionJsonProvider NEVER_LOADED = DefaultResourceConditions.allModsLoaded("a");
	private static final ConditionJsonProvider ALWAYS_LOADED = DefaultResourceConditions.not(NEVER_LOADED);

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator dataGenerator) {
		dataGenerator.addProvider(TestRecipeProvider::new);
		dataGenerator.addProvider(TestConditionalRecipeProvider::new);
		dataGenerator.addProvider(TestModelProvider::new);
		dataGenerator.addProvider(TestAdvancementProvider::new);
		dataGenerator.addProvider(TestBlockLootTableProvider::new);
		dataGenerator.addProvider(TestBarterLootTableProvider::new);
		dataGenerator.addProvider(ExistingEnglishLangProvider::new);
		dataGenerator.addProvider(JapaneseLangProvider::new);

		TestBlockTagProvider blockTagProvider = dataGenerator.addProvider(TestBlockTagProvider::new);
		dataGenerator.addProvider(new TestItemTagProvider(dataGenerator, blockTagProvider));
		dataGenerator.addProvider(TestBiomeTagProvider::new);

		try {
			new FabricTagProvider<>(dataGenerator, BuiltinRegistries.BIOME) {
				@Override
				protected void generateTags() {
				}
			};
			throw new AssertionError("Using FabricTagProvider with built-in registry didn't throw an exception!");
		} catch (IllegalArgumentException e) {
			// no-op
		}

		try {
			new FabricTagProvider.DynamicRegistryTagProvider<>(dataGenerator, Registry.ITEM_KEY) {
				@Override
				protected void generateTags() {
				}
			};
			throw new AssertionError("Using DynamicRegistryTagProvider with static registry didn't throw an exception!");
		} catch (IllegalArgumentException e) {
			// no-op
		}
	}

	private static class TestRecipeProvider extends FabricRecipeProvider {
		private TestRecipeProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		protected void generateRecipes(Consumer<RecipeJsonProvider> exporter) {
			offerPlanksRecipe2(exporter, SIMPLE_BLOCK, ItemTags.ACACIA_LOGS);

			/* Generate test recipes using all types of custom ingredients for easy testing */
			// Testing procedure for vanilla and fabric clients:
			// - Create a new fabric server with the ingredient API.
			// - Copy the generated recipes to a datapack, for example to world/datapacks/<packname>/data/test/recipes/.
			// - Remember to also include a pack.mcmeta file in world/datapacks/<packname>.
			// (see https://minecraft.fandom.com/wiki/Tutorials/Creating_a_data_pack)
			// - Start the server and connect to it with a vanilla client.
			// - Test all the following recipes

			// Test partial NBT
			// 1 undamaged pickaxe + 8 pickaxes with any damage value to test shapeless matching logic.
			// Interesting test cases:
			// - 9 damaged pickaxes should not match.
			// - 9 undamaged pickaxes should match.
			// - 1 undamaged pickaxe + 8 damaged pickaxes should match (regardless of the position).
			// - 1 undamaged renamed pickaxe + 8 damaged pickaxes should match (NBT is not strictly matched here).
			ShapelessRecipeJsonBuilder.create(Items.DIAMOND_BLOCK)
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(DefaultCustomIngredients.nbt(new ItemStack(Items.DIAMOND_PICKAXE), false))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.input(Ingredient.ofItems(Items.DIAMOND_PICKAXE))
					.criterion("has_pickaxe", conditionsFromItem(Items.DIAMOND_PICKAXE))
					.offerTo(exporter);

			// Test strict NBT
			// To test: try renaming an apple to "Golden Apple" in creative with an anvil.
			// That should match the recipe and give a golden apple. Any other NBT should not match.
			ItemStack appleWithGoldenName = new ItemStack(Items.APPLE);
			appleWithGoldenName.setCustomName(Text.literal("Golden Apple"));
			appleWithGoldenName.setRepairCost(0);
			ShapelessRecipeJsonBuilder.create(Items.GOLDEN_APPLE)
					.input(DefaultCustomIngredients.nbt(appleWithGoldenName, true))
					.criterion("has_apple", conditionsFromItem(Items.APPLE))
					.offerTo(exporter);

			// Test AND
			// To test: charcoal should give a torch, but coal should not.
			ShapelessRecipeJsonBuilder.create(Items.TORCH)
					// charcoal only
					.input(DefaultCustomIngredients.all(Ingredient.fromTag(ItemTags.COALS), Ingredient.ofItems(Items.CHARCOAL)))
					.criterion("has_charcoal", conditionsFromItem(Items.CHARCOAL))
					.offerTo(exporter);

			// Test OR
			// To test: a golden pickaxe or a golden shovel should give a block of gold.
			ShapelessRecipeJsonBuilder.create(Items.GOLD_BLOCK)
					.input(DefaultCustomIngredients.any(Ingredient.ofItems(Items.GOLDEN_PICKAXE), Ingredient.ofItems(Items.GOLDEN_SHOVEL)))
					.criterion("has_pickaxe", conditionsFromItem(Items.GOLDEN_PICKAXE))
					.criterion("has_shovel", conditionsFromItem(Items.GOLDEN_SHOVEL))
					.offerTo(exporter);

			// Test difference
			// To test: only copper, netherite and emerald should match the recipe.
			ShapelessRecipeJsonBuilder.create(Items.BEACON)
					.input(DefaultCustomIngredients.difference(
							DefaultCustomIngredients.any(
									Ingredient.fromTag(ItemTags.BEACON_PAYMENT_ITEMS),
									Ingredient.ofItems(Items.COPPER_INGOT)),
							Ingredient.ofItems(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)))
					.criterion("has_payment", conditionsFromTag(ItemTags.BEACON_PAYMENT_ITEMS))
					.offerTo(exporter);
		}
	}

	private static class ExistingEnglishLangProvider extends FabricLanguageProvider {
		private ExistingEnglishLangProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		public void generateTranslations(TranslationBuilder translationBuilder) {
			translationBuilder.add(SIMPLE_BLOCK, "Simple Block");
			translationBuilder.add(new Identifier(MOD_ID, "identifier_test"), "Identifier Test");
			translationBuilder.add(EntityType.ALLAY, "Allay");
			translationBuilder.add(EntityAttributes.GENERIC_ARMOR, "Generic Armor");

			try {
				Optional<Path> path = dataGenerator.getModContainer().findPath("assets/testmod/lang/en_us.base.json");

				if (path.isPresent()) {
					translationBuilder.add(path.get());
				} else {
					throw new RuntimeException("The existing language file could not be found in the testmod assets!");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			try {
				translationBuilder.add(EntityType.ALLAY, "Allay Duplicate Test");
			} catch (RuntimeException e) {
				LOGGER.info("Duplicate test passed.");
			}
		}
	}

	private static class JapaneseLangProvider extends FabricLanguageProvider {
		private JapaneseLangProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator, "ja_jp");
		}

		@Override
		public void generateTranslations(TranslationBuilder translationBuilder) {
			translationBuilder.add(SIMPLE_BLOCK, "シンプルブロック");
			translationBuilder.add(SIMPLE_ITEM_GROUP, "データ生成項目");
			translationBuilder.add("this.is.a.test", "こんにちは");
		}
	}

	private static class TestConditionalRecipeProvider extends FabricRecipeProvider {
		private TestConditionalRecipeProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		protected void generateRecipes(Consumer<RecipeJsonProvider> exporter) {
			ShapelessRecipeJsonBuilder.create(Items.GOLD_INGOT).input(Items.DIRT).criterion("has_dirt", conditionsFromItem(Items.DIRT)).offerTo(withConditions(exporter, NEVER_LOADED));
			ShapelessRecipeJsonBuilder.create(Items.DIAMOND).input(Items.STICK).criterion("has_stick", conditionsFromItem(Items.STICK)).offerTo(withConditions(exporter, ALWAYS_LOADED));
		}
	}

	private static class TestModelProvider extends FabricModelProvider {
		private TestModelProvider(FabricDataGenerator generator) {
			super(generator);
		}

		@Override
		public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
			blockStateModelGenerator.registerSimpleCubeAll(SIMPLE_BLOCK);
			blockStateModelGenerator.registerSimpleCubeAll(BLOCK_WITHOUT_ITEM);
			blockStateModelGenerator.registerSimpleCubeAll(BLOCK_WITHOUT_LOOT_TABLE);
		}

		@Override
		public void generateItemModels(ItemModelGenerator itemModelGenerator) {
			//itemModelGenerator.register(item, Models.SLAB);
		}
	}

	private static class TestBlockTagProvider extends FabricTagProvider.BlockTagProvider {
		private TestBlockTagProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		protected void generateTags() {
			getOrCreateTagBuilder(BlockTags.FIRE).add(SIMPLE_BLOCK);
			getOrCreateTagBuilder(BlockTags.DIRT).setReplace(true).add(SIMPLE_BLOCK, BLOCK_WITHOUT_ITEM);
			getOrCreateTagBuilder(BlockTags.ACACIA_LOGS).forceAddTag(BlockTags.ANIMALS_SPAWNABLE_ON);
		}
	}

	private static class TestItemTagProvider extends FabricTagProvider.ItemTagProvider {
		private TestItemTagProvider(FabricDataGenerator dataGenerator, BlockTagProvider blockTagProvider) {
			super(dataGenerator, blockTagProvider);
		}

		@Override
		protected void generateTags() {
			copy(BlockTags.DIRT, ItemTags.DIRT);
		}
	}

	private static class TestBiomeTagProvider extends FabricTagProvider.DynamicRegistryTagProvider<Biome> {
		private TestBiomeTagProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator, Registry.BIOME_KEY);
		}

		@Override
		protected void generateTags() {
			FabricTagBuilder<Biome> builder = getOrCreateTagBuilder(TagKey.of(Registry.BIOME_KEY, new Identifier(MOD_ID, "biome_tag_test")))
					.add(BiomeKeys.BADLANDS, BiomeKeys.BAMBOO_JUNGLE)
					.add(BiomeKeys.BASALT_DELTAS);

			try {
				builder.add(BuiltinRegistries.BIOME.get(BiomeKeys.PLAINS));
				throw new AssertionError("Adding built-in entry to dynamic registry tag builder didn't throw an exception!");
			} catch (UnsupportedOperationException e) {
				// no-op
			}
		}
	}

	private static class TestAdvancementProvider extends FabricAdvancementProvider {
		private TestAdvancementProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		public void generateAdvancement(Consumer<Advancement> consumer) {
			Advancement root = Advancement.Builder.create()
					.display(
							SIMPLE_BLOCK,
							Text.translatable("advancements.test.root.title"),
							Text.translatable("advancements.test.root.description"),
							new Identifier("textures/gui/advancements/backgrounds/end.png"),
							AdvancementFrame.TASK,
							false, false, false)
					.criterion("killed_something", OnKilledCriterion.Conditions.createPlayerKilledEntity())
					.build(consumer, MOD_ID + ":test/root");
			Advancement rootNotLoaded = Advancement.Builder.create()
					.display(
							SIMPLE_BLOCK,
							Text.translatable("advancements.test.root_not_loaded.title"),
							Text.translatable("advancements.test.root_not_loaded.description"),
							new Identifier("textures/gui/advancements/backgrounds/end.png"),
							AdvancementFrame.TASK,
							false, false, false)
					.criterion("killed_something", OnKilledCriterion.Conditions.createPlayerKilledEntity())
					.build(withConditions(consumer, NEVER_LOADED), MOD_ID + ":test/root_not_loaded");
		}
	}

	private static class TestBlockLootTableProvider extends FabricBlockLootTableProvider {
		private TestBlockLootTableProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator);
		}

		@Override
		protected void generateBlockLootTables() {
			addDrop(SIMPLE_BLOCK);
			addDrop(BLOCK_WITHOUT_ITEM, drops(SIMPLE_BLOCK));

			excludeFromStrictValidation(BLOCK_WITHOUT_LOOT_TABLE);
		}
	}

	private static class TestBarterLootTableProvider extends SimpleFabricLootTableProvider {
		private TestBarterLootTableProvider(FabricDataGenerator dataGenerator) {
			super(dataGenerator, LootContextTypes.BARTER);
		}

		@Override
		public void accept(BiConsumer<Identifier, LootTable.Builder> consumer) {
			withConditions(consumer, ALWAYS_LOADED).accept(
					LootTables.PIGLIN_BARTERING_GAMEPLAY,
					LootTable.builder().pool(
							LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0F)).with(ItemEntry.builder(SIMPLE_BLOCK))
					)
			);
		}
	}
}
