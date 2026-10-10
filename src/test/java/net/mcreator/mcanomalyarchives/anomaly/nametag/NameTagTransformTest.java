package net.mcreator.mcanomalyarchives.anomaly.nametag;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 直接执行生产公式；测试期望不从生产常量自动生成，防止规则漂移仍“自证通过”。 */
class NameTagTransformTest {
	@Test
	void documentedBoundsAndCoefficientsAreFixed() {
		assertEquals(300, NameTagTransform.MIN_TICKS);
		assertEquals(2400, NameTagTransform.MAX_TICKS);
		assertEquals(100, NameTagTransform.TICKS_PER_CHAR);
		assertEquals(1, NameTagTransform.TICKS_PER_DEFICIT);
	}

	@Test
	void shortOrMissingNamesUseFifteenSecondMinimum() {
		assertEquals(300, NameTagTransform.durationTicks("鸡", 300, 10));
		assertEquals(300, NameTagTransform.durationTicks("", 300, 10));
		assertEquals(300, NameTagTransform.durationTicks(null, 300, 10));
	}

	@Test
	void fiveCharactersWithNoDeficitTakeTwentyFiveSeconds() {
		assertEquals(500, NameTagTransform.durationTicks("下界合金镐", 300, 10));
	}

	@Test
	void currentDeficitExamplesUseActualJavaValuesNotOldPythonFormula() {
		assertEquals(1300, NameTagTransform.durationTicks("金块", 100, 1200));
		assertEquals(1100, NameTagTransform.durationTicks("金块", 300, 1200));
		assertEquals(900, NameTagTransform.durationTicks("钻石块", 300, 900));
	}

	@Test
	void namesCountUnicodeCodePointsNotUtf16Units() {
		assertEquals(300, NameTagTransform.durationTicks("𠮷𠮷𠮷", 100, 100));
		assertEquals(400, NameTagTransform.durationTicks("𠮷𠮷𠮷𠮷", 100, 100));
	}

	@Test
	void longNamesOrLargeDeficitsClampToTwoMinutes() {
		assertEquals(2400, NameTagTransform.durationTicks("长".repeat(100), 0, 100000));
		assertEquals(2400, NameTagTransform.durationTicks("金块", Integer.MIN_VALUE, Integer.MAX_VALUE));
	}

	@Test
	void materialSurplusDoesNotSubtractFromNameTime() {
		assertEquals(500, NameTagTransform.durationTicks("下界合金镐", Integer.MAX_VALUE, Integer.MIN_VALUE));
	}

	@Test
	void progressIsClampedAtBothEnds() {
		assertEquals(0.0f, NameTagTransform.progress(99, 100, 1000));
		assertEquals(0.0f, NameTagTransform.progress(100, 100, 1000));
		assertEquals(0.5f, NameTagTransform.progress(600, 100, 1000));
		assertEquals(1.0f, NameTagTransform.progress(1100, 100, 1000));
		assertEquals(1.0f, NameTagTransform.progress(99999, 100, 1000));
	}

	@Test
	void nonPositiveDurationIsAlreadyComplete() {
		assertEquals(1.0f, NameTagTransform.progress(100, 100, 0));
		assertEquals(1.0f, NameTagTransform.progress(100, 100, -1));
	}

	@Test
	void extremeTimeDifferenceDoesNotOverflowBackToZero() {
		assertEquals(1.0f, NameTagTransform.progress(Long.MAX_VALUE, Long.MIN_VALUE, 1000));
		assertEquals(0.0f, NameTagTransform.progress(Long.MIN_VALUE, Long.MAX_VALUE, 1000));
	}

	@Test
	void stagesMatchQuarterProgressThresholds() {
		float[] progress = {0.0f, 0.24f, 0.25f, 0.5f, 0.74f, 0.75f, 0.999f, 1.0f, 2.0f};
		int[] expected = {0, 0, 1, 2, 2, 3, 3, 4, 4};
		for (int index = 0; index < progress.length; index++) {
			assertEquals(expected[index], NameTagTransform.stageOf(progress[index]));
		}
	}

	@Test
	void increasingNamesOrDeficitsCannotMakeTransformationFaster() {
		int previous = 0;
		for (int length = 1; length <= 30; length++) {
			int duration = NameTagTransform.durationTicks("字".repeat(length), 100, 100);
			assertTrue(duration >= previous);
			previous = duration;
		}
		previous = 0;
		for (int need = 100; need <= 3000; need += 100) {
			int duration = NameTagTransform.durationTicks("金块", 100, need);
			assertTrue(duration >= previous);
			previous = duration;
		}
	}
}
