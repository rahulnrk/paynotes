package in.prardhana.app;
import java.util.Locale;
import java.util.Set;
/** Which sources may create money movements. Chat, email and shopping notifications are written by people or marketers, never by a bank. */
final class SourcePolicy {
    private static final Set<String> SMS=Set.of("com.google.android.apps.messaging","com.android.mms","com.samsung.android.messaging");
    private static final Set<String> BANK_APPS=Set.of("com.csam.icici.bank.imobile","com.sbi.lotusintouch","com.msf.kbank.mobile","com.axis.mobile","com.idfcfirstbank.optimus","com.snapwork.hdfc");
    private static final Set<String> UPI_APPS=Set.of("com.google.android.apps.nbu.paisa.user","com.phonepe.app","net.one97.paytm","in.org.npci.upiapp");
    private static final Set<String> LOCAL=Set.of("sms","manual","sample");
    static boolean smsApp(String source){return SMS.contains(source);}
    static boolean allowed(String source){return LOCAL.contains(source)||smsApp(source)||BANK_APPS.contains(source)||UPI_APPS.contains(source);}
    record Notice(String text,String sender){}
    /**
     * What a posted notification contributes, or null to ignore it entirely.
     * An SMS-app notification is read as that SMS: body only, its title as the sender, and only for business headers.
     * When live SMS capture is on, the SMS receiver records the same message with its real sender, so the notification adds nothing.
     */
    static Notice notice(String pkg,String title,String body,boolean liveSms){
        if(LOCAL.contains(pkg)||!allowed(pkg))return null;
        if(!smsApp(pkg))return new Notice(title+"\n"+body,null);
        if(liveSms)return null;
        String sender=smsSender(title);return sender.isEmpty()?null:new Notice(body,sender);
    }
    /** "AX-HDFCBK", "AX-HDFCBK-S", or a bare known bank header such as "HDFCBK". Contact names and phone numbers are personal messages. */
    static String smsSender(String title){
        String t=title==null?"":title.trim();
        if(!Issuer.header(t).isEmpty())return t.toUpperCase(Locale.ROOT);
        return t.matches("[A-Z0-9]{6}")&&Issuer.trusted("XX-"+t)?"XX-"+t:"";
    }
    static TransactionParser.Parsed parse(String text,String sender,String source){
        if(!allowed(source))return null;
        TransactionParser.Parsed p=TransactionParser.parse(text,sender);
        if(p!=null&&p.direction().equals("credit")&&!LOCAL.contains(source)&&!BANK_APPS.contains(source)&&!(smsApp(source)&&Issuer.trusted(sender)))return null;
        return p;
    }
}
