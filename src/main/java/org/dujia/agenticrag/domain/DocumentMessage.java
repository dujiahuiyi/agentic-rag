package org.dujia.agenticrag.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentMessage {
    private Long taskId;
    private String fileUrl;
    private Long assistantId;
}
