package id.duakelinci.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CsvSalesRepositorySpringTest {
  @Test
  void springCreatesRepositoryUsingItsDependencyConstructor() throws Exception {
    Path root = Files.createTempDirectory("repository-spring-test");
    Path source = root.resolve("sales.csv");
    Files.writeString(source, """
        date,invoice_no,region,branch,outlet_id,outlet_name,brand,qty_carton,gross_sales_idr,discount_idr,net_sales_idr
        2026-11-23,I1,Jawa,Bandung,O1,Outlet 1,Dua Kelinci,1,100,0,100
        """);

    try (var context = new AnnotationConfigApplicationContext()) {
      TestPropertyValues.of(
          "sales.csv.path=" + source,
          "sales.import.dir=" + root.resolve("imports"),
          "sales.import.max-rows=100000"
      ).applyTo(context);
      context.register(SalesCsvParser.class, CsvSalesRepository.class);
      context.refresh();

      assertThat(context.getBean(CsvSalesRepository.class).snapshot().records()).hasSize(1);
    } finally {
      try (var paths = Files.walk(root)) {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
          try {
            Files.deleteIfExists(path);
          } catch (Exception ignored) {
          }
        });
      }
    }
  }
}
