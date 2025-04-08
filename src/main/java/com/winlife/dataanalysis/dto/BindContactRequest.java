package com.winlife.dataanalysis.dto;

import lombok.Data;

@Data
public class BindContactRequest {
    private String type; // phone or email or nickname
    private String value;
}
