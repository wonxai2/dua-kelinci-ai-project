package id.duakelinci.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.*;

class DashboardServiceTest {
  private Path csv;
  @BeforeEach void setup() throws Exception { csv=Files.createTempFile("sales-test", ".csv"); Files.writeString(csv,"date,invoice_no,region,branch,outlet_id,outlet_name,brand,qty_carton,gross_sales_idr,discount_idr,net_sales_idr\n2026-09-01,I1,R1,B1,O2,Outlet 2,X,1,100,0,100\n2026-09-01,I2,R1,B1,O1,Outlet 1,Y,1,100,20,80\n2026-09-01,I3,R1,B1,O0,Outlet 0,Y,1,100,20,80\nbad,I4,R2,B2,O3,Invalid,X,1,100,0,100\n"); }
  @AfterEach void cleanup() throws Exception { Files.deleteIfExists(csv); }
  @Test void aggregatesRejectsInvalidAndBreaksOutletTiesById(){var repo=new CsvSalesRepository(csv.toString());var result=new DashboardService(repo).dashboard(null,null,null,null);assertThat(result.kpis().netSalesIdr()).isEqualTo(260);assertThat(result.metadata().rejectedRows()).isEqualTo(1);assertThat(result.weakOutlets()).extracting(ApiModels.WeakOutlet::outletId).containsExactly("O0","O1","O2");}
  @Test void emptyFilterProducesHonestSummary(){var repo=new CsvSalesRepository(csv.toString());var result=new DashboardService(repo).dashboard(null,null,java.util.List.of("absent"),null);assertThat(result.empty()).isTrue();assertThat(result.telegramSummary()).startsWith("Tidak ada data");}
}
