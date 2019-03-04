package eu.pretix.libpretixsync.api;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import eu.pretix.libpretixsync.config.ConfigStore;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class PretixApi {
    /**
     * See https://docs.pretix.eu/en/latest/api/index.html for API documentation
     */

    public static final int SUPPORTED_API_VERSION = 4;
    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private String url;
    private String eventSlug;
    private String orgaSlug;
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

    public PretixApi(String url, String key, String orgaSlug, String eventSlug, int version, HttpClientFactory httpClientFactory) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        this.url = url;
        this.key = key;
        this.eventSlug = eventSlug;
        this.orgaSlug = orgaSlug;
        this.version = version;
        this.client = httpClientFactory.buildClient();
        this.sentry = new DummySentryImplementation();
    }

    public static PretixApi fromConfig(ConfigStore config, HttpClientFactory httpClientFactory) {
        return new PretixApi(config.getApiUrl(), config.getApiKey(), config.getOrganizerSlug(),
                config.getEventSlug(), config.getApiVersion(), httpClientFactory);
    }

    public static PretixApi fromConfig(ConfigStore config) {
        return PretixApi.fromConfig(config, new DefaultHttpClientFactory());
    }

    public ApiResponse redeem(String secret, Date datetime, boolean force, String nonce, List<TicketCheckProvider.Answer> answers, Long listId, boolean ignore_unpaid, boolean pdf_data) throws ApiException, JSONException {
        JSONObject body = new JSONObject();
        if (datetime != null) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.ENGLISH); // Quoted "Z" to indicate UTC, no timezone offset
            df.setTimeZone(tz);
            body.put("datetime", df.format(datetime));
        }
        body.put("force", force);
        body.put("ignore_unpaid", ignore_unpaid);
        body.put("nonce", nonce);
        JSONObject answerbody = new JSONObject();
        if (answers != null) {
            for (TicketCheckProvider.Answer a : answers) {
                answerbody.put("" + a.getQuestion().getServer_id(), a.getValue());
            }
        }
        body.put("answers", answerbody);
        body.put("questions_supported", true);
        String pd = "";
        if (pdf_data) {
            pd = "?pdf_data=true";
        }
        return postResource(eventResourceUrl("checkinlists/" + listId + "/positions/" + secret + "/redeem") + pd, body);
    }

    public ApiResponse status(Long listId) throws ApiException {
        try {
            return fetchResource(eventResourceUrl("checkinlists/" + listId + "/status"));
        } catch (ResourceNotModified resourceNotModified) {
            throw new ApiException("invalid error");
        }
    }

    public ApiResponse search(Long listId, String query) throws ApiException {
        try {
            return fetchResource(eventResourceUrl("checkinlists/" + listId + "/positions") + "?search=" + URLEncoder.encode(query, "UTF-8"));
        } catch (ResourceNotModified | UnsupportedEncodingException resourceNotModified) {
            throw new ApiException("invalid error");
        }
    }

    public String organizerResourceUrl(String resource) {
        try {
            return new URL(new URL(url), "/api/v1/organizers/" + orgaSlug + "/" + resource + "/").toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String eventResourceUrl(String resource) {
        try {
            return new URL(new URL(url), "/api/v1/organizers/" + orgaSlug + "/events/" + eventSlug + "/" + resource + "/").toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ApiResponse deleteResource(String full_url) throws ApiException {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .delete()
                .header("Authorization", "Device " + key);
        try {
            return apiCall(request.build(), false);
        } catch (ResourceNotModified resourceNotModified) {
            resourceNotModified.printStackTrace();
            throw new ApiException("Resource not modified");
        }
    }

    public ApiResponse postResource(String full_url, JSONObject data) throws ApiException {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .post(RequestBody.create(MediaType.parse("application/json"), data.toString()))
                .header("Authorization", "Device " + key);
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
                .header("Authorization", "Device " + key);
        if (if_modified_since != null) {
            request = request.header("If-Modified-Since", if_modified_since);
        }
        return apiCall(request.get().build());
    }

    public ApiResponse fetchResource(String full_url) throws ApiException, ResourceNotModified {
        return fetchResource(full_url, null);
    }

    public ApiResponse downloadFile(String full_url) throws ApiException {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .header("Authorization", "Device " + key);
        try {
            return apiCall(request.build(), false);
        } catch (ResourceNotModified resourceNotModified) {
            resourceNotModified.printStackTrace();
            throw new ApiException("Resource not modified");
        }
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
    }

    private ApiResponse apiCall(Request request) throws ApiException, ResourceNotModified {
        return apiCall(request, true);
    }

    private ApiResponse apiCall(Request request, boolean json) throws ApiException, ResourceNotModified {
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
        String body = "";
        if (json) {
            try {
                body = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                throw new ApiException("Connection error.", e);
            }
        }

        if (response.code() >= 500) {
            response.close();
            throw new ApiException("Server error.");
        } else if (response.code() == 404 && (!json || !body.startsWith("{"))) {
            response.close();
            throw new ApiException("Server error: Resource not found.");
        } else if (response.code() == 304) {
            throw new ResourceNotModified();
        } else if (response.code() == 403) {
            response.close();
            throw new ApiException("Server error: Permission denied.");
        } else if (response.code() >= 405) {
            response.close();
            throw new ApiException("Server error: " + response.code() + ".");
        }
        try {
            if (json) {
                if (body.startsWith("[")) {
                    body = "{\"content\": " + body + "}";
                }
                return new ApiResponse(
                        new JSONObject(body),
                        response
                );
            } else {
                return new ApiResponse(
                        null,
                        response
                );
            }
        } catch (JSONException e) {
            e.printStackTrace();
            sentry.captureException(e);
            throw new ApiException("Invalid JSON received.", e);
        }
    }

}
