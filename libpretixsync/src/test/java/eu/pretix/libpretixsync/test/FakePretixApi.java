package eu.pretix.libpretixsync.test;

import eu.pretix.libpretixsync.api.ApiException;
import eu.pretix.libpretixsync.api.DefaultHttpClientFactory;
import eu.pretix.libpretixsync.api.PretixApi;
import eu.pretix.libpretixsync.check.TicketCheckProvider;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

public class FakePretixApi extends PretixApi {

    private JSONObject nextRedeemResponse = new JSONObject();
    private JSONObject nextStatusResponse = new JSONObject();
    private JSONObject nextSearchResponse = new JSONObject();
    private JSONObject nextDownloadResponse = new JSONObject();
    private String lastSecret;
    private List<TicketCheckProvider.Answer> lastAnswers;
    private String lastQuery;

    public FakePretixApi() {
        super("", "", SUPPORTED_API_VERSION, new DefaultHttpClientFactory());
    }

    public void setNextRedeemResponse(JSONObject nextRedeemResponse) {
        this.nextRedeemResponse = nextRedeemResponse;
    }

    public void setNextStatusResponse(JSONObject nextStatusResponse) {
        this.nextStatusResponse = nextStatusResponse;
    }

    public void setNextSearchResponse(JSONObject nextSearchResponse) {
        this.nextSearchResponse = nextSearchResponse;
    }

    public void setNextDownloadResponse(JSONObject nextDownloadResponse) {
        this.nextDownloadResponse = nextDownloadResponse;
    }

    public String getLastSecret() {
        return lastSecret;
    }

    public List<TicketCheckProvider.Answer> getLastAnswers() {
        return lastAnswers;
    }

    public String getLastQuery() {
        return lastQuery;
    }

    @Override
    public JSONObject redeem(String secret, List<TicketCheckProvider.Answer> answers, boolean ignore_unpaid) throws ApiException {
        lastAnswers = answers;
        lastSecret = secret;
        return nextRedeemResponse;
    }

    @Override
    public JSONObject redeem(String secret, Date datetime, boolean force, String nonce, List<TicketCheckProvider.Answer> answers, boolean ignore_unpaid) throws ApiException {
        lastAnswers = answers;
        lastSecret = secret;
        return nextRedeemResponse;
    }

    @Override
    public JSONObject status() throws ApiException {
        return nextStatusResponse;
    }

    @Override
    public JSONObject search(String query) throws ApiException {
        lastQuery = query;
        return nextSearchResponse;
    }

    @Override
    public JSONObject download() throws ApiException {
        return nextDownloadResponse;
    }
}
