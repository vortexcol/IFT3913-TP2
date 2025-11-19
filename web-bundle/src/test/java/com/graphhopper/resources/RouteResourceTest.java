package com.graphhopper.resources;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ProfileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RouteResource.
 *
 * These tests focus on the POST endpoint because it is easier to call directly
 * than the GET endpoint with all its query parameters.
 */
@ExtendWith(MockitoExtension.class)
class RouteResourceTest {

    @Mock
    private GraphHopper graphHopper;

    @Mock
    private ProfileResolver profileResolver;

    @Mock
    private HttpServletRequest httpRequest;

    /**
     * RouteResource is created with mocked dependencies.
     * The Boolean hasElevation is injected directly in the constructor call.
     */
    @Test
    void doPost_shouldReturnOkWhenGraphHopperReturnsSuccessfulResponse() {
        boolean hasElevation = true;
        RouteResource resource = new RouteResource(graphHopper, profileResolver, hasElevation);

        GHRequest request = new GHRequest(); // real object
        GHResponse ghResponse = new GHResponse(); // no errors by default

        when(graphHopper.route(request)).thenReturn(ghResponse);

        Response response = resource.doPost(request, httpRequest);

        // Verify HTTP 200
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus(),
                "Successful routing should return HTTP 200");

        // We at least expect a non-null entity
        assertNotNull(response.getEntity(), "Response entity should not be null");

        // Verify that graphHopper.route(...) was actually called once with our request
        ArgumentCaptor<GHRequest> captor = ArgumentCaptor.forClass(GHRequest.class);
        verify(graphHopper, times(1)).route(captor.capture());
        assertSame(request, captor.getValue(), "RouteResource should forward the same GHRequest instance");
    }

    @Test
    void doPost_shouldReturnBadRequestWhenGraphHopperReturnsErrors() {
        boolean hasElevation = true;
        RouteResource resource = new RouteResource(graphHopper, profileResolver, hasElevation);

        GHRequest request = new GHRequest();
        GHResponse ghResponse = new GHResponse();
        ghResponse.addError(new IllegalArgumentException("Simulated routing error"));

        when(graphHopper.route(request)).thenReturn(ghResponse);

        Response response = resource.doPost(request, httpRequest);

        // For error responses GraphHopper’s web layer normally returns 400
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus(),
                "Routing errors should map to HTTP 400 (Bad Request)");

        assertNotNull(response.getEntity(), "Error response should contain an entity describing the error");
    }

    /**
     * Example test around legacy parameter handling.
     * This checks that removeLegacyParameters does not throw and actually removes
     * keys that are considered legacy.
     *
     * You might have to adapt the exact key names depending on the current implementation.
     */
    @Test
    void removeLegacyParameters_shouldStripLegacyKeysFromHints() {
        com.graphhopper.util.PMap hints = new com.graphhopper.util.PMap();
        hints.putObject("weighting", "fastest");
        hints.putObject("vehicle", "car");
        // Legacy keys (example; adapt to whatever RouteResource currently treats as legacy)
        hints.putObject("ch.disable", true);
        hints.putObject("lm.disable", true);

        RouteResource.removeLegacyParameters(hints);

        // Current keys should remain
        assertEquals("fastest", hints.get("weighting"));
        assertEquals("car", hints.get("vehicle"));

        // Legacy keys should be gone (null == not present in PMap)
        assertNull(hints.get("ch.disable"), "Legacy key ch.disable should be removed");
        assertNull(hints.get("lm.disable"), "Legacy key lm.disable should be removed");
    }

    /**
     * Example of testing that errorIfLegacyParameters throws when legacy keys are present.
     * Again, you might need to adjust the exact keys depending on the actual implementation.
     */
    @Test
    void errorIfLegacyParameters_shouldThrowWhenLegacyKeyPresent() {
        com.graphhopper.util.PMap hints = new com.graphhopper.util.PMap();
        hints.putObject("vehicle", "car");
        hints.putObject("algo", "dijkstrabi");
        hints.putObject("ch.disable", true); // legacy

        assertThrows(IllegalArgumentException.class,
                () -> RouteResource.errorIfLegacyParameters(hints),
                "errorIfLegacyParameters should fail when legacy keys are present");
    }
}

