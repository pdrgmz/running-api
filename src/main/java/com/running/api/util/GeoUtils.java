package com.running.api.util;

import com.running.api.model.ActivityTrackpoint;

/**
 * Utilidades de cálculo geográfico compartidas por los parsers TCX/GPX/FIT.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {
    }

    /**
     * Distancia en metros entre dos coordenadas (latitud/longitud en grados) usando la
     * fórmula de Haversine.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * @return {@code true} si el trackpoint tiene latitud y longitud válidas.
     */
    public static boolean hasValidCoordinate(ActivityTrackpoint point) {
        return point != null
                && isValidLatitude(point.getLatitude())
                && isValidLongitude(point.getLongitude());
    }

    public static boolean isValidLatitude(Double latitude) {
        return latitude != null && Double.isFinite(latitude)
                && latitude >= -90.0 && latitude <= 90.0;
    }

    public static boolean isValidLongitude(Double longitude) {
        return longitude != null && Double.isFinite(longitude)
                && longitude >= -180.0 && longitude <= 180.0;
    }
}
