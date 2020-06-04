package org.smartregister.reveal.util;

import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vijay.jsonwizard.constants.JsonFormConstants;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.CoreLibrary;
import org.smartregister.clientandeventmodel.Client;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.domain.Location;
import org.smartregister.domain.Task;
import org.smartregister.domain.db.EventClient;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.reveal.BuildConfig;
import org.smartregister.reveal.sync.RevealClientProcessor;
import org.smartregister.sync.helper.ECSyncHelper;
import org.smartregister.util.DateTimeTypeConverter;
import org.smartregister.util.JsonFormUtils;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.smartregister.family.util.JsonFormUtils.RELATIONSHIPS;
import static org.smartregister.reveal.application.RevealApplication.getInstance;
import static org.smartregister.reveal.util.Constants.DETAILS;
import static org.smartregister.reveal.util.Constants.METADATA;
import static org.smartregister.util.JsonFormUtils.getJSONObject;

/**
 * This is a state aware form processor that process and save the form details
 */
public class NativeFormProcessor {
    private JSONObject jsonForm;
    private AllSharedPreferences allSharedPreferences;
    private Gson gson;
    private JSONArray _fields;
    private String entityId;
    private Client _client;
    private Event _event;
    private String encounterType;
    private String bindType;
    private org.smartregister.domain.db.Client domainClient;
    private org.smartregister.domain.db.Event domainEvent;
    private boolean hasClient = false;

    public static NativeFormProcessor createInstance(String jsonString) throws JSONException {
        return new NativeFormProcessor(new JSONObject(jsonString));
    }

    public static NativeFormProcessor createInstance(JSONObject jsonObject) {
        return new NativeFormProcessor(jsonObject);
    }

    @VisibleForTesting
    public NativeFormProcessor(JSONObject jsonObject) {
        this.jsonForm = jsonObject;
        allSharedPreferences = CoreLibrary.getInstance().context().allSharedPreferences();
        gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter()).create();
    }

    public NativeFormProcessor withEntityId(String entityId) {
        this.entityId = entityId;
        return this;
    }

    public NativeFormProcessor withEncounterType(String encounterType) {
        this.encounterType = encounterType;
        return this;
    }

    public NativeFormProcessor withBindType(String bindType) {
        this.bindType = bindType;
        return this;
    }

    public NativeFormProcessor hasClient(boolean hasClient) {
        this.hasClient = hasClient;
        return this;
    }

    private JSONObject getOrCreateDetailsNode() throws JSONException {
        JSONObject formData = jsonForm.has(DETAILS) ? jsonForm.getJSONObject(DETAILS) : null;
        if (formData == null) {
            formData = new JSONObject();
            formData.put(Constants.Properties.FORM_VERSION, jsonForm.optString("form_version"));
            formData.put(Constants.Properties.APP_VERSION_NAME, BuildConfig.VERSION_NAME);

            jsonForm.put(DETAILS, formData);
        }

        return formData;
    }

    public NativeFormProcessor tagTaskDetails(Task task) throws JSONException {
        JSONObject formData = getOrCreateDetailsNode();
        formData.put(Constants.Properties.TASK_IDENTIFIER, task.getIdentifier());
        formData.put(Constants.Properties.TASK_BUSINESS_STATUS, task.getBusinessStatus());
        formData.put(Constants.Properties.TASK_STATUS, task.getStatus());
        return this;
    }

    public NativeFormProcessor tagLocationData(Location location) throws JSONException {
        if (location == null) return this;

        JSONObject formData = getOrCreateDetailsNode();
        formData.put(Constants.Properties.LOCATION_ID, location.getId());
        formData.put(Constants.Properties.LOCATION_UUID, location.getId());
        formData.put(Constants.Properties.LOCATION_VERSION, location.getServerVersion().toString());
        return this;
    }

    private JSONArray getFields() throws JSONException {
        if (_fields == null)
            _fields = jsonForm.getJSONObject(JsonFormConstants.STEP1).getJSONArray(JsonFormConstants.FIELDS);

        return _fields;
    }

    private Client createClient() throws JSONException {
        if (hasClient && _client == null)
            _client = JsonFormUtils.createBaseClient(
                    getFields(),
                    JsonClientProcessingUtils.formTag(allSharedPreferences),
                    entityId
            );

        return _client;
    }

    private Event createEvent() throws JSONException {
        if (_event == null)
            _event = JsonFormUtils.createEvent(
                    getFields(),
                    getJSONObject(jsonForm, METADATA),
                    JsonClientProcessingUtils.formTag(allSharedPreferences),
                    entityId,
                    encounterType,
                    bindType
            );

        return _event;
    }

    public NativeFormProcessor tagEventMetadata() throws JSONException {
        JsonClientProcessingUtils.tagSyncMetadata(allSharedPreferences, createEvent());
        return this;
    }

    private org.smartregister.domain.db.Client getDomainClient() throws JSONException {
        if (hasClient && domainClient == null) {
            JSONObject clientJson = new JSONObject(gson.toJson(createClient()));
            domainClient = gson.fromJson(clientJson.toString(), org.smartregister.domain.db.Client.class);
        }

        return domainClient;
    }

    private org.smartregister.domain.db.Event getDomainEvent() throws JSONException {
        if (domainEvent == null) {
            JSONObject eventJson = new JSONObject(gson.toJson(createEvent()));
            domainEvent = gson.fromJson(eventJson.toString(), org.smartregister.domain.db.Event.class);
        }

        return domainEvent;
    }

    public NativeFormProcessor saveEvent() throws JSONException {
        JSONObject eventJson = new JSONObject(gson.toJson(createEvent()));

        // inject the details
        if (jsonForm.has(DETAILS))
            eventJson.put(DETAILS, jsonForm.getJSONObject(DETAILS));

        getSyncHelper().addEvent(createEvent().getBaseEntityId(), eventJson);
        return this;
    }

    /**
     * when updating client info
     *
     * @return
     * @throws JSONException
     */
    public NativeFormProcessor mergeAndSaveClient() throws JSONException {
        JSONObject updatedClientJson = new JSONObject(JsonFormUtils.gson.toJson(createClient()));

        JSONObject originalClientJsonObject = getSyncHelper().getClient(createClient().getBaseEntityId());

        JSONObject mergedJson = JsonFormUtils.merge(originalClientJsonObject, updatedClientJson);

        //retain existing relationships, relationships are deleted on @Link org.smartregister.util.JsonFormUtils.createBaseClient
        JSONObject relationships = mergedJson.optJSONObject(RELATIONSHIPS);
        if ((relationships == null || relationships.length() == 0) && originalClientJsonObject != null) {
            mergedJson.put(RELATIONSHIPS, originalClientJsonObject.optJSONObject(RELATIONSHIPS));
        }

        getSyncHelper().addClient(createClient().getBaseEntityId(), mergedJson);
        return this;
    }

    /**
     * Save clients
     *
     * @param consumer
     * @return
     * @throws JSONException
     */
    public NativeFormProcessor saveClient(Consumer<Client> consumer) throws JSONException {
        Client client = createClient();
        if (consumer != null)
            consumer.accept(client);

        JSONObject clientJson = new JSONObject(gson.toJson(client));
        getSyncHelper().addClient(createClient().getBaseEntityId(), clientJson);
        return this;
    }

    public NativeFormProcessor clientProcessForm() throws JSONException {
        EventClient eventClient = new EventClient(getDomainEvent(), getDomainClient());

        RevealClientProcessor.getInstance(getInstance().getApplicationContext()).processClient(Collections.singletonList(eventClient), true);
        long lastSyncTimeStamp = allSharedPreferences.fetchLastUpdatedAtDate(0);
        Date lastSyncDate = new Date(lastSyncTimeStamp);
        allSharedPreferences.saveLastUpdatedAtDate(lastSyncDate.getTime());
        return this;
    }

    public NativeFormProcessor closeRegistrationID(String uniqueIDKey) throws JSONException {
        JSONObject field = JsonFormUtils.getFieldJSONObject(JsonFormUtils.fields(jsonForm), uniqueIDKey);
        if (field != null) {
            String uniqueID = field.getString(JsonFormConstants.VALUE);
            CoreLibrary.getInstance().context().getUniqueIdRepository().close(uniqueID);
        }
        return this;
    }

    public String getFieldValue(String fieldKey) throws JSONException {
        String value = null;

        JSONObject field = JsonFormUtils.getFieldJSONObject(JsonFormUtils.fields(jsonForm), fieldKey);
        if (field != null && field.has(JsonFormConstants.VALUE))
            value = field.getString(JsonFormConstants.VALUE);

        return StringUtils.isBlank(value) ? "" : value;
    }

    public ECSyncHelper getSyncHelper() {
        return ECSyncHelper.getInstance(getInstance().getContext().applicationContext());
    }

    public NativeFormProcessor populateValues(Map<String, Object> dictionary) throws JSONException {
        int step = 1;
        while (jsonForm.has("step" + step)) {
            JSONObject jsonStepObject = jsonForm.getJSONObject("step" + step);
            JSONArray array = jsonStepObject.getJSONArray(JsonFormConstants.FIELDS);
            int position = 0;
            while (position < array.length()) {
                JSONObject object = array.getJSONObject(position);
                String key = object.getString(JsonFormConstants.KEY);
                if (dictionary.containsKey(key))
                    object.put(JsonFormConstants.VALUE, dictionary.get(key));

                position++;
            }

            step++;
        }
        return this;
    }

    public Location getCurrentOperationalArea() {
        return Utils.getOperationalAreaLocation(PreferencesUtil.getInstance().getCurrentOperationalArea());
    }

    public Location getCurrentSelectedStructure() {
        return Utils.getStructureByName(PreferencesUtil.getInstance().getCurrentStructure());
    }
}
