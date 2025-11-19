package com.graphhopper.routing.weighting;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for QueryGraphWeighting.
 *
 * These tests avoid using a real QueryGraph/BaseGraph and instead focus on:
 *  - virtual node detection
 *  - virtual edge detection
 *  - u-turn rule at virtual nodes
 *  - delegation to underlying weighting
 */
@ExtendWith(MockitoExtension.class)
class QueryGraphWeightingTest {

    @Mock
    Weighting innerWeighting;

    @Mock
    BaseGraph baseGraph;

    @Mock
    EdgeIteratorState edgeState;

    /**
     * Helper: create a QueryGraphWeighting with
     *  - firstVirtualNodeId = 100
     *  - firstVirtualEdgeId = 200
     */
    private QueryGraphWeighting createQGW() {
        when(baseGraph.getNodes()).thenReturn(100);
        when(baseGraph.getEdges()).thenReturn(200);

        IntArrayList closestEdges = new IntArrayList();
        closestEdges.add(50); // original edge for virtual edge id 200
        closestEdges.add(60); // original edge for virtual edge id 202
        return new QueryGraphWeighting(baseGraph, innerWeighting, closestEdges);
    }

    @Test
    void virtualNodeUTurn_shouldReturnInfinity() {
        QueryGraphWeighting qgw = createQGW();

        int virtualNode = 120;  // >= firstVirtualNodeId → virtual node
        int virtualEdge = 210;  // >= firstVirtualEdgeId → virtual edge

        double w = qgw.calcTurnWeight(virtualEdge, virtualNode, virtualEdge);

        assertEquals(Double.POSITIVE_INFINITY, w,
                "U-turns at virtual nodes should have infinite cost");
    }

    @Test
    void virtualNodeNonUTurn_shouldReturnZero() {
        QueryGraphWeighting qgw = createQGW();

        int virtualNode = 150;
        int inEdge = 210;
        int outEdge = 212; // both virtual but not equal → not a u-turn

        double w = qgw.calcTurnWeight(inEdge, virtualNode, outEdge);

        assertEquals(0.0, w,
                "Non-U-turn at virtual node should have zero turn cost");
    }

    @Test
    void normalTurn_shouldDelegateToInnerWeighting() {
        QueryGraphWeighting qgw = createQGW();

        int normalNode = 20;      // < firstVirtualNodeId
        int inEdge     = 5;       // < firstVirtualEdgeId
        int outEdge    = 7;

        when(innerWeighting.calcTurnWeight(inEdge, normalNode, outEdge))
                .thenReturn(4.5);

        double w = qgw.calcTurnWeight(inEdge, normalNode, outEdge);

        assertEquals(4.5, w,
                "Turn costs for normal nodes should delegate to inner weighting");
    }

    @Test
    void calcEdgeWeight_shouldDelegate() {
        QueryGraphWeighting qgw = createQGW();

        when(innerWeighting.calcEdgeWeight(edgeState, false))
                .thenReturn(12.0);

        double w = qgw.calcEdgeWeight(edgeState, false);

        assertEquals(12.0, w,
                "calcEdgeWeight should be delegated to inner weighting");
    }

    @Test
    void calcEdgeMillis_shouldDelegate() {
        QueryGraphWeighting qgw = createQGW();

        when(innerWeighting.calcEdgeMillis(edgeState, true))
                .thenReturn(777L);

        long ms = qgw.calcEdgeMillis(edgeState, true);

        assertEquals(777L, ms,
                "calcEdgeMillis must delegate to inner weighting");
    }

    @Test
    void hasTurnCosts_shouldDelegate() {
        QueryGraphWeighting qgw = createQGW();

        when(innerWeighting.hasTurnCosts()).thenReturn(true);
        assertTrue(qgw.hasTurnCosts(),
                "hasTurnCosts() should reflect the inner weighting");

        when(innerWeighting.hasTurnCosts()).thenReturn(false);
        assertFalse(qgw.hasTurnCosts(),
                "hasTurnCosts() should reflect updated inner weighting state");
    }

    @Test
    void getName_shouldDelegate() {
        QueryGraphWeighting qgw = createQGW();

        when(innerWeighting.getName()).thenReturn("speed");
        assertEquals("speed", qgw.getName(),
                "getName() must delegate to inner weighting");
    }
}

