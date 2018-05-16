package eu.pretix.libpretixsync.api;

import eu.pretix.libpretixsync.check.TicketCheckProvider;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.net.ssl.SSLException;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.config.ConfigStore;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PretixApi {
    /**
     * See https://docs.pretix.eu/en/latest/api/index.html for API documentation
     */

    public static final int SUPPORTED_API_VERSION = 4;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private String url;
    private String key;
    private int version;
    private OkHttpClient client;
    private SentryInterface sentry;

    public PretixApi(String url, String key, int version, HttpClientFactory httpClientFactory) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        this.url = url;
        this.key = key;
        this.version = version;
        this.client = httpClientFactory.buildClient();
        this.sentry = new DummySentryImplementation();
    }

    public static PretixApi fromConfig(ConfigStore config, HttpClientFactory httpClientFactory) {
        return new PretixApi(config.getApiUrl(), config.getApiKey(), config.getApiVersion(),
                httpClientFactory);
    }

    public static PretixApi fromConfig(ConfigStore config) {
        return PretixApi.fromConfig(config, new DefaultHttpClientFactory());
    }

    public JSONObject redeem(String secret, List<TicketCheckProvider.Answer> answers, boolean ignore_unpaid) throws ApiException {
        return new JSONObject();
    }

    public JSONObject redeem(String secret, Date datetime, boolean force, String nonce, List<TicketCheckProvider.Answer> answers, boolean ignore_unpaid) throws ApiException {
        return new JSONObject();
    }

    public JSONObject status() throws ApiException {
        return new JSONObject();
    }

    public JSONObject search(String query) throws ApiException {
        return new JSONObject();
    }

    public JSONObject download() throws ApiException {
        return new JSONObject();
    }

    public String resourceUrl(String resource) {
        try {
            return new URL(new URL(url), resource).toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject fetchResource(String full_url) throws ApiException {
        Request request = new Request.Builder()
                .url(full_url)
                .header("Authorization", "Token " + key)
                .get()
                .build();
        return apiCall(request);
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
    }

    private JSONObject apiCall(Request request) throws ApiException {
        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (SSLException e) {
            e.printStackTrace();
            throw new ApiException("Error while creating a secure connection.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error.", e);
        }

        String safe_url = request.url().toString().replaceAll("^(.*)key=([0-9A-Za-z]+)([^0-9A-Za-z]*)", "$1key=redacted$3");
        sentry.addHttpBreadcrumb(safe_url, request.method(), response.code());

        if (response.code() >= 500) {
            response.close();
            throw new ApiException("Server error.");
        } else if (response.code() == 404) {
            response.close();
            throw new ApiException("Invalid configuration, please reset and reconfigure.");
        } else if (response.code() == 403) {
            response.close();
            throw new ApiException("Permission error, please try again or reset and reconfigure.");
        }
        try {
            return new JSONObject(response.body().string());
        } catch (JSONException e) {
            e.printStackTrace();
            sentry.captureException(e);
            throw new ApiException("Invalid JSON received.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error.", e);
        }
    }

}
