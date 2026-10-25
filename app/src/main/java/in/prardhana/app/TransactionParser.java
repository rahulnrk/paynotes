package in.prardhana.app;

import java.util.Locale;
import java.util.regex.*;

/** Conservative local extraction. Ambiguous messages are deliberately ignored. */
public final class TransactionParser {
    public static final int VERSION=8;
    /** format is "core-ledger" for branch-ledger copies (e.g. TGB "TO TRANSFER … DR") that repeat a UPI alert without its reference. */
    public record Parsed(long paise, String direction, String merchant, String category, String reference, String type, String channel, String vpa, String nameSource, String format) {}
    public static Parsed parse(String text) {
        if (text == null) return null;
        String s = text.toLowerCase(Locale.ROOT);
        if(s.matches("(?s).*\\b(?:can be credited|welcome bonus|free cash|free rs|get upto|get instant|avail rs|pass code|use code|rummy|added to your wallet|credited to your vi number|received as repayment)\\b.*"))return null;
        if(s.matches("(?s).*\\bipo\\b.*\\b(?:app|application|bid)\\b.*")||s.matches("(?s).*\\bmin\\.?\\s*purchase\\b.*")||s.matches("(?s).*\\d+%\\s*off\\b.*"))return null;
        if(s.matches("(?s).*\\b(?:get up to|approval up to|apply now|instant loan|zero documentation|minimum purchase|coupon|voucher|net position|not debited|could not be processed|cbs rejection|will be refunded|refund initiated|will be presented|to be presented|khatabook)\\b.*"))return null;
        if(s.matches("(?s).*\\byour beneficiary\\b.*\\b(?:received|credited)\\b.*"))return null;
        // Never substitute an INR balance/limit for an unsupported foreign-currency purchase.
        if(s.matches("(?s).*\\b(?:usd|eur|gbp|aed|sgd|aud|jpy)\\s*[0-9].*"))return null;
        if (s.matches("(?s).*(otp|one.time password|verification code|payment request|collect request|due on|payment due|scheduled|failed|declined|unsuccessful|reversed).*")) return null;
        if(s.matches("(?s).*\\b(?:(?:is|amount|total|minimum|bill)\\s+due|(?:if|already)\\s+paid|ignore\\s+if\\s+already\\s+paid)\\b.*"))return null;
        boolean debit = s.matches("(?s).*\\b(debited|paid|spent|sent|purchase|withdrawn)\\b.*");
        boolean cardMandate=s.matches("(?s).*\\bpayment\\b.*\\bprocessed successfully\\b.*\\b(?:debit|credit) card\\b.*");
        debit=debit||cardMandate;
        debit=debit||s.matches("(?s).*\\b(?:used|charged)\\b.{0,60}\\bcard\\b.*")||s.matches("(?s).*\\bcard\\b.{0,60}\\b(?:used|charged)\\b.*");
        boolean credit = s.matches("(?s).*\\b(credited|received|refunded)\\b.*");
        Matcher ownAccount=Pattern.compile("(?i)\\b(?:your\\s+)?(?:a\\s*/\\s*c|account|acct|acc|ac)\\b[^\\n]{0,45}?\\b(debited|credited)\\b").matcher(text);
        boolean own=ownAccount.find();
        boolean receipt=!own&&!cardMandate&&s.matches("(?s).*\\b(?:bill\\s+payment|payment\\s+(?:of|for|towards)|recharge|purchase)\\b.*")&&s.matches("(?s).*\\b(?:successful|received|receipt|acknowledged|bill for)\\b.*")&&!s.matches("(?s).*\\b(?:refunded|refund|cashback)\\b.*");
        if(receipt){debit=true;credit=false;}
        if(debit&&credit&&own){debit=ownAccount.group(1).equalsIgnoreCase("debited");credit=!debit;}
        // Branch-ledger dialect: direction is only the trailing DR/CR (or the transfer wording).
        Matcher ledger=Pattern.compile("(?i)\\baccount\\s+[x*]+[0-9]{2,6}\\s+had\\s+an?\\s+(to transfer|by transfer|bulk posting|atm wdl)\\b[^\\n]{0,30}?\\bof\\s+rs\\.?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s*(dr|cr)?\\b").matcher(text);
        boolean coreLedger=!receipt&&ledger.find();
        if(coreLedger){
            String side=ledger.group(2)==null?"":ledger.group(2).toLowerCase(Locale.ROOT),kind=ledger.group(1).toLowerCase(Locale.ROOT);
            debit=side.equals("dr")||side.isEmpty()&&(kind.equals("to transfer")||kind.equals("atm wdl"));credit=side.equals("cr")||side.isEmpty()&&kind.equals("by transfer");own=true;
        }
        if(s.matches("(?s).*(?:will be|to be)\\s+(?:debited|credited).*"))return null;
        if(credit&&!debit&&!own&&s.matches("(?s).*(talktime|main balance|recharge of|recharge successful|recharge is successful).*"))return null;
        if(credit&&!own&&s.matches("(?s).*\\brecharge\\b.*")&&!s.matches("(?s).*\\b(?:refund|refunded)\\b.*"))return null;
        if(!receipt&&!own&&s.matches("(?s).*\\b(?:received|receipt|thank you)\\b.*\\b(?:your loan|your credit card|proposal|premium receipt)\\b.*"))return null;
        if(!own&&credit&&s.matches("(?s).*\\b(?:proposal|premium receipt)\\b.*"))return null;
        if(credit&&!own&&!s.contains("credited")&&!s.contains("refunded")&&!s.matches("(?s).*(?:received\\s+(?:a\\s+)?(?:payment\\s+of\\s+)?(?:rs\\.?|inr|₹)|(?:rs\\.?|inr|₹)\\s*[0-9,.]+\\s+(?:has been\\s+)?received).*"))return null;
        if (debit == credit) return null;
        long paise=movementAmount(text,s);
        if(paise==0){
            // UPI bank-alert dialect: currency is implicit, never inferred from an arbitrary number.
            if(!own||!s.matches("(?s).*\\bupi\\b.*")||!s.matches("(?s).*\\b(?:refno|rrn|utr|reference\\s*(?:no|number)?)\\s*[:#.-]?\\s*[a-z0-9]{6,}.*"))return null;
            Matcher implicit=Pattern.compile("(?i)\\b(?:debited|credited)\\s+(?:by|with|for)\\s+([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?=\\s|[.;]|$)").matcher(text);
            if(!implicit.find())return null;
            paise=paise(implicit.group(1));
        }
        if (paise <= 0) return null;
        // "To block UPI …" is a security footer, not a payee.
        Matcher merchant = Pattern.compile("(?i)\\b(?:to|at|from)\\s+(?!block\\b)([a-z][a-z0-9 &@._-]{1,45}?)(?=\\s+(?:on|via|using|ref(?:no|erence)?|rrn|utr|if|txn|upi|a/c|avl|bal|available|limit)\\b|[;\\n]|$)").matcher(text);
        String name = merchant.find() ? merchant.group(1).trim() : "Unknown merchant";
        if(debit){Matcher payee=Pattern.compile("(?i)\\bto\\s+([\\p{L}][\\p{L}\\p{N} &@._'/-]{1,60}?)\\s+(?:on|ref(?:no|erence)?|rrn|utr)\\b").matcher(text);if(payee.find()&&!payee.group(1).toLowerCase(Locale.ROOT).matches("(?:your|a/c|account|acct).*"))name=payee.group(1).trim();}
        if(debit){
            // "… on 12-10-25 and credited to a/c …" names no payee; connector words are never names.
            Matcher beneficiary=Pattern.compile("(?i)\\bon\\s+\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}[.\\s,;:-]+(?!(?:and|is|has|been|to|the)\\b)([\\p{L}][\\p{L}\\p{N} &@._'/-]{1,60}?)\\s+(?:is\\s+)?credited\\b").matcher(text);
            if(beneficiary.find())name=beneficiary.group(1).trim();
            // IDBI places the balance before the payee: "; Bal Rs 2,310.45 NAME credited".
            Matcher semicolon=Pattern.compile("(?i);\\s*(?:(?:avl\\.?\\s*)?bal(?:ance)?\\s*(?:rs\\.?|inr\\.?)\\s*-?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?\\s+)?(?!(?:avl\\.?\\s*)?bal(?:ance)?\\b)([\\p{L}\\p{N}][\\p{L}\\p{N} &@._'/-]{1,60}?)\\s+(?:is\\s+)?credited\\b").matcher(text);if(semicolon.find()&&semicolon.group(1).matches(".*\\p{L}.*"))name=semicolon.group(1).trim();
        }
        name = name.replaceAll("(?i)\\b(?:private limited|pvt ltd|payments pvt)\\b", "").replaceAll("[.;,]+$","").trim();
        Matcher vpaMatch=Pattern.compile("(?i)\\b([a-z0-9][a-z0-9._-]{1,100}@[a-z][a-z0-9]{1,40})(?![a-z0-9.])").matcher(text);
        String vpa=vpaMatch.find()?vpaMatch.group(1).toLowerCase(Locale.ROOT):"";
        if(name.contains("@")||name.matches("(?i)(?:your|our|a/c|account|bank|balance|your account).*"))name="Unknown merchant";
        if(coreLedger)name="Unknown merchant";
        boolean maskedPayee=false;
        if(debit&&name.equals("Unknown merchant")){Matcher toAccount=Pattern.compile("(?i)\\bcredited\\s+to\\s+(?:a\\s*/\\s*c|acct|account)\\s*(?:no\\.?)?\\s*[x*]+([0-9]{3,6})\\b").matcher(text);if(toAccount.find()){name="To a/c ••"+toAccount.group(1);maskedPayee=true;}}
        boolean explicitCard=s.matches("(?s).*\\b(?:spent|used|purchase|transaction|txn|debited|paid)\\b.{0,60}\\b(?:on|using|with)\\b.{0,35}\\b(?:credit\\s+|debit\\s+)?card\\b.*")||s.matches("(?s).*\\bcard\\b.{0,50}\\b(?:spent|used|purchase|debited|charged)\\b.*");
        boolean atmCash=s.matches("(?s).*\\b(?:atm\\s+cash|atm\\s+wdl|cash\\s+withdrawal|cash\\s+withdrawn|withdrawn\\s+at\\s+atm)\\b.*")||(s.contains("withdrawn")&&s.contains("atm"));
        String channel=receipt?"Unspecified · receipt":atmCash?"ATM":s.matches("(?s).*\\bupi[a-z0-9]*\\b.*")||!vpa.isEmpty()?"UPI":s.contains("neft")?"NEFT":s.contains("imps")?"IMPS":s.contains("rtgs")?"RTGS":explicitCard||cardMandate?"Card":"Bank / unspecified";
        String type=credit?"Money received":"Payment";
        String category = credit?"Money received":categoryFor(name);
        if(credit&&s.matches("(?s).*\\b(?:refund|refunded)\\b.*")){type="Refund";category="Refund";}
        else if(credit&&s.matches("(?s).*\\bsalary\\b.*")){type="Salary";category="Income";}
        else if(credit&&s.matches("(?s).*\\binterest\\b.*")){type="Interest";category="Interest";}
        else if(credit&&s.matches("(?s).*\\bcashback\\b.*")){type="Cashback";category="Cashback";}
        else if(debit&&channel.equals("ATM")){type="Cash withdrawal";category="Cash withdrawal";}
        else if(debit&&s.matches("(?s).*\\b(?:charges|fee)\\b.*")){type="Bank fee";category="Fees";}
        if(receipt){type="Payment acknowledgement";category="Payment receipt";name="Unknown merchant";}
        // A reference contains a digit: "For disputed txn reporting" must not yield "REPORTING".
        Matcher ref = Pattern.compile("(?i)\\b(?:utr|rrn|upiref|refno|ref(?:erence)?(?:\\s*(?:no\\.?|number))?|txn(?:\\s*id)?|transaction\\s*id)\\s*[:#./-]?\\s*((?=[a-z]*[0-9])[a-z0-9]{6,})").matcher(text);
        String reference=ref.find()?ref.group(1).toUpperCase(Locale.ROOT):"";
        if(reference.isEmpty()){Matcher railRef=Pattern.compile("(?i)\\bUPI[/: -]+(?:(?:DR|CR|P2A|P2M)[/: -]+)?([0-9]{10,16})\\b").matcher(text);if(railRef.find())reference=railRef.group(1);}
        return new Parsed(paise, credit ? "credit" : "debit", name, category, reference,type,channel,vpa,maskedPayee?"Masked account only":name.equals("Unknown merchant")?(vpa.isEmpty()?"Missing in message":"UPI ID only"):"Message text",coreLedger?"core-ledger":"");
    }
    // A whole monetary token: "INR 10.999" never shortens to INR 10, and "Rs 5L" is not 5 rupees.
    private static final Pattern MONEY=Pattern.compile("(?i)(?:₹|rs\\.?|inr\\.?)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9]|\\.[0-9]|[lL](?:akh)?\\b)");
    private static final Pattern MALFORMED=Pattern.compile("(?i)(?:₹|rs\\.?|inr\\.?)\\s*[0-9][0-9,]*\\.[0-9]{3,}");
    private static final Pattern STATE_ROLE=Pattern.compile("(?:\\bbal(?:ance)?|\\bavl|\\bavbl|\\bavail(?:able)?|\\blimit|\\blmt|\\boutstanding|\\bo/s|\\bup\\s*to|\\bworth|\\beligible|\\bwin)\\b[^0-9\\n]{0,20}$");
    private static final Pattern VERB=Pattern.compile("\\b(?:debited|credited|paid|spent|sent|withdrawn|received|refunded|purchase|charged|transferred|debit|credit|payment|txn|transaction|dr|cr)\\b");
    /**
     * The executed movement's amount in paise: 0 when no currency amount describes a movement, -1 to abstain.
     * Balances and limits are states, not movements. Several distinct candidates must be bound to the movement verb.
     */
    static long movementAmount(String text,String lower){
        if(MALFORMED.matcher(text).find())return -1;
        Matcher m=MONEY.matcher(text);java.util.Set<Long> all=new java.util.LinkedHashSet<>(),bound=new java.util.LinkedHashSet<>();
        while(m.find()){
            String before=lower.substring(Math.max(0,m.start()-30),m.start()),near=lower.substring(Math.max(0,m.start()-20),m.start());
            if(STATE_ROLE.matcher(before).find())continue;
            long value=paise(m.group(1));if(value<=0)return -1;all.add(value);
            String after=lower.substring(m.end(),Math.min(lower.length(),m.end()+25));
            if(VERB.matcher(near).find()||VERB.matcher(after).find())bound.add(value);
        }
        if(all.isEmpty())return 0;
        if(all.size()==1)return all.iterator().next();
        return bound.size()==1?bound.iterator().next():-1;
    }
    private static long paise(String amount){
        try{return new java.math.BigDecimal(amount.replace(",","")).movePointRight(2).longValueExact();}catch(RuntimeException e){return -1;}
    }
    public static Parsed parse(String text,String sender){
        if(sender!=null&&sender.toUpperCase(Locale.ROOT).matches("[A-Z]{2}-[A-Z0-9]{6}-P")){
            if(Issuer.header(sender).equals("650022")&&text!=null&&text.toLowerCase(Locale.ROOT).contains("your prepaid bill payment")){Parsed receipt=parse(text);if(receipt!=null&&receipt.category().equals("Payment receipt"))return receipt;}
            return null;
        }
        if(sender!=null&&sender.replaceAll("[+\\s()-]","").matches("[0-9]{8,15}"))return null;
        Parsed p=parse(text);
        // A provider acknowledgement or wallet promotion must never invent bank income.
        if(p!=null&&p.direction().equals("credit")&&sender!=null&&!Issuer.trusted(sender))return null;
        return p;
    }
    public static String categoryFor(String name){
        String m=name.toLowerCase(Locale.ROOT);
        if(m.matches(".*\\b(swiggy|zomato|cafe|restaurant|coffee)\\b.*"))return "Food & drink";
        if(m.matches(".*\\b(uber|ola|metro|irctc|petrol)\\b.*"))return "Transport";
        if(m.matches(".*\\b(amazon|flipkart|myntra)\\b.*"))return "Shopping";
        if(m.matches(".*\\b(netflix|spotify|youtube)\\b.*"))return "Entertainment";
        if(m.matches(".*\\b(bigbasket|blinkit|zepto|grocery)\\b.*"))return "Groceries";
        return "Other";
    }
}
