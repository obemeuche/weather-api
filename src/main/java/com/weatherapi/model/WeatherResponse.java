package com.weatherapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Nested structures (days, currentConditions) are Map<String,Object> rather than
 * dedicated POJOs because this service is a passthrough proxy: we cache and return
 * the data without running business logic on its internals. Loose mapping means
 * zero maintenance cost when Visual Crossing adds sub-fields.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    private String queryCost;
    private double latitude;
    private double longitude;
    private String resolvedAddress;
    private String address;
    private String timezone;
    private double tzoffset;
    private String description;

    /** Daily forecast data — each entry is one day's worth of weather values. */
    private List<Map<String, Object>> days;

    /** Current conditions at the time of the API call. */
    private Map<String, Object> currentConditions;
}
