package com.fsd.dispatch.geo.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationCoordinateValidatorTest {

    private StationCoordinateValidator validator;

    @BeforeEach
    void setUp() {
        validator = OsmPilotGeoTestSupport.stationValidator();
    }

    @ParameterizedTest(name = "{0} should pass coordinate validation")
    @CsvSource({
            "ZJF-PICK-01, 121.074453, 31.960396",
            "ZJF-PICK-02, 121.072610, 31.960726",
            "ZJF-DROP-01, 121.079762, 31.963627",
            "ZJF-DROP-02, 121.087005, 31.961780",
            "ZJF-DROP-03, 121.074367, 31.963548",
            "ZJF-DROP-04, 121.083893, 31.962833",
            "ZJF-EXPRESS-01, 121.072610, 31.960726",
            "ZJF-IDLE-01, 121.080055, 31.961922",
            "ZJF-CHG-01, 121.080069, 31.961850",
            "ZJF-CHG-02, 121.079780, 31.963518",
            "ZJF-CHG-03, 121.072610, 31.960726",
            "ZJF-CHG-04, 121.074442, 31.960671",
            "ZJF-CHG-05, 121.084334, 31.962890",
    })
    void pilotStationsShouldPassValidation(String code, double lng, double lat) {
        StationCoordinateValidator.ValidationResult result = validator.validate(lng, lat);
        assertTrue(result.valid(), code + ": " + result.summary());
    }

    @Test
    void buildingInteriorShouldFailValidation() {
        // OSM building footprint inside pilot (from data/pilot_osm_geo.json)
        StationCoordinateValidator.ValidationResult result = validator.validate(121.072758, 31.961387);
        assertFalse(result.valid());
    }

    @Test
    void outsidePilotFenceShouldFailValidation() {
        assertFalse(validator.withinBounds(121.070000, 31.961977));
        assertFalse(validator.withinBounds(121.080354, 31.958000));
    }

    @Test
    void coordinateFarFromRoadShouldFailValidation() {
        // 围栏内、非建筑区，但距 OSM 道路 >30m（V4-S2 阈值）
        StationCoordinateValidator.ValidationResult result = validator.validate(121.088600, 31.963000);
        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("距最近道路")));
    }
}
