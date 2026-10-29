package in.prardhana.app;

import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class AiClient {
    static String chat(String endpoint,String token,JSONObject payload) throws Exception {return chatResponse(endpoint,token,payload).getString("reply");}
    static JSONObject chatResponse(String endpoint,String token,JSONObject payload) throws Exception {
        URL configured=new URL(endpoint);
        if(!configured.getProtocol().equals("https")||configured.getUserInfo()!=null||configured.getHost().isBlank()||configured.getQuery()!=null||configured.getRef()!=null)
            throw new IllegalArgumentException("An HTTPS AI server is required");
        URL url=new URL(configured,"/chat");
        HttpsURLConnection conn=(HttpsURLConnection)url.openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+token);conn.setDoOutput(true);
        try {
            try(var out=conn.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            if(conn.getResponseCode()!=200)throw new IllegalStateException("AI connection unavailable");
            try(var in=conn.getInputStream();var bytes=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[2048];int n;
                while((n=in.read(buffer))!=-1){if(bytes.size()+n>32768)throw new IllegalStateException("Response too large");bytes.write(buffer,0,n);}
                JSONObject result=new JSONObject(bytes.toString("UTF-8"));String reply=result.getString("reply");
                if(reply.isBlank()||reply.length()>6000)throw new IllegalStateException("Invalid reply");
                if(result.optJSONObject("proposal")!=null)CoachProposal.validate(result.getJSONObject("proposal"));
                return result;
            }
        } finally {conn.disconnect();}
    }
    static Map<String,String> explain(String endpoint,String token,JSONObject payload) throws Exception {
        URL url=new URL(endpoint);
        if(!url.getProtocol().equals("https") || url.getUserInfo()!=null || url.getHost().isBlank() || url.getQuery()!=null || url.getRef()!=null)
            throw new IllegalArgumentException("Use an HTTPS endpoint without credentials, query parameters, or a fragment.");
        HttpsURLConnection conn=(HttpsURLConnection)url.openConnection();
        conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+token);conn.setDoOutput(true);
        try {
            try(var stream=conn.getOutputStream()){stream.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int status=conn.getResponseCode();
            if(status!=200)throw new IllegalStateException(status==401?"Pilot access token was rejected.":status==503?"The AI service is not configured yet.":"AI service could not complete the request (HTTP "+status+").");
            byte[] bytes;try(var stream=conn.getInputStream();var buffer=new java.io.ByteArrayOutputStream()){byte[] chunk=new byte[2048];int n;while((n=stream.read(chunk))!=-1){buffer.write(chunk,0,n);if(buffer.size()>32768)throw new IllegalStateException("AI response exceeded the size limit.");}bytes=buffer.toByteArray();}
            if(bytes.length>32768)throw new IllegalStateException("AI response exceeded the size limit.");
            JSONArray explanations=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).getJSONArray("explanations");
            Set<String> valid=new HashSet<>();JSONArray findings=payload.getJSONArray("findings");for(int i=0;i<findings.length();i++)valid.add(findings.getJSONObject(i).getString("id"));
            Map<String,String> result=new LinkedHashMap<>();
            for(int i=0;i<explanations.length();i++){JSONObject item=explanations.getJSONObject(i);String id=item.getString("id"),text=item.getString("text");if(!valid.contains(id)||result.containsKey(id)||text.isBlank()||text.length()>1200)throw new IllegalStateException("AI returned an invalid explanation.");result.put(id,text);}
            if(result.size()!=valid.size())throw new IllegalStateException("AI returned incomplete explanations. Please try again.");
            return result;
        } finally {conn.disconnect();}
    }
}
