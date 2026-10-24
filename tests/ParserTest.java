import in.prardhana.app.TransactionParser;

public class ParserTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        var debit=TransactionParser.parse("INR 1,250.50 debited from a/c XX12 to Swiggy on 23 Oct. UTR 123456789012");
        check(debit!=null&&debit.paise()==125050&&debit.direction().equals("debit"),"debit amount");
        check(debit.reference().equals("123456789012"),"UTR extraction");
        check(TransactionParser.parse("Received Rs. 500 from Alice ref ABC123456").direction().equals("credit"),"credit");
        check(TransactionParser.parse("Paid INR 240 to Swiggy ref DEMO1001").category().equals("Food & drink"),"merchant categorisation");
        var bank=TransactionParser.parse("Your A/c XX1234 debited by Rs 240.00 on 23-10-2026. Example credited. UPIRef 123456789012. Available balance Rs 10000.00");
        check(bank!=null&&bank.direction().equals("debit")&&bank.paise()==24000,"own account debit vs beneficiary credit");
        check(bank.reference().equals("123456789012"),"concatenated UPI reference");
        check(bank.merchant().equals("Example"),"beneficiary name in debit alert");
        check(TransactionParser.parse("Your account XX1234 credited with INR 500. Sender account debited").direction().equals("credit"),"own account credit vs sender debit");
        check(TransactionParser.parse("Received Rs 500 from Alice Reference No. ABC123456").reference().equals("ABC123456"),"reference number label");
        check(TransactionParser.parse("Recharge of Rs 199 successful. Amount credited Rs 199. Main balance Rs 0").category().equals("Payment receipt"),"telecom receipt is not income");
        check(TransactionParser.parse("Your account XX1234 will be debited INR 500 tomorrow")==null,"future debit not recorded");
        check(TransactionParser.parse("Bill of Rs 500 is due tomorrow. Ignore if already paid")==null,"bill reminder with paid keyword is not a debit");
        check(TransactionParser.parse("Received Rs 500 from Alice ref ABC123456").category().equals("Money received"),"generic credit is not income");
        check(TransactionParser.parse("Salary INR 50000 credited to your account").type().equals("Salary"),"explicit salary");
        check(TransactionParser.parse("Interest of INR 250 has been credited to your a/c on 23-10-2026").type().equals("Interest"),"interest");
        check(TransactionParser.parse("Cashback Rs 50 credited to your account").type().equals("Cashback"),"cashback");
        check(TransactionParser.parse("We successfully received a payment of Rs 500 towards your loan").category().equals("Payment receipt"),"lender receipt is not incoming money or a second expense");
        check(TransactionParser.parse("Proposal no 123456 for Rs 500 received. Use your ID for details")==null,"proposal receipt is not a credit");
        var vpa=TransactionParser.parse("Paid INR 250 to example@ybl ref VPA123456");check(vpa.vpa().equals("example@ybl")&&vpa.merchant().equals("Unknown merchant")&&vpa.channel().equals("UPI"),"UPI identifier is not a verified name");
        check(TransactionParser.parse("Your a/c XX123 debited INR 400 via UPI/DR/123456789012").reference().equals("123456789012"),"UPI narration reference");
        check(TransactionParser.categoryFor("Solar Services").equals("Other"),"brand substrings must not classify unrelated people");
        var implicit=TransactionParser.parse("Example UPI: A/c XX1234 debited by 150.00 on 23Oct2026 to Fixture Store RefNo 123456789012 If not you call support");
        check(implicit!=null&&implicit.paise()==15000&&implicit.channel().equals("UPI")&&implicit.merchant().equals("Fixture Store")&&implicit.reference().equals("123456789012"),"currency-less account-scoped UPI debit");
        check(TransactionParser.parse("UPI payment 150.00 RefNo 123456789012")==null,"bare amount without account movement is not a transaction");
        var receipt=TransactionParser.parse("Thank you. Your mobile bill payment of Rs 299 was successful. Get paid up to Rs 1000 with Example Bank credit card");
        check(receipt!=null&&receipt.paise()==29900&&receipt.type().equals("Payment acknowledgement")&&receipt.category().equals("Payment receipt")&&!receipt.channel().equals("Card"),"credit-card marketing footer is not card-payment evidence");
        check(TransactionParser.parse("Rs 99 recharge credited. Your mobile plan has 28 days validity")==null,"recharge credit is not bank money");
        var card=TransactionParser.parse("INR 1250.00 spent on your credit card XX1234 at Fixture Store on 23-10-2026. Available limit INR 50000");
        check(card!=null&&card.channel().equals("Card")&&card.paise()==125000,"real card purchase amount, not available limit");
        check(TransactionParser.parse("Rs 250 used on your debit card XX1234 at Fixture Store on 23-10-2026").channel().equals("Card"),"used-on-card dialect");
        check(TransactionParser.parse("USD 25 spent on your credit card at Fixture Store. Available limit INR 50000")==null,"foreign currency purchase cannot become INR credit limit");
        check(TransactionParser.parse("Example UPI: A/c XX1234 debited by 500.00 on 23Oct2026 to ATM CASH RefNo 123456789012").type().equals("Cash withdrawal"),"ATM evidence takes precedence over generic bank UPI header");
        for(String bad:new String[]{"OTP 123456 to pay INR 500","Payment failed: paid INR 500","INR 800 payment due on Monday","Collect request: INR 600","Available balance INR 10000","INR 900 debited and INR 900 credited","INR 0 paid to Uber","Scheduled payment INR 300 to Spotify"})check(TransactionParser.parse(bad)==null,"false positive: "+bad);
        for(String bad:new String[]{"Get up to Rs 20000 credited to your account. Apply now","Your account is not debited with Rs 130 due to cbs rejection","Your beneficiary Fixture has received Rs 5000 transferred via NEFT","Your fare Rs 680 will be refunded in 7 days","EMI Rs 7315 will be presented via NACH. Sent as requested"})check(TransactionParser.parse(bad)==null,"research false positive: "+bad);
        check(TransactionParser.parse("ICICI Bank Acct XX123 debited for Rs 250 on 12-Sep-25; SAMPLE SHOP credited. UPI:512345678901").merchant().equals("SAMPLE SHOP"),"ICICI own-account debit");
        check(TransactionParser.parse("Sent Rs 250 from Kotak Bank A/c XX123 to SAMPLE SHOP on 12-09-25 UPI Ref 512345678901").merchant().equals("SAMPLE SHOP"),"Kotak destination payee");
        check(TransactionParser.parse("Monthly interest of INR.123 earned on Savings A/c XX123 has been credited").paise()==12300,"INR dot");
        check(TransactionParser.parse("Your Payment of Rs 1240.91 at SAMPLE CLOUD for e-mandate has been processed successfully on your Debit Card ending 1234").channel().equals("Card"),"completed card mandate");
        check(TransactionParser.parse("Received Rs 500 from Fixture","VM-FIXTUR-P")==null,"promotional header");
        check(TransactionParser.parse("Received Rs 500 from Fixture","+919876543210")==null,"personal number not verified bank movement");
        // Device 6 (masked): connector words, balance before payee, word references, TGB branch ledger.
        var transfer=TransactionParser.parse("Your a/c no. XXXXXXXX1234 is debited for Rs.500.00 on 12-10-25 and credited to a/c no. XXXXXXXX0025 (UPI Ref no 512345678901)-TGB","JX-TGBEBK-S");
        check(transfer!=null&&transfer.direction().equals("debit")&&transfer.paise()==50000&&transfer.merchant().equals("To a/c ••0025")&&transfer.nameSource().equals("Masked account only")&&transfer.reference().equals("512345678901"),"'and credited to a/c' is a masked account, never the name 'and'");
        var balanceFirst=TransactionParser.parse("IDBI Bank Acct XX123 debited for Rs 120 on 05-May-25; Bal Rs 2,310.45 RAMESH K credited. UPI:512345678901","VM-IDBIBK");
        check(balanceFirst!=null&&balanceFirst.merchant().equals("RAMESH K")&&balanceFirst.paise()==12000,"balance before payee is not part of the name");
        check(TransactionParser.parse("IDBI Bank Acct XX123 debited for Rs 45.50 on 05-Jul-25; Bal Rs 310.45 12345 SAMPLE PH credited. UPI:512345678901","VM-IDBIBK").merchant().equals("12345 SAMPLE PH"),"store-coded payee after balance");
        check(TransactionParser.parse("Your a/c no. XXXXXXXXXXXX1234 is debited for Rs.75.00 on 12-10-25 and credited to a/c no. XXXXXXXXXX0007 (UPI Ref no 512345678901).To block UPI services of IDBI Bank, Send SMS as UPIBLOCK to 9999 - IDBI BANK","VM-IDBIBK").merchant().equals("To a/c ••0007"),"'To block UPI' footer is not a payee");
        check(TransactionParser.parse("A/c XX1234 Debited with Rs. 30.00 on 18-07-20 by UCO-UPI.Avl Bal Rs.0.10.For disputed txn reporting,click https://example.invalid/x","JD-UCOBNK").reference().isEmpty(),"a dictionary word is not a transaction reference");
        var ledgerDebit=TransactionParser.parse("Your Account XXXX1234 had a TO TRANSFER of RS. 500.00 DR on 12/10/25 at 10:15:30Current balance is Rs. 2,310.45 CR TGB BRANCH","JX-TGBTXN-S");
        check(ledgerDebit!=null&&ledgerDebit.direction().equals("debit")&&ledgerDebit.paise()==50000&&ledgerDebit.format().equals("core-ledger")&&ledgerDebit.merchant().equals("Unknown merchant"),"TGB branch-ledger debit");
        var ledgerCredit=TransactionParser.parse("Your Account XXXX1234 had a By Transfer of RS. 900.00 CR on 12/10/25 at 10:15:30Current balance is Rs. 3,210.45 CR TGB BRANCH","CP-TGBTXN-S");
        check(ledgerCredit!=null&&ledgerCredit.direction().equals("credit")&&ledgerCredit.paise()==90000,"TGB branch-ledger credit from a trusted header");
        check(TransactionParser.parse("Your Account XXXX1234 had a ATM WDL transaction of Rs. 2,000 on 12/10/25 at 10:15:30From cardno.#####1234Current balance is Rs. 310.45 CR TGB 1234","JX-TGBTXN-S").type().equals("Cash withdrawal"),"TGB ATM withdrawal");
        check(TransactionParser.parse("IDBI Bank A/c NN1234 debited for INR 2,000; ATM WDL. Bal INR 310.45 as of 12OCT 10:15hrs.","VM-IDBIBK").type().equals("Cash withdrawal"),"IDBI ATM WDL");
        check(TransactionParser.parse("Your Account XXXXX1234 has been credited by Rs.300 on12/10/25 at 10:15:30 Current balance is Rs.3,000 Info: UPI 512345678901 sample@axl TGB","JK-TGBBNK-S")!=null,"TGB credit is trusted bank income");
        check(TransactionParser.parse("Your Account XXXX1234 had a By Transfer of RS. 900.00 CR on 12/10/25","AX-UNKNWN-S")==null,"ledger credit from an unknown header stays untrusted");
        System.out.println("Parser checks passed, including research regressions");
    }
}
