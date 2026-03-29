package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailEventListResponse {

    private List<EmailEventSummaryDto> items;
    private String nextCursor;
}
