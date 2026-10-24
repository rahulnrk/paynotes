package in.prardhana.app;
import java.util.*;
import java.util.regex.*;
/** Source context supports extraction; it is not authentication. */
public final class Issuer {
    private static final Map<String,String> BANKS=Map.ofEntries(Map.entry("SBIUPI","SBI"),Map.entry("CBSSBI","SBI"),Map.entry("SBIPSG","SBI"),Map.entry("SBIINB","SBI"),Map.entry("ICICIT","ICICI"),Map.entry("ICICIB","ICICI"),Map.entry("IDFCFB","IDFC"),Map.entry("IDBIBK","IDBI"),Map.entry("KOTAKB","Kotak"),Map.entry("UCOBNK","UCO"),Map.entry("HDFCBK","HDFC"),Map.entry("CANBNK","Canara"),Map.entry("AIRBNK","Airtel Payments Bank"),Map.entry("AXISBK","Axis"),Map.entry("YESBNK","YES Bank"),Map.entry("KVBANK","KVB"),Map.entry("KVBUPI","KVB"),Map.entry("PAYTMB","Paytm Payments Bank"),Map.entry("PNBSMS","PNB"),Map.entry("BOIIND","Bank of India"),Map.entry("UNIONB","Union Bank"),Map.entry("BOBSMS","Bank of Baroda"),Map.entry("TGBEBK","Telangana Grameena Bank"),Map.entry("TGBTXN","Telangana Grameena Bank"),Map.entry("TGBBNK","Telangana Grameena Bank"));
    public static String header(String sender){if(sender==null)return "";Matcher m=Pattern.compile("^[A-Z]{2}-([A-Z0-9]{6})(?:-[SPTG])?$").matcher(sender.toUpperCase(Locale.ROOT));return m.matches()?m.group(1):"";}
    public static boolean trusted(String sender){return BANKS.containsKey(header(sender));}
    public static String fromSender(String sender){return BANKS.getOrDefault(header(sender),"");}
    public static String inText(String text){for(String bank:List.of("Airtel Payments Bank","Paytm Payments Bank","ICICI","Kotak","IDFC","IDBI","UCO","SBI","HDFC","Canara","Axis","YES Bank","KVB","PNB","Bank of Baroda","Telangana Grameena Bank"))if(Pattern.compile("(?i)\\b"+Pattern.quote(bank)+"\\b").matcher(text).find())return bank;return "";}
}
