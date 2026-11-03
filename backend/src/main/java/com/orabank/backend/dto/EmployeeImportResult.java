package com.orabank.backend.dto;

import java.util.List;

public record EmployeeImportResult(
        int created,
        int skipped,
        int failed,
        List<String> errors
) {}
