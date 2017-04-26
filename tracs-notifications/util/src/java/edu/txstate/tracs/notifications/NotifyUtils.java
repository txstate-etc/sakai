package edu.txstate.tracs.notifications;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Shared utility functions for the TRACS notification module
 */
public class NotifyUtils {
    private final Log log = LogFactory.getLog(NotifyUtils.class);
    private MessageDigest sha1;
    private SiteService siteService;
    public void setSiteService(SiteService siteService) {
      this.siteService = siteService;
    }
    private ServerConfigurationService serverConfigurationService;
    public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
      this.serverConfigurationService = serverConfigurationService;
    }

    public void init() {
      log.info("init()");
      try {
        sha1 = MessageDigest.getInstance("SHA-1");
      } catch (Exception e) {
        // won't happen
      }
    }

    public List<String> getAllUserIdsExcept(String siteid, String useridtoexclude) throws Exception {
      List<String> ret = new ArrayList<String>();
      Site site = siteService.getSite(siteid);
      for (Member m : site.getMembers()) {
        if (!m.getUserId().equals(useridtoexclude)) ret.add(m.getUserId());
      }
      return ret;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
      char[] hexChars = new char[bytes.length * 2];
      for ( int j = 0; j < bytes.length; j++ ) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = hexArray[v >>> 4];
        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
      }
      return new String(hexChars);
    }

    public Calendar translateDate(String dt) throws java.text.ParseException {
      Calendar cal = Calendar.getInstance();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSSZ");
      cal.setTime(sdf.parse(dt+"-0000"));
      return cal;
    }

    public String dateToJson(Calendar c) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
      return sdf.format(c.getTime());
    }

    public String hashContent(String title) {
      return hashContent(title, "", "");
    }
    public String hashContent(String title, String body) {
      return hashContent(title, body, "");
    }
    public String hashContent(String title, String body, String extra) {
      String total = title+":"+body+":"+extra;
      try {
        return bytesToHex(sha1.digest(total.getBytes("UTF-8")));
      } catch (Exception e) {
        e.printStackTrace();
        return "";
      }
    }

    protected String mapToParams(Map<String,String> params) throws Exception {
      List<String> pairs = new ArrayList<String>();
      for (Map.Entry<String,String> entry : params.entrySet()) {
        pairs.add(URLEncoder.encode(entry.getKey(), "UTF-8")+"="+URLEncoder.encode(entry.getValue(), "UTF-8"));
      }
      return StringUtils.join(pairs,"&");
    }

    protected HttpURLConnection getConnection() throws Exception {
      return getConnection(null);
    }
    protected HttpURLConnection getConnection(Map<String,String> params) throws Exception {
      String t = serverConfigurationService.getString("tracs.notifications.scheme", "https");
      t += "://"+serverConfigurationService.getString("tracs.notifications.host");
      t += serverConfigurationService.getString("tracs.notifications.path", "/notifications");
      if (params != null)
        t += "?"+mapToParams(params);
      URL url = new URL(t);
      HttpURLConnection http = (HttpURLConnection) url.openConnection();
      http.setRequestProperty("Notification-Token", serverConfigurationService.getString("tracs.notifications.secret", ""));
      return http;
    }

    public void sendNotification(String objecttype, String notifytype,
      String objectid, String siteid, List<String> userids, Calendar notifyafter,
      String contenthash) throws Exception {
      String after = dateToJson(notifyafter);

      String jsonBody = "[{";
      jsonBody += "\"notification_type\":\""+notifytype+"\",";
      jsonBody += "\"object_type\":\""+objecttype+"\",";
      jsonBody += "\"object_id\":\""+objectid+"\",";
      jsonBody += "\"context_id\":\""+siteid+"\",";
      jsonBody += "\"content_hash\":\""+contenthash+"\",";
      jsonBody += "\"notify_after\":\""+after+"\",";
      jsonBody += "\"user_ids\":[\""+StringUtils.join(userids, "\",\"")+"\"]";
      jsonBody += "}]";

      HttpURLConnection http = getConnection();
      http.setRequestMethod("POST");
      http.setDoOutput(true);
      http.setFixedLengthStreamingMode(jsonBody.getBytes().length);
      http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
      try (OutputStream os = http.getOutputStream()) {
        os.write(jsonBody.getBytes());
      }

      System.out.println(jsonBody);

    }

    public void deleteForSite(String siteid) {

    }
    public void deleteForUserInSite(String siteid, String userid) {

    }
    public void deleteForObject(String objecttype, String objectid) {

    }
}