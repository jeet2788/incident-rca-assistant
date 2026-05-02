package com.rca.rag;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetrievalResult {
    private String context;
    private List<String> incidentIds;
    private List<String> runbookIds;
}
