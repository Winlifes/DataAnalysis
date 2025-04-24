// src/main/java/com/winlife/dataanalysis/dto/EventReportStatistic.java
package com.winlife.dataanalysis.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventReportStatistic {
    private String eventName;
    private long count;
}