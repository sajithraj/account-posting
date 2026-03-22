package com.accountposting.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "posting_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long configId;

    @Column(nullable = false)
    private String sourceName;

    @Column(nullable = false)
    private String requestType;

    @Column(nullable = false)
    private String targetSystem;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private Integer orderSeq;
}
