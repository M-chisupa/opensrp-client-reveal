package org.smartregister.reveal.sync;

import net.sqlcipher.database.SQLiteDatabase;

import org.smartregister.domain.Client;
import org.smartregister.domain.Event;
import org.smartregister.domain.Obs;
import org.smartregister.domain.jsonmapping.ClientClassification;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.util.Constants.DatabaseKeys;
import org.smartregister.reveal.util.Constants.Tables;

import java.util.Collections;
import java.util.HashMap;

import static org.smartregister.commonregistry.CommonRepository.ID_COLUMN;

/**
 * Created by samuelgithengi on 9/17/20.
 */
public class SprayEventProcessor {

    public void processSprayEvent(RevealClientProcessor clientProcessor, ClientClassification clientClassification, Event event, boolean isLocalEvent) throws Exception {


        ClientClassification normalClassification = new ClientClassification();
        normalClassification.case_classification_rules = Collections.singletonList(clientClassification.case_classification_rules.get(0));
        ClientClassification ecEventsClassification = new ClientClassification();
        ecEventsClassification.case_classification_rules = Collections.singletonList(clientClassification.case_classification_rules.get(1));


        Client client = new Client(event.getBaseEntityId());
        clientProcessor.processEvent(event, client, normalClassification);

        if (event.getDetails() == null)
            event.setDetails(new HashMap<>());
        event.getDetails().put(DatabaseKeys.FORM_SUBMISSION_ID, event.getFormSubmissionId());

        if (isLocalEvent) {
            Obs mopup = event.findObs(null, true, "mopup");
            if (mopup != null) {
                event.setFormSubmissionId(event.getBaseEntityId() + ":" + mopup.getValue());
            } else {
                event.setFormSubmissionId(event.getBaseEntityId());
            }
        } else {
            event.setFormSubmissionId(event.getBaseEntityId());
            SQLiteDatabase sqLiteDatabase = RevealApplication.getInstance().getRepository().getWritableDatabase();
            sqLiteDatabase.delete(Tables.EC_EVENTS_TABLE,
                    String.format("%s like ? AND %s=?", ID_COLUMN, DatabaseKeys.EVENT_TYPE),
                    new String[]{event.getBaseEntityId() + "%", event.getEventType()});


        }
        clientProcessor.processEvent(event, client, ecEventsClassification);
    }
}
