package com.running.api.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FitActivityDTO {
    private LocalDateTime timestamp;
    private Double totalDistanceMeters;
    private Double totalTimerTimeSeconds;
    private Double elevationGain;
    private Double elevationLoss;
    private Integer totalCalories;
    private Integer maxCadence;
    private Double trainingLoad;
    private Double vamKmH;
    private Double vo2MaxEstimated;
    private List<TrackPoint> trackPoints = new ArrayList<>();

    public static class TrackPoint {
        private LocalDateTime timestamp;
        private Double latitude;
        private Double longitude;
        private Integer heartRate;
        private Float speed;
        private Double altitudeMeters;
        private Double distanceMeters;
        private Integer cadence;

        // Getters y Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Integer getHeartRate() { return heartRate; }
        public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }
        public Float getSpeed() { return speed; }
        public void setSpeed(Float speed) { this.speed = speed; }
        public Double getAltitudeMeters() { return altitudeMeters; }
        public void setAltitudeMeters(Double altitudeMeters) { this.altitudeMeters = altitudeMeters; }

        public Double getDistanceMeters() { return distanceMeters; }
        public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

        public Integer getCadence() { return cadence; }
        public void setCadence(Integer cadence) { this.cadence = cadence; }
    }

    // Getters y Setters de FitActivityDTO
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Double getTotalDistanceMeters() { return totalDistanceMeters; }
    public void setTotalDistanceMeters(Double totalDistanceMeters) { this.totalDistanceMeters = totalDistanceMeters; }
    public Double getTotalTimerTimeSeconds() { return totalTimerTimeSeconds; }
    public void setTotalTimerTimeSeconds(Double totalTimerTimeSeconds) { this.totalTimerTimeSeconds = totalTimerTimeSeconds; }
    public Double getElevationGain() { return elevationGain; }
    public void setElevationGain(Double elevationGain) { this.elevationGain = elevationGain; }
    public Double getElevationLoss() { return elevationLoss; }
    public void setElevationLoss(Double elevationLoss) { this.elevationLoss = elevationLoss; }
    public Integer getTotalCalories() { return totalCalories; }
    public void setTotalCalories(Integer totalCalories) { this.totalCalories = totalCalories; }
    public Integer getMaxCadence() { return maxCadence; }
    public void setMaxCadence(Integer maxCadence) { this.maxCadence = maxCadence; }
    public Double getTrainingLoad() { return trainingLoad; }
    public void setTrainingLoad(Double trainingLoad) { this.trainingLoad = trainingLoad; }
    public Double getVamKmH() { return vamKmH; }
    public void setVamKmH(Double vamKmH) { this.vamKmH = vamKmH; }
    public Double getVo2MaxEstimated() { return vo2MaxEstimated; }
    public void setVo2MaxEstimated(Double vo2MaxEstimated) { this.vo2MaxEstimated = vo2MaxEstimated; }
    public List<TrackPoint> getTrackPoints() { return trackPoints; }
}
