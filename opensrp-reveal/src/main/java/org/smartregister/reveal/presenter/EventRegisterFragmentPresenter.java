package org.smartregister.reveal.presenter;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.configurableviews.ConfigurableViewsLibrary;
import org.smartregister.configurableviews.helper.ConfigurableViewsHelper;
import org.smartregister.configurableviews.model.View;
import org.smartregister.configurableviews.model.ViewConfiguration;
import org.smartregister.cursoradapter.SmartRegisterQueryBuilder;
import org.smartregister.domain.Event;
import org.smartregister.reveal.R;
import org.smartregister.reveal.contract.EventRegisterContract;
import org.smartregister.reveal.interactor.EventRegisterFragmentInteractor;
import org.smartregister.reveal.model.EventRegisterDetails;
import org.smartregister.reveal.model.TaskFilterParams;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.Utils;

import java.util.Set;

/**
 * Created by samuelgithengi on 7/30/20.
 */
public class EventRegisterFragmentPresenter implements EventRegisterContract.Presenter {

    private String viewConfigurationIdentifier;

    private ConfigurableViewsHelper viewsHelper;

    private Set<View> visibleColumns;

    private EventRegisterContract.View view;

    private EventRegisterFragmentInteractor interactor;

    private EventRegisterDetails eventRegisterDetails;

    private TaskFilterParams filterParams;

    public EventRegisterFragmentPresenter(EventRegisterContract.View view, String viewConfigurationIdentifier) {
        this.view = view;
        this.interactor = new EventRegisterFragmentInteractor(this);
        this.viewConfigurationIdentifier = viewConfigurationIdentifier;
        this.viewsHelper = ConfigurableViewsLibrary.getInstance().getConfigurableViewsHelper();
    }

    @Override
    public void processViewConfigurations() {
        if (!StringUtils.isBlank(this.viewConfigurationIdentifier)) {
            ViewConfiguration viewConfiguration = viewsHelper.getViewConfiguration(this.viewConfigurationIdentifier);
            if (viewConfiguration != null) {
                visibleColumns = viewsHelper.getRegisterActiveColumns(this.viewConfigurationIdentifier);
            }
        }
    }

    @Override
    public void initializeQueries(String mainCondition) {

        String tableName = Constants.EventsRegister.TABLE_NAME;

        String countSelect = countSelect(tableName, mainCondition);
        String mainSelect = mainSelect(tableName, mainCondition);

        view.initializeQueryParams(tableName, countSelect, mainSelect);
        view.initializeAdapter(visibleColumns);

        view.countExecute();
        view.filterandSortInInitializeQueries();

    }

    private String mainSelect(String tableName, String mainCondition) {
        SmartRegisterQueryBuilder queryBUilder = new SmartRegisterQueryBuilder();
        queryBUilder.selectInitiateMainTable(tableName, mainColumns(tableName));
        return queryBUilder.mainCondition(mainCondition);
    }

    protected String[] mainColumns(String tableName) {
        String[] columns = new String[]{
                tableName + ".relationalid",
                tableName + "." + Constants.DatabaseKeys.EVENT_DATE,
                tableName + "." + Constants.DatabaseKeys.EVENT_TYPE,
                tableName + "." + Constants.DatabaseKeys.SOP,
                tableName + "." + Constants.DatabaseKeys.ENTITY,
                tableName + "." + Constants.DatabaseKeys.STATUS,
                tableName + "." + Constants.DatabaseKeys.BASE_ENTITY_ID,
                tableName + "." + Constants.DatabaseKeys.SPRAYED,
                tableName + "." + Constants.DatabaseKeys.FOUND
        };
        return columns;
    }

    private String countSelect(String tableName, String mainCondition) {
        SmartRegisterQueryBuilder countQueryBuilder = new SmartRegisterQueryBuilder();
        countQueryBuilder.selectInitiateMainTableCounts(tableName);
        return countQueryBuilder.mainCondition(mainCondition);
    }

    @Override
    public void startSync() {
        Utils.startImmediateSync();
    }

    @Override
    public void searchGlobally(String s) {
        // do nothing
    }

    @Override
    public void onEventFound(Event event) {
        String formName = view.getJsonFormUtils().getFormName(this.eventRegisterDetails.getEventType(), null);
        if (StringUtils.isBlank(formName)) {
            view.displayError(R.string.opening_form_title, R.string.form_not_found);
        } else {
            JSONObject formJSON = view.getJsonFormUtils().getFormJSON(view.getContext(), formName, null, null);
            view.getJsonFormUtils().populateForm(event, formJSON);
            view.getJsonFormUtils().populateFormWithServerOptions(formName, formJSON);
            view.startForm(formJSON);
        }
        view.hideProgressDialog();
    }

    @Override
    public void onOpenMapClicked() {
        view.startMapActivity();
    }

    public void onEventSelected(EventRegisterDetails details) {
        view.showProgressDialog(R.string.opening_form_title, R.string.opening_form_message);
        this.eventRegisterDetails = details;
        interactor.findEvent(details.getFormSubmissionId());
    }

    @Override
    public void onFilterTasksClicked() {
        view.openFilterActivity(filterParams);
    }

}
