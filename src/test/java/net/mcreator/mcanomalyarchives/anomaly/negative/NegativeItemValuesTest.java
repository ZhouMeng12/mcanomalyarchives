package net.mcreator.mcanomalyarchives.anomaly.negative;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NegativeItemValuesTest {
    @Test void inventoryMagnitudesHaveSignedLabelsIncludingSingleItems() {
        assertEquals("-1",NegativeItemValues.countLabel(1));
        assertEquals("-2",NegativeItemValues.countLabel(2));
        assertEquals("-64",NegativeItemValues.countLabel(64));
    }
    @Test void everyUseApproachesZeroWithoutWrappingBackToPositiveDurability() {
        int remaining=-1561;
        for(int n=1;n<=1561;n++)assertEquals(-1561+n,remaining=NegativeItemValues.afterUse(remaining));
        assertEquals(0,NegativeItemValues.afterUse(remaining));
    }
    @Test void vanillaRemainingDurabilityIsNegativeAndIncreasesWithUse() {
        int damage=NegativeItemValues.vanillaDamage(1561,-1561);
        assertEquals(3122,damage);
        assertEquals(-1561,1561-damage);
        assertEquals(-1560,1561-NegativeItemValues.vanillaDamage(1561,-1560));
        assertEquals(-1,1561-NegativeItemValues.vanillaDamage(1561,-1));
        assertEquals(0,1561-NegativeItemValues.vanillaDamage(1561,0));
        assertEquals(Integer.MAX_VALUE,NegativeItemValues.vanillaDamage(1561,Integer.MIN_VALUE));
    }
    @Test void eatingAtFullHungerHasTheSameFourPointPenaltyAsEatingBelowTheCap() {
        assertEquals(16,NegativeItemValues.foodAfter(20,4));
        assertEquals(14,NegativeItemValues.foodAfter(18,4));
        assertEquals(0,NegativeItemValues.foodAfter(2,4));
        assertEquals(2.6f,NegativeItemValues.saturationAfter(5,2.4f,16),.0001f);
        assertEquals(0,NegativeItemValues.saturationAfter(1,2.4f,16),.0001f);
    }
}
