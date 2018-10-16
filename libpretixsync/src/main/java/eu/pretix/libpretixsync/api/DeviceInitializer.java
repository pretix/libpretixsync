package eu.pretix.libpretixsync.api;

import org.json.JSONException;
import org.json.JSONObject;

import eu.pretix.libpretixsync.config.ConfigStore;

public abstract class DeviceInitializer {

    public class InitializationResult {
        private String organizer;
        private String device_id;
        private String unique_serial;
        private String api_token;
        private String name;

        public InitializationResult() {}

        public InitializationResult(String organizer, String device_id, String unique_serial, String api_token, String name) {
            this.organizer = organizer;
            this.device_id = device_id;
            this.unique_serial = unique_serial;
            this.api_token = api_token;
            this.name = name;
        }

        public String getOrganizer() {
            return organizer;
        }

        public String getDevice_id() {
            return device_id;
        }

        public String getUnique_serial() {
            return unique_serial;
        }

        public String getApi_token() {
            return api_token;
        }

        public String getName() {
            return name;
        }
    }

    protected PretixApi api;

    public DeviceInitializer(ConfigStore config, HttpClientFactory httpClientFactory) {
        this.api = PretixApi.fromConfig(config, httpClientFactory);
    }

    public InitializationResult init(String token) {
        String hardware_brand = fetchHardwareBrand();
        String hardware_model = fetchHardwareModel();
        String software_brand = fetchSoftwareBrand();
        String software_version = fetchSoftwareVersion();

        try {
            JSONObject responseJson = api.initializeDevice(token, hardware_brand, hardware_model, software_brand, software_version);

            String organizer = responseJson.getString("organizer");
            String device_id = responseJson.getString("device_id");
            String unique_serial = responseJson.getString("unique_serial");
            String api_token = responseJson.getString("api_token");
            String name = responseJson.getString("name");

            InitializationResult result = new InitializationResult(organizer, device_id, unique_serial, api_token, name);
            return result;

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ApiException e) {
            e.printStackTrace();
        }

        return new InitializationResult();
    }

    public abstract String fetchHardwareBrand();
    public abstract String fetchHardwareModel();
    public abstract String fetchSoftwareBrand();
    public abstract String fetchSoftwareVersion();
}
