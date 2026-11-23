package id.duakelinci.dashboard;

import java.time.LocalDate;

record SalesRecord(LocalDate date, String invoiceNo, String region, String branch, String outletId,
                   String outletName, String brand, long qtyCarton, long grossSalesIdr,
                   long discountIdr, long netSalesIdr) {}
