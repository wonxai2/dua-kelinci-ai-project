package id.duakelinci.dashboard;

import static id.duakelinci.dashboard.ApiModels.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class DashboardController {
  private final DashboardService service;
  DashboardController(DashboardService service){this.service=service;}
  @GetMapping("/dashboard") DashboardResult dashboard(@RequestParam(required=false,name="date_from") LocalDate from,@RequestParam(required=false,name="date_to") LocalDate to,@RequestParam(required=false,name="region") List<String> regions,@RequestParam(required=false,name="brand") List<String> brands){return service.dashboard(from,to,regions,brands);}
  @GetMapping("/filters") FilterOptions filters(){return service.filters();}
  @PostMapping("/data/reload") ReloadResult reload(){var m=service.reload();return new ReloadResult(true,m,"Data berhasil dimuat ulang");}
  @GetMapping("/health") Object health(){var f=service.filters();return java.util.Map.of("status","UP","data_loaded",true,"min_date",f.minDate(),"max_date",f.maxDate());}
  @ExceptionHandler({IllegalArgumentException.class}) ResponseEntity<?> bad(Exception e){return ResponseEntity.badRequest().body(java.util.Map.of("error",e.getMessage()));}
  @ExceptionHandler({IllegalStateException.class}) ResponseEntity<?> source(Exception e){return ResponseEntity.status(503).body(java.util.Map.of("error",e.getMessage()));}
}
