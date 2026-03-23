package com.accountposting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
