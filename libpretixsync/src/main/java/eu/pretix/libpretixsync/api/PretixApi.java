package eu.pretix.libpretixsync.api;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.db.QueuedCheckIn;
import eu.pretix.libpretixsync.utils.NetUtils;
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
        this.client = httpClientFactory.buildClient(NetUtils.ignoreSSLforURL(url));
        this.sentry = new DummySentryImplementation();
    }

    public String getEventSlug() {
        return eventSlug;
    }

    public void setEventSlug(String eventSlug) {
        this.eventSlug = eventSlug;
    }

    public static PretixApi fromConfig(ConfigStore config, HttpClientFactory httpClientFactory) {
        return new PretixApi(config.getApiUrl(), config.getApiKey(), config.getOrganizerSlug(),
                config.getEventSlug(), config.getApiVersion(), httpClientFactory);
    }

    public static PretixApi fromConfig(ConfigStore config) {
        return PretixApi.fromConfig(config, new DefaultHttpClientFactory());
    }

    public ApiResponse redeem(String secret, Date datetime, boolean force, String nonce, List<TicketCheckProvider.Answer> answers, Long listId, boolean ignore_unpaid, boolean pdf_data) throws ApiException, JSONException {
        String dt = null;
        if (datetime != null) {
            dt = QueuedCheckIn.formatDatetime(datetime);
        }
        return redeem(secret, dt, force, nonce, answers, listId, ignore_unpaid, pdf_data);
    }

    public ApiResponse redeem(String secret, String datetime, boolean force, String nonce, List<TicketCheckProvider.Answer> answers, Long listId, boolean ignore_unpaid, boolean pdf_data) throws ApiException, JSONException {
        JSONObject body = new JSONObject();
        if (datetime != null) {
            body.put("datetime", datetime);
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
        body.put("canceled_supported", true);
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
            throw new FinalApiException("invalid error");
        }
    }

    public ApiResponse search(Long listId, String query, int page) throws ApiException {
        try {
            return fetchResource(eventResourceUrl("checkinlists/" + listId + "/positions") + "?ignore_status=true&page=" + page + "&search=" + URLEncoder.encode(query, "UTF-8"));
        } catch (ResourceNotModified | UnsupportedEncodingException resourceNotModified) {
            throw new FinalApiException("invalid error");
        }
    }

    public String apiURL(String suffix) {
        try {
            return new URL(new URL(url), "/api/v1/" + suffix).toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
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
            throw new FinalApiException("Resource not modified");
        }
    }

    public ApiResponse postResource(String full_url, JSONObject data) throws ApiException {
        return postResource(full_url, data, null);
    }

    public ApiResponse postResource(String full_url, JSONObject data, String idempotency_key) throws ApiException {
        Request.Builder request = new Request.Builder()
                .url(full_url)
                .post(RequestBody.create(data.toString(), MediaType.parse("application/json")))
                .header("Authorization", "Device " + key);
        if (idempotency_key != null) {
            request = request.header("X-Idempotency-Key", idempotency_key);
        }
        try {
            return apiCall(request.build());
        } catch (ResourceNotModified resourceNotModified) {
            resourceNotModified.printStackTrace();
            throw new FinalApiException("Resource not modified");
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
            throw new FinalApiException("Resource not modified");
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
            throw new ApiException("Connection error: " + e.getMessage(), e);
        }

        String safe_url = request.url().toString().replaceAll("^(.*)key=([0-9A-Za-z]+)([^0-9A-Za-z]*)", "$1key=redacted$3");
        sentry.addHttpBreadcrumb(safe_url, request.method(), response.code());
        String body = "";
        if (json) {
            try {
                body = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                throw new ApiException("Connection error: " + e.getMessage(), e);
            }
        }

        if (response.code() >= 500) {
            response.close();
            throw new ApiException("Server error: " + response.code());
        } else if (response.code() == 404 && (!json || !body.startsWith("{"))) {
            response.close();
            throw new FinalApiException("Server error: Resource not found.");
        } else if (response.code() == 304) {
            throw new ResourceNotModified();
        } else if (response.code() == 403) {
            response.close();
            throw new FinalApiException("Server error: Permission denied.");
        } else if (response.code() == 409) {
            response.close();
            throw new ConflictApiException("Server error: " + response.code() + ": " + body);
        } else if (response.code() >= 405) {
            response.close();
            throw new FinalApiException("Server error: " + response.code() + ".");
        }
        if (response.code() == 401) {
            if (body.startsWith("{")) {
                try {
                    JSONObject err = new JSONObject(body);
                    if (err.optString("detail", "").equals("Device access has been revoked.")) {
                        throw new DeviceAccessRevokedException("Device access has been revoked.");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
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
            throw new ApiException("Invalid JSON received: " + body.substring(0, 100), e);
        }
    }

}
