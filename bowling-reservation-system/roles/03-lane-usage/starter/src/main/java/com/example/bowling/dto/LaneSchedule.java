package com.example.bowling.dto;
import java.time.LocalDateTime;
public record LaneSchedule(long reservationLaneId, long reservationId, String memberName,
 LocalDateTime startTime, LocalDateTime endTime, String reservationStatus, String usageStatus,
 int personCount, Integer actualPersonCount) {}
