package net.mcreator.mcanomalyarchives.client.hud;

/**
 * 纯逻辑测试 - 诺德指数映射、电量分段、序列号格式、兰达指数。
 * 
 * 运行方式: javac HudLogicTest.java && java HudLogicTest
 * 或通过 gradle test (配置后)
 */
public class HudLogicTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testNordMapping();
        testEnergySegments();
        testSerialFormat();
        testLandaIndex();
        testIndicatorColor();

        System.out.println("\n========== RESULTS ==========");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        if (failed > 0) {
            System.out.println("SOME TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("All tests passed!");
        }
    }

    // === Nord Index Tests ===

    private static void testNordMapping() {
        // Test 7-level mapping
        check("sanity 100 → 极高", "极高",
            NordIndexResolver.getText(100));
        check("sanity 90 → 极高", "极高",
            NordIndexResolver.getText(90));
        check("sanity 89 → 较高", "较高",
            NordIndexResolver.getText(89));
        check("sanity 75 → 较高", "较高",
            NordIndexResolver.getText(75));
        check("sanity 74 → 正常", "正常",
            NordIndexResolver.getText(74));
        check("sanity 60 → 正常", "正常",
            NordIndexResolver.getText(60));
        check("sanity 59 → 较低", "较低",
            NordIndexResolver.getText(59));
        check("sanity 45 → 较低", "较低",
            NordIndexResolver.getText(45));
        check("sanity 44 → 低", "低",
            NordIndexResolver.getText(44));
        check("sanity 30 → 低", "低",
            NordIndexResolver.getText(30));
        check("sanity 29 → 警戒", "警戒",
            NordIndexResolver.getText(29));
        check("sanity 15 → 警戒", "警戒",
            NordIndexResolver.getText(15));
        check("sanity 14 → 危险", "危险",
            NordIndexResolver.getText(14));
        check("sanity 0 → 危险", "危险",
            NordIndexResolver.getText(0));
    }

    private static void testIndicatorColor() {
        check("sanity 90 → green", 0xFF99FF00,
            NordIndexResolver.getColor(90));
        check("sanity 60 → green", 0xFF99FF00,
            NordIndexResolver.getColor(60));
        check("sanity 50 → yellow", 0xFFFFCC00,
            NordIndexResolver.getColor(50));
        check("sanity 30 → yellow", 0xFFFFCC00,
            NordIndexResolver.getColor(30));
        check("sanity 20 → red", 0xFFFF3333,
            NordIndexResolver.getColor(20));
        check("sanity 5 → red", 0xFFFF4444,
            NordIndexResolver.getColor(5));
    }

    // === Energy Segment Tests ===

    private static void testEnergySegments() {
        int maxEnergy = 12000;

        check("FE 12000 → 4 segments", 4,
            EnergyVisuals.getSegmentCount(12000, maxEnergy));
        check("FE 9001 → 4 segments", 4,
            EnergyVisuals.getSegmentCount(9001, maxEnergy));
        check("FE 9000 → 3 segments", 3,
            EnergyVisuals.getSegmentCount(9000, maxEnergy));
        check("FE 6001 → 3 segments", 3,
            EnergyVisuals.getSegmentCount(6001, maxEnergy));
        check("FE 6000 → 2 segments", 2,
            EnergyVisuals.getSegmentCount(6000, maxEnergy));
        check("FE 3001 → 2 segments", 2,
            EnergyVisuals.getSegmentCount(3001, maxEnergy));
        check("FE 3000 → 1 segment", 1,
            EnergyVisuals.getSegmentCount(3000, maxEnergy));
        check("FE 1 → 1 segment", 1,
            EnergyVisuals.getSegmentCount(1, maxEnergy));
        check("FE 0 → 0 segments", 0,
            EnergyVisuals.getSegmentCount(0, maxEnergy));
    }

    // === Serial Format Tests ===

    private static void testSerialFormat() {
        for (int i = 0; i < 100; i++) {
            String serial = SerialGenerator.generate();
            check("Serial length = 2", 2, serial.length());
            char letter = serial.charAt(0);
            check("First char is uppercase letter (" + letter + ")",
                letter >= 'A' && letter <= 'Z');
            char digit = serial.charAt(1);
            check("Second char is digit 1-9 (" + digit + ")",
                digit >= '1' && digit <= '9');
        }
    }

    // === Landa Index Tests ===

    private static void testLandaIndex() {
        // No addiction → -1 sentinel
        check("no addiction → -1", -1.0,
            LandaIndex.fromEffect(0), 0.01);
        // amplifier 0 → 1.0
        check("amplifier 0 → 1.0", 1.0,
            LandaIndex.fromEffect(1), 0.01);
        // amplifier 2 → 3.0
        check("amplifier 2 → 3.0", 3.0,
            LandaIndex.fromEffect(3), 0.01);
        // amplifier 6 → 7.0 (max)
        check("amplifier 6 → 7.0", 7.0,
            LandaIndex.fromEffect(7), 0.01);
        // amplifier 10 → 7.0 (clamped)
        check("amplifier 10 → 7.0 (clamped)", 7.0,
            LandaIndex.fromEffect(11), 0.01);
    }

    // === Test helpers ===

    private static void check(String description, Object expected, Object actual) {
        if (expected.equals(actual)) {
            passed++;
            System.out.println("  PASS: " + description);
        } else {
            failed++;
            System.out.println("  FAIL: " + description);
            System.out.println("    expected: " + expected);
            System.out.println("    actual:   " + actual);
        }
    }

    private static void check(String description, double expected, double actual, double delta) {
        if (Math.abs(expected - actual) < delta) {
            passed++;
            System.out.println("  PASS: " + description);
        } else {
            failed++;
            System.out.println("  FAIL: " + description);
            System.out.println("    expected: " + expected);
            System.out.println("    actual:   " + actual);
        }
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + description);
        } else {
            failed++;
            System.out.println("  FAIL: " + description);
        }
    }
}
