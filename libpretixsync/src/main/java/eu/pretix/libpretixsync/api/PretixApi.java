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
import okhttp3.RequestBody;
import okhttp3.Response;

public class PretixApi {
    /**
     * See https://docs.pretix.eu/en/latest/api/index.html for API documentation
     */

    public static final int SUPPORTED_API_VERSION = 4;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private String url;
    private String eventSlug;
    private String key;
    private int version;
    private OkHttpClient client;
    private SentryInterface sentry;

    public class ApiResponse {
        private JSONObject data;
        private Response response;

        public ApiResponse(JSONObject data, Response response) {
            this.data = data;
            this.response = response;
        }

        public JSONObject getData() {
            return data;
        }

        public Response getResponse() {
            return response;
        }
    }

    public PretixApi(String url, String key, String eventSlug, int version, HttpClientFactory httpClientFactory) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        this.url = url;
        this.key = key;
        this.eventSlug = eventSlug;
        this.version = version;
        this.client = httpClientFactory.buildClient();
        this.sentry = new DummySentryImplementation();
    }

    public static PretixApi fromConfig(ConfigStore config, HttpClientFactory httpClientFactory) {
        return new PretixApi(config.getApiUrl(), config.getApiKey(), config.getEventSlug(),
                config.getApiVersion(), httpClientFactory);
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

    public String eventResourceUrl(String resource) {
        try {
            return new URL(new URL(url), "events/" + eventSlug + "/" + resource + "/").toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ApiResponse postResource(String full_url, JSONObject data) throws ApiException {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .post(RequestBody.create(MediaType.parse("application/json"), data.toString()))
                .header("Authorization", "Token " + key);
        try {
            return apiCall(request.build());
        } catch (ResourceNotModified resourceNotModified) {
            resourceNotModified.printStackTrace();
            throw new ApiException("Resource not modified");
        }
    }

    public ApiResponse fetchResource(String full_url, String if_modified_since) throws ApiException, ResourceNotModified {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .header("Authorization", "Token " + key);
        if (if_modified_since != null) {
            request = request.header("If-Modified-Since", if_modified_since);
        }
        return apiCall(request.get().build());
    }

    public ApiResponse fetchResource(String full_url) throws ApiException, ResourceNotModified {
        return fetchResource(full_url, null);
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
    }

    private ApiResponse apiCall(Request request) throws ApiException, ResourceNotModified {
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
        } else if (response.code() == 304) {
            throw new ResourceNotModified();
        } else if (response.code() == 403) {
            response.close();
            throw new ApiException("Permission error, please try again or reset and reconfigure.");
        }
        try {
            return new ApiResponse(
                    new JSONObject(response.body().string()),
                    response
            );
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
