package dev.zpp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApocalypseMathTest {
    @Test void dayAndNightBoundaries() {
        assertEquals(1,ApocalypseMath.day(0)); assertEquals(1,ApocalypseMath.day(23999));
        assertEquals(2,ApocalypseMath.day(24000)); assertFalse(ApocalypseMath.night(12999));
        assertTrue(ApocalypseMath.night(13000)); assertTrue(ApocalypseMath.night(22999));
        assertFalse(ApocalypseMath.night(23000)); assertTrue(ApocalypseMath.night(37000));
    }
    @Test void eventsNeverEnableDisabledDaytimeAndRespectHardBudget() {
        var c=new ZppConfig(); assertEquals(0,ApocalypseMath.waveAmount(c,false,true,true));
        assertEquals(12,ApocalypseMath.waveAmount(c,true,true,true));
        c.nightAmount=64; assertEquals(64,ApocalypseMath.waveAmount(c,true,true,true));
        c.nightAmount=0; assertEquals(0,ApocalypseMath.waveAmount(c,true,true,true));
    }
    @Test void progressionIsBounded() {
        assertEquals(0,ApocalypseMath.progression(1,30)); assertEquals(0.5,ApocalypseMath.progression(16,30));
        assertEquals(1,ApocalypseMath.progression(31,30)); assertEquals(1,ApocalypseMath.progression(100000,30));
    }
}
