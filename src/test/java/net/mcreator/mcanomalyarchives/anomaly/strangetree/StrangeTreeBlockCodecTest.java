package net.mcreator.mcanomalyarchives.anomaly.strangetree;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.Test;

class StrangeTreeBlockCodecTest {
	@Test
	void allowsTheActualDefaultTreeBlockDescriptions() {
		assertTrue(StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreelog\"}").properties().isEmpty());
		assertTrue(StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreeleaves\"}").properties().isEmpty());
	}

	@Test
	void rejectsUnknownPropertiesEvenWhenSnbtIsOtherwiseValid() {
		var description = StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreelog\",Properties:{axis:\"y\"}}");
		assertThrows(IllegalArgumentException.class, () -> StrangeTreeBlockCodec.validateProperties(description, name -> null));
	}

	@Test
	void checksActualPropertyAllowedValuesRatherThanFallingBackToDefault() {
		BooleanProperty persistent = BooleanProperty.create("persistent");
		var invalid = StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreeleaves\",Properties:{persistent:\"maybe\"}}");
		assertThrows(IllegalArgumentException.class, () ->
				StrangeTreeBlockCodec.validateProperties(invalid, name -> persistent));
		var valid = StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreeleaves\",Properties:{persistent:\"true\"}}");
		assertDoesNotThrow(() -> StrangeTreeBlockCodec.validateProperties(valid, name -> persistent));
	}

	@Test
	void rejectsNonCompoundPropertiesOrUnrecognizedFields() {
		assertThrows(IllegalArgumentException.class, () ->
				StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreelog\",Properties:4}"));
		assertThrows(IllegalArgumentException.class, () ->
				StrangeTreeBlockCodec.parse("{Name:\"mcanomalyarchives:strangetreelog\",Unexpected:\"payload\"}"));
	}
}
