package com.accountposting.dto.config;

import lombok.Data;

@Data
public class PostingConfigResponseV2 {
    private Long configId;
    private String sourceName;
    private String requestType;
    private String targetSystem;
    private String operation;
    private Integer orderSeq;
}
