package com.officialpapers.api.dto;

import com.officialpapers.api.enums.SuggestedActionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedActionDto {

    private SuggestedActionType type;
    private String label;
}
