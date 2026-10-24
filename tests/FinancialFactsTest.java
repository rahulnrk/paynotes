import in.prardhana.app.FinancialFacts;
public class FinancialFactsTest {
    static void check(boolean v,String label){if(!v)throw new AssertionError(label);}
    public static void main(String[] args){
        var f=FinancialFacts.read("SBI UPI A/c XX1234 debited by 150 on date 23Oct26 trf to Fixture Refno 123456789012");
        check(f.date().equals("2026-10-23")&&f.clock().isEmpty(),"compact date does not invent midnight");
        var b=FinancialFacts.read("IDFC Your A/c XX123 debited INR 50 on 23/10/26 18:32. New Bal :INR -250.50");
        check(b.balance()!=null&&b.balance()==-25050&&b.account().equals("Account ••123")&&b.clock().equals("18:32"),"negative balance and clock");
        check(FinancialFacts.read("Available balance INR 99999").balance()==null,"balance requires bank and masked account context");
        var event=FinancialFacts.read("SBI UPI AutoPay debit of Rs 399 is scheduled on 24/10/26");
        check(event.event().equals("Scheduled payment")&&event.dueDate().equals("2026-10-24"),"scheduled due date");
        check(FinancialFacts.read("Refund of Rs 50 initiated").event().equals("Pending refund"),"refund lifecycle");
        check(FinancialFacts.read("Your account is not debited with Rs 50").event().equals("Failed payment"),"failed movement");
        check(FinancialFacts.read("Get up to Rs 500 loan due on 24/10/26 apply now").event().isEmpty(),"advertisement not obligation");
        check(FinancialFacts.read("SBI A/c XX123 on 31Feb26").date().isEmpty(),"invalid calendar date");
        System.out.println("Financial facts checks passed");
    }
}
