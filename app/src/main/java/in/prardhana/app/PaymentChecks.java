package in.prardhana.app;
import java.util.*;
/** Review prompts, never fraud verdicts and never automatic deletion. */
public final class PaymentChecks {
    public record Movement(String id,String issuer,String account,String merchant,String reference,long amount,long time){}
    public record Flag(String firstId,String secondId,String explanation){}
    public static List<Flag> possibleDoublePayments(List<Movement> input){
        Map<String,Movement> last=new HashMap<>();List<Flag> flags=new ArrayList<>();List<Movement> sorted=new ArrayList<>(input);sorted.sort(Comparator.comparingLong(Movement::time));
        for(Movement m:sorted){if(m.issuer.isEmpty()||m.account.isEmpty()||m.reference.isEmpty()||m.merchant.equals("Unknown merchant")||m.merchant.isBlank())continue;
            String key=m.issuer+"|"+m.account+"|"+m.merchant.toLowerCase(Locale.ROOT)+"|"+m.amount;Movement old=last.put(key,m);
            if(old!=null&&!old.reference.equalsIgnoreCase(m.reference)&&m.time-old.time<=120000&&m.time>old.time)flags.add(new Flag(old.id,m.id,"Two equal payments to this payee within two minutes, with different references. These may be separate purchases. Check your bank before reporting a duplicate charge."));
        }return flags;
    }
}
