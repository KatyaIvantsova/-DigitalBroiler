package com.broiler_monitoring.dto.norms;

import java.util.List;

public record NormImportResult(int created, int updated, int unchanged, List<String> errors) {
}
