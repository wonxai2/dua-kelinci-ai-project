package id.duakelinci.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class ApiModels {
  private ApiModels() {}
  record ValidationError(long row, String reason) {}
  record Metadata(String sourceFile, LocalDate minDate, LocalDate maxDate, int validRows, int rejectedRows,
                  Instant loadedAt, List<ValidationError> validationErrors, String status) {}
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
}
