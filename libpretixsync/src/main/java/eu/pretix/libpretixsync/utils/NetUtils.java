package eu.pretix.libpretixsync.utils;

import java.net.MalformedURLException;
import java.net.URL;

public class NetUtils {

    public static String IPV4_REGEX = "^([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)$";
    public static String IPV6_REGEX = "^([0-9a-f]+):([0-9a-f:]+)$";

    public static boolean ignoreSSLforURL(String surl) {
        URL url = null;
        try {
            url = new URL(surl);
        } catch (MalformedURLException e) {
            return false;
        }
        return (url.getHost().matches(IPV6_REGEX) || url.getHost().matches(IPV4_REGEX));
    }
}
