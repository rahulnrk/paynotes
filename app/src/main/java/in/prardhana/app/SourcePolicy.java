package in.prardhana.app;
import java.util.Set;
final class SourcePolicy {
    private static final Set<String> SMS=Set.of("com.google.android.apps.messaging","com.android.mms","com.samsung.android.messaging");
    private static final Set<String> BANK_APPS=Set.of("com.csam.icici.bank.imobile","com.sbi.lotusintouch","com.msf.kbank.mobile","com.axis.mobile","com.idfcfirstbank.optimus","com.snapwork.hdfc");
    static boolean smsApp(String source){return SMS.contains(source);}
    static TransactionParser.Parsed parse(String text,String sender,String source){
        TransactionParser.Parsed p=TransactionParser.parse(text,sender);
        if(p!=null&&p.direction().equals("credit")&&!Set.of("sms","manual","sample").contains(source)&&!BANK_APPS.contains(source)&&!(smsApp(source)&&Issuer.trusted(sender)))return null;
        return p;
    }
}
