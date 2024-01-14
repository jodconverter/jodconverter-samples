package org.jodconverter.sample.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParameterDecoderTest {

    private ParameterDecoder parameterDecoder;
    private Map<String, String> parameters;
    private Map<String, Object> loadProperties;
    private Map<String, Object> storeProperties;

    @BeforeEach
    void setUp() {
        parameterDecoder = new ParameterDecoder();
        parameters = new HashMap<>();
        loadProperties = new HashMap<>();
        storeProperties = new HashMap<>();
    }

    @Test
    void testDecodeParameters() {
        // Prepare test data
        parameters.put("lfdA", "true");
        parameters.put("sfdB", "false");
        parameters.put("lC", "42");
        parameters.put("sD", "value");

        // Call the method under test
        parameterDecoder.decodeParameters(parameters, loadProperties, storeProperties);

        // Verify the expected behavior and assertions
        assertEquals(true, ((Map<String, Object>) loadProperties.get("FilterData")).get("A"));
        assertEquals(false, ((Map<String, Object>) storeProperties.get("FilterData")).get("B"));

        assertEquals(42, loadProperties.get("C"));
        assertEquals("value", storeProperties.get("D"));
    }
}