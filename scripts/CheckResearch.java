import in.prardhana.app.TransactionParser;

/** Synthetic, non-personal probes of research claims; prints classifications only. */
public class CheckResearch {
    public static void main(String[] args) {
        String[][] cases = {
            {"Loan advertisement", "Get up to Rs.20000 credited to your account. Fast approval. Apply now"},
            {"Negated debit", "Your account is not debited with Rs 130 towards Google due to cbs rejection"},
            {"ICICI dual movement", "ICICI Bank Acct XX123 debited for Rs 250.00 on 12-Sep-25; SAMPLE SHOP credited. UPI:512345678901"},
            {"Kotak counterparty", "Sent Rs.250 from Kotak Bank A/c XX123 to SAMPLE SHOP on 12-09-25 UPI Ref 512345678901"},
            {"Beneficiary received confirmation", "Your beneficiary SAMPLE NAME has received Rs.5000 transferred via NEFT UTR ABC123456"},
            {"INR dot interest", "Monthly interest of INR.123 earned on your Savings A/c XX123 has been credited"},
            {"Future refund", "Your full IRCTC fare of Rs 680 will be refunded in 7 days"},
            {"Completed card mandate", "Your Payment of Rs 1240.91 at SAMPLE CLOUD for e-mandate SiHubId X has been processed successfully on your Debit Card ending 1234"},
            {"Future presentment", "EMI overdue of Rs.7315 on your loan a/c XX123 will be presented via NACH on 9th Feb 2026. Sent as per request"}
        };
        for(String[] item:cases){
            var p=TransactionParser.parse(item[1]);
            System.out.println(item[0]+": "+(p==null?"REJECTED":p.direction()+" / "+p.type()+" / "+p.channel()+" / name="+p.merchant()));
        }
    }
}
