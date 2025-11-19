package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.storage.TurnCostStorage;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SpeedWeighting}.
 *
 * These tests focus on:
 *  - behavior when speed is zero
 *  - use of reverse speed when reverse=true
 *  - min-weight-per-distance calculation
 *  - turn cost behavior (u-turn vs non u-turn)
 *  - hasTurnCosts() depending on constructor used
 */
@ExtendWith(MockitoExtension.class)
class SpeedWeightingTest {

    @Mock
    DecimalEncodedValue speedEnc;

    @Mock
    DecimalEncodedValue turnCostEnc;

    @Mock
    TurnCostStorage turnCostStorage;

    @Mock
    EdgeIteratorState edgeState;

    @Test
    void calcEdgeWeight_returnsInfinityWhenSpeedIsZero() {
        // Given a SpeedWeighting without turn costs
        SpeedWeighting weighting = new SpeedWeighting(speedEnc);

        // Edge has speed 0 in forward direction
        when(edgeState.get(speedEnc)).thenReturn(0.0);

        double weight = weighting.calcEdgeWeight(edgeState, false);

        assertEquals(Double.POSITIVE_INFINITY, weight,
                "If speed is zero, calcEdgeWeight must return +∞");
        // reverse speed should not even be queried in this case
        verify(edgeState, never()).getReverse(speedEnc);
    }

    @Test
    void calcEdgeWeight_usesReverseSpeedWhenReverseIsTrue() {
        SpeedWeighting weighting = new SpeedWeighting(speedEnc);

        // distance = 1000m, reverse speed = 10 m/s => weight = 100 seconds
        when(edgeState.getReverse(speedEnc)).thenReturn(10.0);
        when(edgeState.getDistance()).thenReturn(1000.0);

        double weight = weighting.calcEdgeWeight(edgeState, true);

        assertEquals(100.0, weight, 1e-6,
                "When reverse=true, weight must be distance / reverseSpeed");

        // Forward speed should not be used in this case
        verify(edgeState, never()).get(speedEnc);
        verify(edgeState, times(1)).getReverse(speedEnc);
    }

    @Test
    void calcMinWeightPerDistance_usesMaxStorableSpeed() {
        // max storable speed 20 m/s -> min weight per meter = 1/20
        when(speedEnc.getMaxStorableDecimal()).thenReturn(20.0);

        SpeedWeighting weighting = new SpeedWeighting(speedEnc);

        double minWeightPerDistance = weighting.calcMinWeightPerDistance();

        assertEquals(1.0 / 20.0, minWeightPerDistance, 1e-9,
                "calcMinWeightPerDistance should be 1 / maxStorableSpeed");
    }

    @Test
    void constructorWithTurnCostStorage_appliesUTurnCostMinimum() {
        double uTurnCost = 5.0;
        SpeedWeighting weighting = new SpeedWeighting(speedEnc, turnCostEnc, turnCostStorage, uTurnCost);

        int viaNode = 1;
        int edgeId = 7;

        // For a u-turn (inEdge == outEdge) we expect max(storedCost, uTurnCost)
        when(turnCostStorage.get(turnCostEnc, edgeId, viaNode, edgeId)).thenReturn(1.0);

        double uTurnWeight = weighting.calcTurnWeight(edgeId, viaNode, edgeId);

        assertEquals(uTurnCost, uTurnWeight, 1e-9,
                "For a u-turn, SpeedWeighting must apply max(stored, uTurnCosts)");

        // For a normal turn, we expect the stored cost directly
        int inEdge = 3;
        int outEdge = 4;
        when(turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge)).thenReturn(2.5);

        double turnWeight = weighting.calcTurnWeight(inEdge, viaNode, outEdge);

        assertEquals(2.5, turnWeight, 1e-9,
                "For a non u-turn, SpeedWeighting must use the stored turn cost as-is");
    }

    @Test
    void hasTurnCosts_falseForDefaultConstructor_trueForTurnCostProvider() {
        SpeedWeighting noTurnCosts = new SpeedWeighting(speedEnc);
        assertFalse(noTurnCosts.hasTurnCosts(),
                "Default constructor without TurnCostProvider should report hasTurnCosts=false");

        TurnCostProvider provider = mock(TurnCostProvider.class);
        SpeedWeighting withTurnCosts = new SpeedWeighting(speedEnc, provider);
        assertTrue(withTurnCosts.hasTurnCosts(),
                "Constructor with custom TurnCostProvider should report hasTurnCosts=true");
    }
}

