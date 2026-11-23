package id.duakelinci.dashboard;

import static id.duakelinci.dashboard.ApiModels.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class DashboardService {
  private static final Locale ID = Locale.forLanguageTag("id-ID");
  private final CsvSalesRepository repository;
  DashboardService(CsvSalesRepository repository) { this.repository = repository; }

  FilterOptions filters() { var s=repository.snapshot(); return new FilterOptions(s.metadata().minDate(), s.metadata().maxDate(), distinct(s.records(),SalesRecord::region), distinct(s.records(),SalesRecord::brand)); }
  Metadata reload() { return repository.reload(); }
  Metadata useDefault() { return repository.useDefault(); }
  DashboardResult dashboard(LocalDate from, LocalDate to, List<String> regions, List<String> brands) {
    var s=repository.snapshot(); var start=from==null?s.metadata().minDate():from; var end=to==null?s.metadata().maxDate():to;
    if (start.isAfter(end)) throw new IllegalArgumentException("Tanggal mulai tidak boleh setelah tanggal selesai");
    var rows=s.records().stream().filter(r -> !r.date().isBefore(start)&&!r.date().isAfter(end))
      .filter(r -> regions==null||regions.isEmpty()||regions.contains(r.region()))
      .filter(r -> brands==null||brands.isEmpty()||brands.contains(r.brand())).toList();
    var kpis=new Kpis(sum(rows,SalesRecord::netSalesIdr),sum(rows,SalesRecord::grossSalesIdr),sum(rows,SalesRecord::discountIdr),sum(rows,SalesRecord::qtyCarton),unique(rows,SalesRecord::invoiceNo),unique(rows,SalesRecord::outletId));
    var byRegion=aggregate(rows,SalesRecord::region,kpis.netSalesIdr()); var byBrand=aggregate(rows,SalesRecord::brand,kpis.netSalesIdr());
    var weak=weak(rows); var summary=summary(start,end,kpis,byRegion,byBrand,weak,rows.isEmpty());
    return new DashboardResult(s.metadata(),kpis,byRegion,byBrand,weak,summary,rows.isEmpty());
  }
  private List<SalesAggregate> aggregate(List<SalesRecord> rows, Function<SalesRecord,String> key, long total) {
    return rows.stream().collect(Collectors.groupingBy(key)).entrySet().stream().map(e -> new SalesAggregate(e.getKey(),sum(e.getValue(),SalesRecord::netSalesIdr),sum(e.getValue(),SalesRecord::grossSalesIdr),sum(e.getValue(),SalesRecord::qtyCarton),total==0?0:(100.0*sum(e.getValue(),SalesRecord::netSalesIdr)/total)))
      .sorted(Comparator.comparingLong(SalesAggregate::netSalesIdr).reversed().thenComparing(SalesAggregate::key)).toList();
  }
  private List<WeakOutlet> weak(List<SalesRecord> rows) {
    var groups=rows.stream().collect(Collectors.groupingBy(SalesRecord::outletId));
    var sorted=groups.entrySet().stream().map(e->{var first=e.getValue().getFirst();return new WeakOutlet(0,e.getKey(),first.outletName(),first.region(),first.branch(),sum(e.getValue(),SalesRecord::netSalesIdr),unique(e.getValue(),SalesRecord::invoiceNo),sum(e.getValue(),SalesRecord::qtyCarton));})
      .sorted(Comparator.comparingLong(WeakOutlet::netSalesIdr).thenComparing(WeakOutlet::outletId)).limit(5).toList();
    var result=new ArrayList<WeakOutlet>(); for(int i=0;i<sorted.size();i++){var w=sorted.get(i);result.add(new WeakOutlet(i+1,w.outletId(),w.outletName(),w.region(),w.branch(),w.netSalesIdr(),w.invoiceCount(),w.qtyCarton()));} return result;
  }
  private String summary(LocalDate from, LocalDate to,Kpis k,List<SalesAggregate> regions,List<SalesAggregate> brands,List<WeakOutlet> weak,boolean empty){
    String period=date(from)+(from.equals(to)?"":"–"+date(to)); if(empty)return "Tidak ada data penjualan pada periode "+period+" untuk filter yang dipilih.";
    String outlets=weak.stream().map(w->w.outletId()+" ("+money(w.netSalesIdr())+")").collect(Collectors.joining(", "));
    return "Periode "+period+": total net sales "+money(k.netSalesIdr())+". Region terbesar "+regions.getFirst().key()+" ("+money(regions.getFirst().netSalesIdr())+") dan brand terbesar "+brands.getFirst().key()+" ("+money(brands.getFirst().netSalesIdr())+"). Outlet dengan net sales terendah: "+outlets+".";
  }
  private String money(long n){return NumberFormat.getCurrencyInstance(ID).format(n).replace(",00","").replace("Rp ","Rp");}
  private String date(LocalDate d){return d.format(DateTimeFormatter.ofPattern("d MMM uuuu",ID));}
  private static long sum(List<SalesRecord> r,java.util.function.ToLongFunction<SalesRecord> f){long total=0;for(var row:r)try{total=Math.addExact(total,f.applyAsLong(row));}catch(ArithmeticException e){throw new IllegalArgumentException("Total data melampaui batas bilangan bulat 64-bit");}return total;}
  private static int unique(List<SalesRecord> r,Function<SalesRecord,String> f){return (int)r.stream().map(f).distinct().count();}
  private static List<String> distinct(List<SalesRecord> r,Function<SalesRecord,String> f){return r.stream().map(f).distinct().sorted().toList();}
}
