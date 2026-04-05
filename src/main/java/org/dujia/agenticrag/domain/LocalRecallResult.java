package org.dujia.agenticrag.domain;

import dev.langchain4j.rag.content.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LocalRecallResult {
    private List<Content> contents;
    private boolean weak;
    private double topScore;
    private String reason;
    private int hitCount;
}
