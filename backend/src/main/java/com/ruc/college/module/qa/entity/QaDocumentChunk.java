package com.ruc.college.module.qa.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaDocumentChunk {
    private Long id;
    private Long documentId;
    private String title;
    private String category;
    private Integer chunkIndex;
    private String content;
    private String keywords;
    private Double score;
    private LocalDateTime createdAt;
}
