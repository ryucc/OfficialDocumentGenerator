package com.officialpapers.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {

    private UUID id;
    private String sender;
    private String title;
    private String content;
    private LocalDateTime date;
}
