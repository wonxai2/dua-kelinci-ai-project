package id.duakelinci.dashboard;

import static id.duakelinci.dashboard.ApiModels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class CsvSalesRepository {
  private static final Set<String> REQUIRED = Set.of("date","invoice_no","region","branch","outlet_id","outlet_name","brand","qty_carton","gross_sales_idr","discount_idr","net_sales_idr");
  private final Path source;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private List<SalesRecord> records = List.of();
  private Metadata metadata;

  CsvSalesRepository(@Value("${sales.csv.path}") String path) { source = Path.of(path).toAbsolutePath().normalize(); reload(); }

  Metadata reload() {
    var next = new ArrayList<SalesRecord>(); var errors = new ArrayList<ValidationError>();
    try {
      var lines = Files.readAllLines(source, StandardCharsets.UTF_8);
      if (lines.isEmpty()) throw new IllegalStateException("CSV kosong");
      var headerValues = split(lines.getFirst()); var headers = new HashMap<String,Integer>();
      for (int i=0;i<headerValues.size();i++) headers.put(headerValues.get(i),i);
      if (!headers.keySet().containsAll(REQUIRED)) {
        var missing = new TreeSet<>(REQUIRED); missing.removeAll(headers.keySet());
        throw new IllegalStateException("Header wajib tidak tersedia: " + String.join(", ", missing));
      }
      for (int i=1;i<lines.size();i++) { int rowNumber=i+1; try { if(!lines.get(i).isBlank()) next.add(parse(split(lines.get(i)),headers)); }
      catch (Exception e) { errors.add(new ValidationError(rowNumber, clean(e.getMessage()))); } }
      if (next.isEmpty()) throw new IllegalStateException("Tidak ada baris valid dalam CSV");
      var min = next.stream().map(SalesRecord::date).min(Comparator.naturalOrder()).orElseThrow();
      var max = next.stream().map(SalesRecord::date).max(Comparator.naturalOrder()).orElseThrow();
      var nextMetadata = new Metadata(source.toString(), min, max, next.size(), errors.size(), Instant.now(), List.copyOf(errors), errors.isEmpty() ? "healthy" : "warning");
      lock.writeLock().lock(); try { records = List.copyOf(next); metadata = nextMetadata; } finally { lock.writeLock().unlock(); }
      return nextMetadata;
    } catch (Exception e) { throw new IllegalStateException("Gagal memuat " + source + ": " + clean(e.getMessage()), e); }
  }
  private SalesRecord parse(List<String> r, Map<String,Integer> h) {
    for (String field : REQUIRED) if (get(r,h,field).isBlank()) throw new IllegalArgumentException(field + " kosong");
    return new SalesRecord(LocalDate.parse(get(r,h,"date")), get(r,h,"invoice_no"), get(r,h,"region"), get(r,h,"branch"),
      get(r,h,"outlet_id"), get(r,h,"outlet_name"), get(r,h,"brand"), integer(r,h,"qty_carton"), integer(r,h,"gross_sales_idr"), integer(r,h,"discount_idr"), integer(r,h,"net_sales_idr"));
  }
  private String get(List<String> r,Map<String,Integer> h,String key){int i=h.get(key);if(i>=r.size())throw new IllegalArgumentException(key+" tidak tersedia");return r.get(i).trim();}
  private long integer(List<String> r,Map<String,Integer> h,String key) { try { return Long.parseLong(get(r,h,key)); } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " bukan bilangan bulat"); } }
  private List<String> split(String line){var values=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){values.add(cell.toString());cell.setLength(0);}else cell.append(c);}if(quoted)throw new IllegalArgumentException("Tanda kutip CSV tidak lengkap");values.add(cell.toString());return values;}
  private String clean(String message) { return message == null ? "Kesalahan tidak diketahui" : message.replaceAll("[\\r\\n]+", " "); }
  Snapshot snapshot() { lock.readLock().lock(); try { return new Snapshot(records, metadata); } finally { lock.readLock().unlock(); } }
  record Snapshot(List<SalesRecord> records, Metadata metadata) {}
}
