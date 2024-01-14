package org.jodconverter.sample.rest;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ParameterDecoder {
    private static final String FILTER_DATA = "FilterData";

    private static final String LOAD_PROPERTIES_PREFIX_PARAM = "l";
    private static final String STORE_PROPERTIES_PREFIX_PARAM = "s";

    private static final String FILTER_DATA_PREFIX_PARAM = "fd";

    private static final String LOAD_FILTER_DATA_PREFIX_PARAM =
            LOAD_PROPERTIES_PREFIX_PARAM + FILTER_DATA_PREFIX_PARAM;

    private static final String STORE_FILTER_DATA_PREFIX_PARAM =
            STORE_PROPERTIES_PREFIX_PARAM + FILTER_DATA_PREFIX_PARAM;

    public void decodeParameters(final Map<String, String> parameters,
                                 final Map<String, Object> loadProperties,
                                 final Map<String, Object> storeProperties) {

        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        final Map<String, Object> loadFilterDataProperties = new HashMap<>();
        final Map<String, Object> storeFilterDataProperties = new HashMap<>();

        for (final Map.Entry<String, String> param : parameters.entrySet()) {
            final String key = param.getKey().toLowerCase(Locale.ROOT);
            if (!this.addProperty(key, LOAD_FILTER_DATA_PREFIX_PARAM, param, loadFilterDataProperties) &&
                    !this.addProperty(key, LOAD_PROPERTIES_PREFIX_PARAM, param, loadProperties) &&
                    !this.addProperty(key, STORE_FILTER_DATA_PREFIX_PARAM, param, storeFilterDataProperties)) {
                this.addProperty(key, STORE_PROPERTIES_PREFIX_PARAM, param, storeProperties);
            }
        }

        if (!loadFilterDataProperties.isEmpty()) {
            loadProperties.put(FILTER_DATA, loadFilterDataProperties);
        }

        if (!storeFilterDataProperties.isEmpty()) {
            storeProperties.put(FILTER_DATA, storeFilterDataProperties);
        }
    }

    private boolean addProperty(
            final String key,
            final String prefix,
            final Map.Entry<String, String> param,
            final Map<String, Object> properties) {
        if (key.startsWith(prefix)) {
            this.addProperty(prefix, param, properties);
            return true;
        }
        return false;
    }

    private void addProperty(
            final String prefix,
            final Map.Entry<String, String> param,
            final Map<String, Object> properties) {

        final String name = param.getKey().substring(prefix.length());
        final String value = param.getValue();

        if ("true".equalsIgnoreCase(value)) {
            properties.put(name, Boolean.TRUE);
        } else if ("false".equalsIgnoreCase(value)) {
            properties.put(name, Boolean.FALSE);
        } else {
            try {
                final int ival = Integer.parseInt(value);
                properties.put(name, ival);
            } catch (NumberFormatException nfe) {
                properties.put(name, value);
            }
        }
    }
}
