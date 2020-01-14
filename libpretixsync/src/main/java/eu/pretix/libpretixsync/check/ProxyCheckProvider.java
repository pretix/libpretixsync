package eu.pretix.libpretixsync.check;


import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import eu.pretix.libpretixsync.db.CheckIn;
import eu.pretix.libpretixsync.db.CheckInList;
import eu.pretix.libpretixsync.db.Item;
import eu.pretix.libpretixsync.db.ItemVariation;
import eu.pretix.libpretixsync.db.Question;
import eu.pretix.libpretixsync.db.QuestionOption;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLException;

import eu.pretix.libpretixsync.DummySentryImplementation;
import eu.pretix.libpretixsync.SentryInterface;
import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.DefaultHttpClientFactory;
import eu.pretix.libpretixsync.api.HttpClientFactory;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.config.ConfigStore;
import eu.pretix.libpretixsync.serialization.JSONArrayDeserializer;
import eu.pretix.libpretixsync.serialization.JSONArraySerializer;
import eu.pretix.libpretixsync.serialization.JSONObjectDeserializer;
import eu.pretix.libpretixsync.serialization.JSONObjectSerializer;
import eu.pretix.libpretixsync.utils.NetUtils;
import io.requery.BlockingEntityStore;
import io.requery.Persistable;
import kotlin.reflect.jvm.internal.impl.util.Check;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProxyCheckProvider implements TicketCheckProvider {
    private ConfigStore config;
    private SentryInterface sentry;
    private Long listId;
    private OkHttpClient client;
    private ObjectMapper mapper;

    public ProxyCheckProvider(ConfigStore config, HttpClientFactory httpClientFactory, BlockingEntityStore<Persistable> dataStore, Long listId) {
        this.config = config;
        this.sentry = new DummySentryImplementation();
        this.listId = listId;
        this.client = httpClientFactory.buildClient(NetUtils.ignoreSSLforURL(config.getApiUrl()));
        this.mapper = new ObjectMapper();

        SimpleModule m = new SimpleModule();
        m.addDeserializer(JSONObject.class, new JSONObjectDeserializer());
        m.addDeserializer(JSONArray.class, new JSONArrayDeserializer());
        m.addSerializer(JSONObject.class, new JSONObjectSerializer());
        m.addSerializer(JSONArray.class, new JSONArraySerializer());
        mapper.registerModule(m);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ProxyCheckProvider(ConfigStore config, BlockingEntityStore<Persistable> dataStore, Long listId) {
        this(config, new DefaultHttpClientFactory(), dataStore, listId);
    }

    public SentryInterface getSentry() {
        return sentry;
    }

    public void setSentry(SentryInterface sentry) {
        this.sentry = sentry;
    }

    private String execute(Request r) throws ApiException, CheckException {
        Response response;
        try {
            response = client.newCall(r).execute();
        } catch (SSLException e) {
            e.printStackTrace();
            throw new ApiException("Error while creating a secure connection.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error: " + e.getMessage(), e);
        }

        sentry.addHttpBreadcrumb(r.url().toString(), r.method(), response.code());
        String body = "";
        try {
            body = response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error: " + e.getMessage(), e);
        }

        if (response.code() >= 500) {
            response.close();
            throw new ApiException("Server error: " + response.code());
        } else if (response.code() == 404) {
            response.close();
            throw new ApiException("Server error: Resource not found.");
        } else if (response.code() == 403) {
            response.close();
            throw new ApiException("Server error: Permission denied.");
        } else if (response.code() >= 400) {
            response.close();
            try {
                throw new CheckException((new JSONObject("body")).optString("title", "?"));
            } catch (JSONException e) {
                throw new ApiException(body);
            }
        } else if (response.code() >= 405) {
            response.close();
            throw new ApiException("Server error: " + response.code() + ".");
        }
        return body;
    }

    @Override
    public CheckResult check(String ticketid, List<Answer> answers, boolean ignore_unpaid, boolean with_badge_data) {
        Map<String, Object> data = new HashMap<>();
        data.put("ticketid", ticketid);
        data.put("answers", answers);
        data.put("ignore_unpaid", ignore_unpaid);
        data.put("with_badge_data", with_badge_data);
        try {
            Request request = new Request.Builder()
                    .url(config.getApiUrl() + "/proxyapi/v1/rpc/" + config.getEventSlug() + "/" + listId + "/check/")
                    .post(RequestBody.create(mapper.writeValueAsString(data), MediaType.parse("application/json")))
                    .header("Authorization", "Device " + config.getApiKey())
                    .build();
            String body = execute(request);
            return mapper.readValue(body, CheckResult.class);
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.getMessage());
            return new CheckResult(CheckResult.Type.ERROR, e.getMessage());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return new CheckResult(CheckResult.Type.ERROR, e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            return new CheckResult(CheckResult.Type.ERROR, e.getMessage());
        } catch (CheckException e) {
            return new CheckResult(CheckResult.Type.ERROR, e.getMessage());
        }
    }

    @Override
    public CheckResult check(String ticketid) {
        return check(ticketid, new ArrayList<Answer>(), false, true);
    }

    @Override
    public List<SearchResult> search(String query, int page) throws CheckException {
        Map<String, Object> data = new HashMap<>();
        data.put("query", query);
        data.put("page", page);
        try {
            Request request = new Request.Builder()
                    .url(config.getApiUrl() + "/proxyapi/v1/rpc/" + config.getEventSlug() + "/" + listId + "/search/")
                    .post(RequestBody.create(mapper.writeValueAsString(data), MediaType.parse("application/json")))
                    .header("Authorization", "Device " + config.getApiKey())
                    .build();
            String body = execute(request);
            return mapper.readValue(body, new TypeReference<List<SearchResult>>() {
            });
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.search", "API Error: " + e.getMessage());
            throw new CheckException(e.getMessage());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new CheckException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new CheckException(e.getMessage());
        }
    }

    @Override
    public StatusResult status() throws CheckException {
        Request request = new Request.Builder()
                .url(config.getApiUrl() + "/proxyapi/v1/rpc/" + config.getEventSlug() + "/" + listId + "/status/")
                .header("Authorization", "Device " + config.getApiKey())
                .build();
        try {
            String body = execute(request);
            return mapper.readValue(body, StatusResult.class);
        } catch (ApiException e) {
            sentry.addBreadcrumb("provider.status", "API Error: " + e.getMessage());
            throw new CheckException(e.getMessage());
        } catch (JsonParseException e) {
            e.printStackTrace();
            throw new CheckException(e.getMessage());
        } catch (JsonMappingException e) {
            e.printStackTrace();
            throw new CheckException(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new CheckException(e.getMessage());
        }
    }
}
