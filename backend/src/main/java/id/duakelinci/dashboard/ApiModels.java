package id.duakelinci.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ApiModels {
  private ApiModels() {}
  record ValidationError(long row, String column, String code, String message, boolean rejected) {}
  record Metadata(UUID datasetId, String sourceType, String sourceFile, LocalDate minDate, LocalDate maxDate,
                  int validRows, int rejectedRows, Instant importedAt, Instant activatedAt, Instant loadedAt,
                  List<ValidationError> validationErrors, String status) {}
  record Kpis(long netSalesIdr, long grossSalesIdr, long discountIdr, long qtyCarton,
              int invoiceCount, int outletCount) {}
  record SalesAggregate(String key, long netSalesIdr, long grossSalesIdr, long qtyCarton, double contributionPct) {}
  record WeakOutlet(int rank, String outletId, String outletName, String region, String branch,
                    long netSalesIdr, int invoiceCount, long qtyCarton) {}
  record DashboardResult(Metadata metadata, Kpis kpis, List<SalesAggregate> salesByRegion,
                         List<SalesAggregate> salesByBrand, List<WeakOutlet> weakOutlets,
                         String telegramSummary, boolean empty) {}
  record FilterOptions(LocalDate minDate, LocalDate maxDate, List<String> regions, List<String> brands) {}
  record ReloadResult(boolean success, Metadata metadata, String message) {}
  record ImportPreview(String previewId, String originalFileName, long fileSizeBytes, String checksumSha256,
      Instant createdAt, Instant expiresAt, String status, int totalDataRows, int validRows, int rejectedRows,
      int truncatedErrorCount, LocalDate minDate, LocalDate maxDate, int invoiceCount, int outletCount,
      int regionCount, int brandCount, long netSalesIdr, boolean hasStructuralError,
      List<ValidationError> errors) {}
  record ActivateImportRequest(String previewId, boolean acceptRejectedRows, String expectedChecksum) {}
  record ImportAudit(UUID importId, Instant occurredAt, String originalFileName, long fileSizeBytes,
      String checksumSha256, int validRows, int rejectedRows, String result, String errorCode,
      String correlationId) {}
}
