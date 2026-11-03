package com.example.bowling.dto;

import java.time.LocalDateTime;

public record UsageInfo(long usageId, long reservationId, long laneId, int laneNumber,
                        String memberName, LocalDateTime actualStartTime,
                        LocalDateTime actualEndTime, String usageStatus) {}
