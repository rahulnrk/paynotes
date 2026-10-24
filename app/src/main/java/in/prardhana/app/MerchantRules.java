package in.prardhana.app;
import android.content.Context;
import org.json.*;
import java.util.Locale;
final class MerchantRules {
    static String key(String vpa,String raw){if(!vpa.isEmpty())return "vpa:"+vpa.toLowerCase(Locale.ROOT);if(raw.isBlank()||raw.equals("Unknown merchant"))return "";return "name:"+raw.trim().toLowerCase(Locale.ROOT);}
    static JSONObject rules(Context c){try{return new JSONObject(Store.prefs(c).getString("merchant_rules","{}"));}catch(JSONException e){return new JSONObject();}}
    static JSONObject find(Context c,String vpa,String raw){String key=key(vpa,raw);return key.isEmpty()?null:rules(c).optJSONObject(key);}
    static String scoped(String vpa,String raw,String issuer,String source,String channel,String direction){String identity=key(vpa,raw);return identity.isEmpty()?"":identity+"|"+issuer+"|"+source+"|"+channel+"|"+direction;}
    static JSONObject find(Context c,String vpa,String raw,String issuer,String source,String channel,String direction){String k=scoped(vpa,raw,issuer,source,channel,direction);JSONObject r=k.isEmpty()?null:rules(c).optJSONObject(k);return r!=null?r:!vpa.isEmpty()?find(c,vpa,raw):null;}
    static void remember(Context c,String vpa,String raw,String name,String category,String issuer,String source,String channel,String direction){String k=scoped(vpa,raw,issuer,source,channel,direction);if(k.isEmpty())return;try{JSONObject r=rules(c);r.put(k,new JSONObject().put("name",name).put("category",category));Store.prefs(c).edit().putString("merchant_rules",r.toString()).apply();}catch(JSONException e){throw new IllegalStateException(e);}}
    static void remember(Context c,String vpa,String raw,String name,String category){String key=key(vpa,raw);if(key.isEmpty())return;try{JSONObject rules=rules(c);rules.put(key,new JSONObject().put("name",name).put("category",category));Store.prefs(c).edit().putString("merchant_rules",rules.toString()).apply();}catch(JSONException e){throw new IllegalStateException(e);}}
}
