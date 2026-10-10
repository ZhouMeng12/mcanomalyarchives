package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** 严格解码；不使用会把未知属性/值静默降级为默认值的宽松 NbtUtils reader。 */
final class StrangeTreeBlockCodec {
	record Description(ResourceLocation id, Map<String, String> properties) { }

	private StrangeTreeBlockCodec() { }

	static Description parse(String text) {
		CompoundTag tag;
		try {
			tag = TagParser.parseTag(text);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
			throw new IllegalArgumentException("Invalid tree block SNBT", failure);
		}
		if (!tag.contains("Name", Tag.TAG_STRING)) throw new IllegalArgumentException("Missing tree block Name");
		ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
		if (id == null || !(id.toString().equals("mcanomalyarchives:strangetreelog")
				|| id.toString().equals("mcanomalyarchives:strangetreeleaves"))) {
			throw new IllegalArgumentException("Unknown or non-tree block in journal: " + tag.getString("Name"));
		}
		for (String key : tag.getAllKeys()) {
			if (!key.equals("Name") && !key.equals("Properties")) {
				throw new IllegalArgumentException("Unexpected tree block field: " + key);
			}
		}
		Map<String, String> properties = new LinkedHashMap<>();
		if (tag.contains("Properties")) {
			if (!tag.contains("Properties", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid tree block Properties");
			CompoundTag stored = tag.getCompound("Properties");
			for (String key : stored.getAllKeys()) {
				if (!stored.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Invalid property type: " + key);
				properties.put(key, stored.getString(key));
			}
		}
		return new Description(id, Map.copyOf(properties));
	}

	static BlockState decode(Description description, BlockState base) {
		validateProperties(description, base.getBlock().getStateDefinition()::getProperty);
		BlockState state = base;
		for (Map.Entry<String, String> value : description.properties.entrySet()) {
			Property<?> property = base.getBlock().getStateDefinition().getProperty(value.getKey());
			if (property == null) throw new IllegalArgumentException("Unknown tree block property: " + value.getKey());
			state = apply(state, property, value.getValue());
		}
		return state;
	}

	static void validateProperties(Description description, Function<String, Property<?>> definition) {
		for (Map.Entry<String, String> value : description.properties.entrySet()) {
			Property<?> property = definition.apply(value.getKey());
			if (property == null) throw new IllegalArgumentException("Unknown tree block property: " + value.getKey());
			if (property.getValue(value.getValue()).isEmpty()) {
				throw new IllegalArgumentException("Invalid tree block property value: " + value.getKey() + "=" + value.getValue());
			}
		}
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String text) {
		T value = property.getValue(text).orElseThrow(() ->
				new IllegalArgumentException("Invalid tree block property value: " + property.getName() + "=" + text));
		return state.setValue(property, value);
	}
}
