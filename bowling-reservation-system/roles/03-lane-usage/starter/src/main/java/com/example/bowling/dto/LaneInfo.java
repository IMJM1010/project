package com.example.bowling.dto;
import java.time.LocalDateTime;
public record LaneInfo(long laneId, int laneNumber, String laneStatus, boolean occupied, LocalDateTime updatedAt) {
    public String label() { return switch(laneStatus) { case "AVAILABLE" -> "이용가능"; case "IN_USE" -> "이용중"; case "MAINTENANCE" -> "점검중"; default -> laneStatus; }; }
}
