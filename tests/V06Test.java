import in.prardhana.app.*;
import java.time.*;
import java.util.*;
public class V06Test {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  for(String text:List.of("Dear user IPO application for Rs 195000 received", "Rs 500000 can be credited to your account", "Rs 500 free cash credited to wallet", "Pass code for Purchase of insurance for Rs 100000 is 333333", "Rs 1549 received as repayment of ZIP"))check(TransactionParser.parse(text)==null,"Reject false money: "+text);
  check(TransactionParser.parse("INR 500 credited to your account","AX-ZEPTON-S")==null,"Provider cannot invent bank income");
  check(TransactionParser.parse("INR 500 credited to your account","AX-IDFCFB-S")!=null,"Bank credit preserved");
  long arrived=LocalDate.of(2025,8,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
  check(FinancialFacts.read("Your EMI of Rs 5426 is due on 05-Aug",arrived).dueDate().equals("2025-08-05"),"Yearless due date");
  check(FinancialFacts.read("EMI of Rs 100 due on 9th Jul'25",arrived).dueDate().equals("2025-07-09"),"Ordinal date");
  check(FinancialFacts.read("Your account will be debited with Rs 130 towards Google Play for UPI Mandate on 16/11/2025",arrived).dueDate().equals("2025-11-16"),"Non-adjacent execution date");
  check(FinancialFacts.read("Blinkit requested money. On approval Rs 250 will be debited").event().equals("Payment request"),"Collect not schedule");
  check(FinancialFacts.read("Delivery failed for order 123456").event().isEmpty(),"Delivery not failed payment");
  check(FinancialFacts.read("Your IDFC A/c XX1234 credited INR 10. Your new balance is INR 1000").balance()==100000L,"New balance is");
  var p=TransactionParser.parse("Paid INR 100 to Cafe");check(!DuplicatePolicy.same(p,"sms",1000,10000,"debit","Cafe","","sms",1001),"Rapid identical purchases preserved");
  LocalDate now=LocalDate.of(2026,10,24);List<RecurringEngine.Payment> payments=new ArrayList<>();for(int i=0;i<3;i++)payments.add(new RecurringEngine.Payment("p"+i,"utility|1","Electricity","Utilities",10000+i*5000,now.minusMonths(2-i),true));
  var series=RecurringEngine.analyze(payments,now);check(series.size()==1&&series.get(0).variable()&&series.get(0).kind().equals("Utility"),"Variable bills recur");
  payments.clear();for(int i=0;i<3;i++)payments.add(new RecurringEngine.Payment("w"+i,"plan|1","Plan","Subscriptions",5000,now.minusWeeks(2-i),true));check(RecurringEngine.analyze(payments,now).get(0).cadence().equals("Weekly"),"Weekly recurrence");
  payments.clear();for(int i=0;i<3;i++)payments.add(new RecurringEngine.Payment("a"+i,"annual|1","Insurance","Insurance",500000,now.minusYears(2-i),true));check(RecurringEngine.analyze(payments,now).get(0).cadence().equals("Annual"),"Annual recurrence");
  System.out.println("v0.6 parsing, dates, false-money guards and recurring series passed");
 }
}
