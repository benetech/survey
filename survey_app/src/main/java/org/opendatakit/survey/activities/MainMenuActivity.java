/*
 * Copyright (C) 2009-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.survey.activities;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.BackStackEntry;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;
import org.opendatakit.consts.IntentConsts;
import org.opendatakit.activities.BaseActivity;
import org.opendatakit.application.CommonApplication;
import org.opendatakit.database.data.OrderedColumns;
import org.opendatakit.database.data.UserTable;
import org.opendatakit.exception.ActionNotAuthorizedException;
import org.opendatakit.exception.ServicesAvailabilityException;
import org.opendatakit.fragment.AboutMenuFragment;
import org.opendatakit.listener.DatabaseConnectionListener;
import org.opendatakit.properties.CommonToolProperties;
import org.opendatakit.properties.DynamicPropertiesCallback;
import org.opendatakit.properties.PropertiesSingleton;
import org.opendatakit.properties.PropertyManager;
import org.opendatakit.provider.FormsColumns;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.provider.InstanceProviderAPI;
import org.opendatakit.survey.fragments.FrontPageFragment;
import org.opendatakit.webkitserver.utilities.SerializationUtils;
import org.opendatakit.webkitserver.utilities.SerializationUtils.MacroStringExpander;
import org.opendatakit.utilities.ODKFileUtils;
import org.opendatakit.webkitserver.utilities.UrlUtils;
import org.opendatakit.logging.WebLogger;
import org.opendatakit.logging.WebLoggerIf;
import org.opendatakit.views.ExecutorContext;
import org.opendatakit.views.ExecutorProcessor;
import org.opendatakit.views.ODKWebView;
import org.opendatakit.database.service.UserDbInterface;
import org.opendatakit.database.service.DbHandle;
import org.opendatakit.database.service.TableHealthInfo;
import org.opendatakit.database.service.TableHealthStatus;
import org.opendatakit.survey.R;
import org.opendatakit.survey.application.Survey;
import org.opendatakit.survey.fragments.BackPressWebkitConfirmationDialogFragment;
import org.opendatakit.survey.fragments.FormChooserListFragment;
import org.opendatakit.survey.fragments.InitializationFragment;
import org.opendatakit.survey.fragments.WebViewFragment;
import org.opendatakit.survey.logic.FormIdStruct;
import org.opendatakit.survey.logic.SurveyDataExecutorProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for displaying buttons to launch the major activities. Launches
 * some activities based on returns of others.
 *
 * @author mitchellsundt@gmail.com
 */
public class MainMenuActivity extends BaseActivity implements IOdkSurveyActivity, FrontPageFragment.OnFragmentInteractionListener{

  private static final String t = "MainMenuActivity";

  public static enum ScreenList {
    MAIN_SCREEN, FORM_CHOOSER, WEBKIT, INITIALIZATION_DIALOG, ABOUT_MENU, FRONT_PAGE
  };

  // Extra returned from gp activity
  // TODO: move to Survey???
  public static final String LOCATION_LATITUDE_RESULT = "latitude";
  public static final String LOCATION_LONGITUDE_RESULT = "longitude";
  public static final String LOCATION_ALTITUDE_RESULT = "altitude";
  public static final String LOCATION_ACCURACY_RESULT = "accuracy";

  // tags for retained context
  private static final String DISPATCH_STRING_WAITING_FOR_DATA = "dispatchStringWaitingForData";
  private static final String ACTION_WAITING_FOR_DATA = "actionWaitingForData";

  private static final String FORM_URI = "formUri";
  private static final String UPLOAD_TABLE_ID = "uploadTableId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String SCREEN_PATH = "screenPath";
  private static final String CONTROLLER_STATE = "controllerState";
  private static final String AUXILLARY_HASH = "auxillaryHash";
  private static final String SESSION_VARIABLES = "sessionVariables";
  private static final String SECTION_STATE_SCREEN_HISTORY = "sectionStateScreenHistory";

  private static final String CURRENT_FRAGMENT = "currentFragment";

  private static final String QUEUED_ACTIONS = "queuedActions";
  private static final String RESPONSE_JSON = "responseJSON";

  /** tables that have conflict rows */
  public static final String CONFLICT_TABLES = "conflictTables";

  // menu options

  private static final int MENU_CLOUD_FORMS = Menu.FIRST ;
  private static final int MENU_ABOUT = Menu.FIRST + 1;

  // activity callback codes
  private static final int HANDLER_ACTIVITY_CODE = 20;
  private static final int INTERNAL_ACTIVITY_CODE = 21;
  private static final int SYNC_ACTIVITY_CODE = 22;
  private static final int CONFLICT_ACTIVITY_CODE = 23;

  private static final String BACKPRESS_DIALOG_TAG = "backPressDialog";

  private static final boolean EXIT = true;

  private static class ScreenState {
    String screenPath;
    String state;

    ScreenState(String screenPath, String state) {
      this.screenPath = screenPath;
      this.state = state;
    }
  }

  private static class SectionScreenStateHistory implements Parcelable {
    ScreenState currentScreen = new ScreenState(null, null);
    ArrayList<ScreenState> history = new ArrayList<ScreenState>();

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(currentScreen.screenPath);
      dest.writeString(currentScreen.state);

      dest.writeInt(history.size());
      for (int i = 0; i < history.size(); ++i) {
        ScreenState screen = history.get(i);
        dest.writeString(screen.screenPath);
        dest.writeString(screen.state);
      }
    }

    public static final Parcelable.Creator<SectionScreenStateHistory> CREATOR = new Parcelable.Creator<SectionScreenStateHistory>() {
      public SectionScreenStateHistory createFromParcel(Parcel in) {
        SectionScreenStateHistory cur = new SectionScreenStateHistory();
        String screenPath = in.readString();
        String state = in.readString();
        cur.currentScreen = new ScreenState(screenPath, state);
        int count = in.readInt();
        for (int i = 0; i < count; ++i) {
          screenPath = in.readString();
          state = in.readString();
          cur.history.add(new ScreenState(screenPath, state));
        }
        return cur;
      }

      @Override
      public SectionScreenStateHistory[] newArray(int size) {
        SectionScreenStateHistory[] array = new SectionScreenStateHistory[size];
        for (int i = 0; i < size; ++i) {
          array[i] = null;
        }
        return array;
      }
    };
  }

  /**
   * Member variables that are saved and restored across orientation changes.
   */

  private ScreenList currentFragment = ScreenList.FRONT_PAGE;

  private String dispatchStringWaitingForData = null;
  private String actionWaitingForData = null;

  private String appName = null;
  private String uploadTableId = null;
  private FormIdStruct currentForm = null; // via FORM_URI (formUri)
  private String instanceId = null;

  private Bundle sessionVariables = new Bundle();
  private ArrayList<SectionScreenStateHistory> sectionStateScreenHistory = new ArrayList<SectionScreenStateHistory>();

  private final String refId = UUID.randomUUID().toString();
  private String auxillaryHash = null;

  private String frameworkBaseUrl = null;
  private Long frameworkLastModifiedDate = 0L;

  private LinkedList<String> queuedActions = new LinkedList<String>();

  LinkedList<String> queueResponseJSON = new LinkedList<String>();

  // DO NOT USE THESE -- only used to determine if the current form has changed.
  private String trackingFormPath = null;
  private Long trackingFormLastModifiedDate = 0L;

  private String submenuPage = null;
  /**
   * track which tables have conflicts (these need to be resolved before Survey
   * can operate)
   */
  Bundle mConflictTables = new Bundle();

  /**
   * Member variables that do not need to be preserved across orientation
   * changes, etc.
   */

  private DatabaseConnectionListener mIOdkDataDatabaseListener;
  // no need to preserve
  private PropertyManager mPropertyManager;

  // no need to preserve
  private AlertDialog mAlertDialog;

  @Override
  protected void onPause() {
    // if (mAlertDialog != null && mAlertDialog.isShowing()) {
    // mAlertDialog.dismiss();
    // }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // TODO Auto-generated method stub
    super.onSaveInstanceState(outState);

    if (dispatchStringWaitingForData != null) {
      outState.putString(DISPATCH_STRING_WAITING_FOR_DATA, dispatchStringWaitingForData);
    }
    if (actionWaitingForData != null) {
      outState.putString(ACTION_WAITING_FOR_DATA, actionWaitingForData);
    }
    outState.putString(CURRENT_FRAGMENT, currentFragment.name());

    if (getCurrentForm() != null) {
      outState.putString(FORM_URI, getCurrentForm().formUri.toString());
    }
    if (getInstanceId() != null) {
      outState.putString(INSTANCE_ID, getInstanceId());
    }
    if (getUploadTableId() != null) {
      outState.putString(UPLOAD_TABLE_ID, getUploadTableId());
    }
    if (getScreenPath() != null) {
      outState.putString(SCREEN_PATH, getScreenPath());
    }
    if (getControllerState() != null) {
      outState.putString(CONTROLLER_STATE, getControllerState());
    }
    if (getAuxillaryHash() != null) {
      outState.putString(AUXILLARY_HASH, getAuxillaryHash());
    }
    if (getAppName() != null) {
      outState.putString(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
    }
    outState.putBundle(SESSION_VARIABLES, sessionVariables);

    outState.putParcelableArrayList(SECTION_STATE_SCREEN_HISTORY, sectionStateScreenHistory);

    if ( !queuedActions.isEmpty() ) {
      String[] actionOutcomesArray = new String[queuedActions.size()];
      queuedActions.toArray(actionOutcomesArray);
      outState.putStringArray(QUEUED_ACTIONS, actionOutcomesArray);
    }

    if ( !queueResponseJSON.isEmpty() ) {
      String[] qra = queueResponseJSON.toArray(new String[queueResponseJSON.size()]);
      outState.putStringArray(RESPONSE_JSON, qra);
    }

    if (mConflictTables != null && !mConflictTables.isEmpty()) {
      outState.putBundle(CONFLICT_TABLES, mConflictTables);
    }

  }

  private void transitionToFormHelper(Uri uri, FormIdStruct newForm) {
    // work through switching to that form
    setAppName(newForm.appName);
    setCurrentForm(newForm);
    clearSectionScreenState();
    String fragment = uri.getFragment();
    if (fragment != null && fragment.length() != 0) {
      // and process the fragment to find the instanceId, screenPath and other
      // kv pairs
      String[] pargs = fragment.split("&");
      boolean first = true;
      StringBuilder b = new StringBuilder();
      int i;
      for (i = 0; i < pargs.length; ++i) {
        String[] keyValue = pargs[i].split("=");
        if ("instanceId".equals(keyValue[0])) {
          if (keyValue.length == 2) {
            setInstanceId(StringEscapeUtils.unescapeHtml4(keyValue[1]));
          }
        } else if ("screenPath".equals(keyValue[0])) {
          if (keyValue.length == 2) {
            setSectionScreenState(StringEscapeUtils.unescapeHtml4(keyValue[1]), null);
          }
        } else if ("refId".equals(keyValue[0]) || "formPath".equals(keyValue[0])) {
          // ignore
        } else {
          if (!first) {
            b.append("&");
          }
          first = false;
          b.append(pargs[i]);
        }
      }
      String aux = b.toString();
      if (aux.length() != 0) {
        setAuxillaryHash(aux);
      }
    } else {
      setInstanceId(null);
      setAuxillaryHash(null);
    }
    currentFragment = ScreenList.WEBKIT;
  }

  @Override
  protected void onStop() {
    super.onStop();
  }

  @SuppressLint("InlinedApi")
  @Override
  protected void onStart() {
    super.onStart();
  }

  public void scanForConflictAllTables() {
    
    UserDbInterface db = ((Survey) getApplication()).getDatabase();
    if ( db != null ) {
      List<TableHealthInfo> info;
      DbHandle dbHandle = null;
      try {
        dbHandle = db.openDatabase(appName);
        info = db.getTableHealthStatuses(getAppName(), dbHandle);
      } catch (ServicesAvailabilityException e) {
        WebLogger.getLogger(appName).printStackTrace(e);
        return;
      } finally {
        try {
          if ( dbHandle != null ) {
            db.closeDatabase(appName, dbHandle);
          }
        } catch (ServicesAvailabilityException e) {
          WebLogger.getLogger(appName).printStackTrace(e);
        }
      }
      
      if ( info != null ) {

        Bundle conflictTables = new Bundle();

        for (TableHealthInfo tableInfo : info) {
          TableHealthStatus status = tableInfo.getHealthStatus();
          if ( status == TableHealthStatus.TABLE_HEALTH_HAS_CONFLICTS ||
               status == TableHealthStatus.TABLE_HEALTH_HAS_CHECKPOINTS_AND_CONFLICTS ) {
              conflictTables.putString(tableInfo.getTableId(), tableInfo.getTableId());
          }
        }
        
        mConflictTables = conflictTables;
      }
    }
  }

  private void resolveAnyConflicts() {
    if (mConflictTables == null || mConflictTables.isEmpty()) {
      scanForConflictAllTables();
    }

    if ((mConflictTables != null) && !mConflictTables.isEmpty()) {
      Iterator<String> iterator = mConflictTables.keySet().iterator();
      String tableId = iterator.next();
      mConflictTables.remove(tableId);

      Intent i;
      i = new Intent();
      i.setComponent(new ComponentName(IntentConsts.ResolveConflict.APPLICATION_NAME,
          IntentConsts.ResolveConflict.ACTIVITY_NAME));
      i.setAction(Intent.ACTION_EDIT);
      i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
      i.putExtra(IntentConsts.INTENT_KEY_TABLE_ID, tableId);
      try {
        this.startActivityForResult(i, CONFLICT_ACTIVITY_CODE);
      } catch (ActivityNotFoundException e) {
        Toast.makeText(this,
            getString(R.string.activity_not_found, IntentConsts.ResolveConflict.ACTIVITY_NAME),
            Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  public void databaseAvailable() {
    if ( getAppName() != null ) {
      resolveAnyConflicts();
    }
    FragmentManager mgr = this.getFragmentManager();
    if ( currentFragment != null ) {
      Fragment fragment = mgr.findFragmentByTag(currentFragment.name());
      if (fragment instanceof DatabaseConnectionListener) {
        ((DatabaseConnectionListener) fragment).databaseAvailable();
      }
    }
    if ( mIOdkDataDatabaseListener != null ) {
      mIOdkDataDatabaseListener.databaseAvailable();
    }
  }

  @Override
  public void databaseUnavailable() {
    FragmentManager mgr = this.getFragmentManager();
    if ( currentFragment != null ) {
      Fragment fragment = mgr.findFragmentByTag(currentFragment.name());
      if (fragment instanceof DatabaseConnectionListener) {
        ((DatabaseConnectionListener) fragment).databaseUnavailable();
      }
    }
    if ( mIOdkDataDatabaseListener != null ) {
      mIOdkDataDatabaseListener.databaseUnavailable();
    }
  }

  public void setCurrentForm(FormIdStruct currentForm) {
    WebLogger.getLogger(getAppName()).i(t,
        "setCurrentForm: " + ((currentForm == null) ? "null" : currentForm.formPath));
    this.currentForm = currentForm;
  }

  public FormIdStruct getCurrentForm() {
    return this.currentForm;
  }

  private void setUploadTableId(String uploadTableId) {
    WebLogger.getLogger(getAppName()).i(t, "setUploadTableId: " + uploadTableId);
    this.uploadTableId = uploadTableId;
  }

  @Override
  public String getUploadTableId() {
    return this.uploadTableId;
  }

  @Override
  public void setInstanceId(String instanceId) {
    WebLogger.getLogger(getAppName()).i(t, "setInstanceId: " + instanceId);
    this.instanceId = instanceId;
  }

  @Override
  public String getInstanceId() {
    return this.instanceId;
  }

  public void setAuxillaryHash(String auxillaryHash) {
    WebLogger.getLogger(getAppName()).i(t, "setAuxillaryHash: " + auxillaryHash);
    this.auxillaryHash = auxillaryHash;
  }

  @Override
  public String getAppName() {
    return this.appName;
  }

  @Override
  public String getActiveUser() {
    PropertiesSingleton props = CommonToolProperties.get(this, getAppName());

    return props.getActiveUser();
  }

  @Override
  public String getProperty(String propertyId) {
    FormIdStruct form = getCurrentForm();
    PropertiesSingleton props = CommonToolProperties.get(this, getAppName());

    final DynamicPropertiesCallback cb = new DynamicPropertiesCallback(getAppName(),
        form == null ? null : getCurrentForm().tableId, getInstanceId(),
            props.getActiveUser(), props.getLocale(),
            props.getProperty(CommonToolProperties.KEY_USERNAME),
            props.getProperty(CommonToolProperties.KEY_ACCOUNT));

    String value = mPropertyManager.getSingularProperty(propertyId, cb);
    return value;
  }

  @Override
  public String getWebViewContentUri() {
    Uri u = UrlUtils.getWebViewContentUri(this);

    String uriString = u.toString();

    // Ensures that the string always ends with '/'
    if (uriString.charAt(uriString.length() - 1) != '/') {
      return uriString + "/";
    } else {
      return uriString;
    }
  }

  @Override
  public String getUrlBaseLocation(boolean ifChanged) {
    // Find the formPath for the framework formDef.json
    File frameworkFormDef = new File( ODKFileUtils.getFormFolder(appName, 
        FormsColumns.COMMON_BASE_FORM_ID, FormsColumns.COMMON_BASE_FORM_ID), "formDef.json");

    // formPath always begins ../ -- strip that off to get explicit path
    // suffix...
    File systemFolder = new File(ODKFileUtils.getSystemFolder(appName));

    // File htmlFile = new File(mediaFolder, mPrompt.getAppearanceHint());
    File htmlFile = new File(systemFolder, "index.html");

    if (!htmlFile.exists()) {
      return null;
    }

    String fullPath = UrlUtils.getAsWebViewUri(this, appName,
        ODKFileUtils.asUriFragment(appName, htmlFile));

    if (fullPath == null) {
      return null;
    }

    // for some reason, the jqMobile framework wants an empty search string...
    // add this here now...
    fullPath += "?";
    Long frameworkLastModified = frameworkFormDef.lastModified();

    boolean changed = false;

    if (ifChanged && frameworkBaseUrl != null && frameworkBaseUrl.equals(fullPath)) {
      // determine if there are any changes in the framework
      // or in the form. If there are, reload. Otherwise,
      // return null.

      changed = (!frameworkLastModified.equals(frameworkLastModifiedDate));
    }

    if (currentForm == null) {
      trackingFormPath = null;
      trackingFormLastModifiedDate = 0L;
      changed = true;
    } else if (trackingFormPath == null || !trackingFormPath.equals(currentForm.formPath)) {
      trackingFormPath = currentForm.formPath;
      trackingFormLastModifiedDate = currentForm.lastDownloadDate.getTime();
    } else {
      changed = changed
          || (Long.valueOf(trackingFormLastModifiedDate).compareTo(
              currentForm.lastDownloadDate.getTime()) < 0);
      trackingFormLastModifiedDate = currentForm.lastDownloadDate.getTime();
    }

    frameworkBaseUrl = fullPath;
    frameworkLastModifiedDate = frameworkLastModified;
    return (ifChanged && !changed) ? null : frameworkBaseUrl;
  }

  @Override
  public String getUrlLocationHash() {
    if (currentForm == null) {
      // we want framework...
      File frameworkFormDef = new File( ODKFileUtils.getFormFolder(appName, 
          FormsColumns.COMMON_BASE_FORM_ID, FormsColumns.COMMON_BASE_FORM_ID), "formDef.json");
      
      String hashUrl = "#formPath="
          + StringEscapeUtils.escapeHtml4(ODKFileUtils.getRelativeFormPath(appName, frameworkFormDef))
          + ((instanceId == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceId))
          + ((getScreenPath() == null) ? "" : "&screenPath="
              + StringEscapeUtils.escapeHtml4(getScreenPath()))
          + ("&refId=" + StringEscapeUtils.escapeHtml4(refId))
          + ((auxillaryHash == null) ? "" : "&" + auxillaryHash);
      return hashUrl;
    } else {
      String hashUrl = "#formPath="
          + StringEscapeUtils.escapeHtml4((currentForm == null) ? "" : currentForm.formPath)
          + ((instanceId == null) ? "" : "&instanceId=" + StringEscapeUtils.escapeHtml4(instanceId))
          + ((getScreenPath() == null) ? "" : "&screenPath="
              + StringEscapeUtils.escapeHtml4(getScreenPath()))
          + ("&refId=" + StringEscapeUtils.escapeHtml4(refId))
          + ((auxillaryHash == null) ? "" : "&" + auxillaryHash);

      return hashUrl;
    }
  }

  @Override
  public void clearAuxillaryHash() {
    this.auxillaryHash = null;
  }

  public String getAuxillaryHash() {
    return this.auxillaryHash;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getRefId() {
    return this.refId;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // android.os.Debug.waitForDebugger();
    submenuPage = getIntentExtras().getString("_sync_state");
    try {
      // ensure that we have a BackgroundTaskFragment...
      // create it programmatically because if we place it in the
      // layout XML, it will be recreated with each screen rotation
      // and we don't want that!!!
      mPropertyManager = new PropertyManager(this);

      // must be at the beginning of any activity that can be called from an
      // external intent
      setAppName(ODKFileUtils.getOdkDefaultAppName());
      Uri uri = getIntent().getData();
      Uri formUri = null;
      if (uri != null) {
        // initialize to the URI, then we will customize further based upon the
        // savedInstanceState...
        final Uri uriFormsProvider = FormsProviderAPI.CONTENT_URI;
        final Uri uriWebView = UrlUtils.getWebViewContentUri(this);
        if (uri.getScheme().equalsIgnoreCase(uriFormsProvider.getScheme()) && uri.getAuthority().equalsIgnoreCase(uriFormsProvider.getAuthority())) {
          List<String> segments = uri.getPathSegments();
          if (segments != null && segments.size() == 1) {
            String appName = segments.get(0);
            setAppName(appName);
          } else if (segments != null && segments.size() >= 2) {
            String appName = segments.get(0);
            setAppName(appName);
            String tableId = segments.get(1);
            String formId = (segments.size() > 2) ? segments.get(2) : null;
            formUri = Uri.withAppendedPath(
                Uri.withAppendedPath(Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName),
                    tableId), formId);
          } else {
            createErrorDialog(getString(R.string.invalid_uri_expecting_n_segments, uri.toString(), 2), EXIT);
            return;
          }
        } else if (uri.getScheme().equals(uriWebView.getScheme()) && uri.getAuthority().equals(uriWebView.getAuthority())
            && uri.getPort() == uriWebView.getPort()) {
          List<String> segments = uri.getPathSegments();
          if (segments != null && segments.size() == 1) {
            String appName = segments.get(0);
            setAppName(appName);
          } else {
            createErrorDialog(getString(R.string.invalid_uri_expecting_one_segment, uri.toString()),
                EXIT);
            return;
          }

        } else {
          createErrorDialog(getString(R.string.unrecognized_uri, uri.toString(), uriWebView.toString(),
              uriFormsProvider.toString()), EXIT);
          return;
        }
      }

      if (savedInstanceState != null) {
        // if appName is explicitly set, use it...
        setAppName(savedInstanceState.containsKey(IntentConsts.INTENT_KEY_APP_NAME) ?
            savedInstanceState.getString(IntentConsts.INTENT_KEY_APP_NAME) :
            getAppName());

        if (savedInstanceState.containsKey(CONFLICT_TABLES)) {
          mConflictTables = savedInstanceState.getBundle(CONFLICT_TABLES);
        }
      }

      try {
        String appName = getAppName();
        if (appName != null && appName.length() != 0) {
          ODKFileUtils.verifyExternalStorageAvailability();
          ODKFileUtils.assertDirectoryStructure(appName);
        }
      } catch (RuntimeException e) {
        createErrorDialog(e.getMessage(), EXIT);
        return;
      }

      WebLogger.getLogger(getAppName()).i(t, "Starting up, creating directories");

      if (savedInstanceState != null) {
        // if we are restoring, assume that initialization has already occurred.

        dispatchStringWaitingForData = savedInstanceState.containsKey(DISPATCH_STRING_WAITING_FOR_DATA) ?
            savedInstanceState.getString(DISPATCH_STRING_WAITING_FOR_DATA) : null;
        actionWaitingForData = savedInstanceState.containsKey(ACTION_WAITING_FOR_DATA) ?
            savedInstanceState.getString(ACTION_WAITING_FOR_DATA) :
            null;

        currentFragment = ScreenList.valueOf(savedInstanceState.containsKey(CURRENT_FRAGMENT) ?
            savedInstanceState.getString(CURRENT_FRAGMENT) :
            currentFragment.name());

        if (savedInstanceState.containsKey(FORM_URI)) {
          FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), Uri.parse(savedInstanceState.getString(FORM_URI)));
          if (newForm != null) {
            setAppName(newForm.appName);
            setCurrentForm(newForm);
          }
        }
        setInstanceId(savedInstanceState.containsKey(INSTANCE_ID) ?
            savedInstanceState.getString(INSTANCE_ID) :
            getInstanceId());
        setUploadTableId(savedInstanceState.containsKey(UPLOAD_TABLE_ID) ?
            savedInstanceState.getString(UPLOAD_TABLE_ID) :
            getUploadTableId());

        String tmpScreenPath = savedInstanceState.containsKey(SCREEN_PATH) ?
            savedInstanceState.getString(SCREEN_PATH) :
            getScreenPath();
        String tmpControllerState = savedInstanceState.containsKey(CONTROLLER_STATE) ?
            savedInstanceState.getString(CONTROLLER_STATE) :
            getControllerState();
        setSectionScreenState(tmpScreenPath, tmpControllerState);

        setAuxillaryHash(savedInstanceState.containsKey(AUXILLARY_HASH) ?
            savedInstanceState.getString(AUXILLARY_HASH) :
            getAuxillaryHash());

        if (savedInstanceState.containsKey(SESSION_VARIABLES)) {
          sessionVariables = savedInstanceState.getBundle(SESSION_VARIABLES);
        }

        if (savedInstanceState.containsKey(SECTION_STATE_SCREEN_HISTORY)) {
          sectionStateScreenHistory = savedInstanceState.getParcelableArrayList(SECTION_STATE_SCREEN_HISTORY);
        }

        if (savedInstanceState.containsKey(QUEUED_ACTIONS)) {
          String[] actionOutcomesArray = savedInstanceState.getStringArray(QUEUED_ACTIONS);
          queuedActions.clear();
          queuedActions.addAll(Arrays.asList(actionOutcomesArray));
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(RESPONSE_JSON)) {
          String[] pendingResponseJSON = savedInstanceState.getStringArray(RESPONSE_JSON);
          queueResponseJSON.addAll(Arrays.asList(pendingResponseJSON));
        }
      } else if (formUri != null) {
        // request specifies a specific formUri -- try to open that
        FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), formUri);
        if (newForm == null) {
          // can't find it -- launch the initialization dialog to hopefully
          // discover it.
          WebLogger.getLogger(getAppName()).i(t, "onCreate -- calling setRunInitializationTask");
          ((Survey) getApplication()).setRunInitializationTask(getAppName());
          currentFragment = ScreenList.WEBKIT;
        } else {
          transitionToFormHelper(uri, newForm);
        }
      }
    } catch (Exception e) {
      createErrorDialog(e.getMessage(), EXIT);
    } finally {
      setContentView(R.layout.main_screen);

      ActionBar actionBar = getActionBar();
      actionBar.show();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    ((Survey) getApplication()).establishDoNotFireDatabaseConnectionListener(this);

    swapToFragmentView(currentFragment);
  }

  @Override
  public void onPostResume() {
    super.onPostResume();
    ((Survey) getApplication()).fireDatabaseConnectionListener();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    PropertiesSingleton props = CommonToolProperties.get(this, getAppName());

    int showOption = MenuItem.SHOW_AS_ACTION_IF_ROOM;
    MenuItem item;
    if (currentFragment != ScreenList.WEBKIT) {
      ActionBar actionBar = getActionBar();
      actionBar.setCustomView(R.layout.action_bar);
      actionBar.setDisplayShowCustomEnabled(true);
      actionBar.show();

      item = menu.add(Menu.NONE, MENU_CLOUD_FORMS, Menu.NONE, R.string.sync);
      item.setIcon(R.drawable.ic_cached_black_24dp).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

      item = menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.about);
      item.setIcon(R.drawable.ic_info_outline_black_24dp).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    } else {
      ActionBar actionBar = getActionBar();
      actionBar.hide();
    }

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    if (item.getItemId() == MENU_CLOUD_FORMS) {
      try {
        Intent syncIntent = new Intent();
        syncIntent.setComponent(new ComponentName(
            IntentConsts.Sync.APPLICATION_NAME,
            IntentConsts.Sync.ACTIVITY_NAME));
        syncIntent.setAction(Intent.ACTION_DEFAULT);
        Bundle bundle = new Bundle();
        bundle.putString(IntentConsts.INTENT_KEY_APP_NAME, appName);
        syncIntent.putExtras(bundle);
        this.startActivityForResult(syncIntent, SYNC_ACTIVITY_CODE);
      } catch (ActivityNotFoundException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, R.string.sync_not_found, Toast.LENGTH_LONG).show();
      }
      return true;
    } else if (item.getItemId() == MENU_ABOUT) {
      swapToFragmentView(ScreenList.ABOUT_MENU);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void chooseForm(Uri formUri) {
    Intent i = new Intent(Intent.ACTION_EDIT, formUri, this, MainMenuActivity.class);
    i.putExtra("_sync_state", submenuPage);
    startActivityForResult(i, INTERNAL_ACTIVITY_CODE);
  }

  private void createErrorDialog(String errorMsg, final boolean shouldExit) {
    WebLogger.getLogger(getAppName()).e(t, errorMsg);
    if (mAlertDialog != null) {
      mAlertDialog.dismiss();
      mAlertDialog = null;
    }
    mAlertDialog = new AlertDialog.Builder(this).create();
    mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
    mAlertDialog.setMessage(errorMsg);
    DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int button) {
        switch (button) {
        case DialogInterface.BUTTON_POSITIVE:
          if (shouldExit) {
            Intent i = new Intent();
            setResult(RESULT_CANCELED, i);
            finish();
          }
          break;
        }
      }
    };
    mAlertDialog.setCancelable(false);
    mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), errorListener);
    mAlertDialog.show();
  }

  private void popBackStack() {
    FragmentManager mgr = getFragmentManager();
    int idxLast = mgr.getBackStackEntryCount() - 2;
    if (idxLast < 0) {
      Intent result = new Intent();
      // If we are in a WEBKIT, return the instanceId and the savepoint_type...
      if (this.getInstanceId() != null && currentFragment == ScreenList.WEBKIT) {
        result.putExtra("instanceId", getInstanceId());
        // in this case, the savepoint_type is null (a checkpoint).
      }
      this.setResult(RESULT_OK, result);
      finish();
    } else {
      BackStackEntry entry = mgr.getBackStackEntryAt(idxLast);
      swapToFragmentView(ScreenList.valueOf(entry.getName()));
    }
  }

  @Override
  public void initializationCompleted() {
    popBackStack();
  }

  @Override
  public void onBackPressed() {
    if ( (currentFragment == ScreenList.WEBKIT) &&
        getInstanceId() != null && getCurrentForm() != null &&
        getCurrentForm().tableId != null) {

      // try to retrieve the active dialog
      DialogFragment dialog = (DialogFragment)
          getFragmentManager().findFragmentByTag(BACKPRESS_DIALOG_TAG);

      if (dialog != null && dialog.getDialog() != null) {
        // as-is
      } else {
        dialog = new BackPressWebkitConfirmationDialogFragment();
      }
      dialog.show(getFragmentManager(), BACKPRESS_DIALOG_TAG);
    } else {
      popBackStack();
    }
  }

  // for back press suppression
  // trigger save of everything...
  @Override
  public void saveAllAsIncompleteThenPopBackStack() {
    String tableId = this.getCurrentForm().tableId;
    String rowId = this.getInstanceId();

    if ( rowId != null && tableId != null ) {
      DbHandle dbHandleName = null;
      try {
        dbHandleName = this.getDatabase().openDatabase(getAppName());
        OrderedColumns cols = this.getDatabase()
            .getUserDefinedColumns(getAppName(), dbHandleName, tableId);
        UserTable table = this.getDatabase()
            .saveAsIncompleteMostRecentCheckpointRowWithId(getAppName(), dbHandleName, tableId, cols, rowId);
        // this should not be possible, but if somehow we exit before anything is written
        // clear instanceId if the row no longer exists
        if ( table.getNumberOfRows() == 0 ) {
          setInstanceId(null);
        }
      } catch (ActionNotAuthorizedException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, R.string.database_authorization_error_occured, Toast.LENGTH_LONG)
            .show();
      } catch (ServicesAvailabilityException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, R.string.database_error_occured, Toast.LENGTH_LONG).show();
      } finally {
        if ( dbHandleName != null ) {
          try {
            this.getDatabase().closeDatabase(getAppName(), dbHandleName);
          } catch (ServicesAvailabilityException e) {
            // ignore
            WebLogger.getLogger(getAppName()).printStackTrace(e);
          }
        }
      }
    }
    popBackStack();
  }

  // trigger resolve UI...
  @Override
  public void resolveAllCheckpointsThenPopBackStack() {
    String tableId = this.getCurrentForm().tableId;
    String rowId = this.getInstanceId();

    if ( rowId != null && tableId != null ) {
      DbHandle dbHandleName = null;
      try {
        dbHandleName = this.getDatabase().openDatabase(getAppName());
        OrderedColumns cols = this.getDatabase()
            .getUserDefinedColumns(getAppName(), dbHandleName, tableId);
        UserTable table = this.getDatabase()
            .deleteAllCheckpointRowsWithId(getAppName(), dbHandleName, tableId, cols, rowId);
        // clear instanceId if the row no longer exists
        if ( table.getNumberOfRows() == 0 ) {
          setInstanceId(null);
        }
      } catch (ActionNotAuthorizedException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, R.string.database_authorization_error_occured, Toast.LENGTH_LONG)
            .show();
      } catch (ServicesAvailabilityException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        Toast.makeText(this, R.string.database_error_occured, Toast.LENGTH_LONG).show();
      } finally {
        if ( dbHandleName != null ) {
          try {
            this.getDatabase().closeDatabase(getAppName(), dbHandleName);
          } catch (ServicesAvailabilityException e) {
            // ignore
            WebLogger.getLogger(getAppName()).printStackTrace(e);
          }
        }
      }
    }
    popBackStack();
  }

  public void hideWebkitView() {
    // This is a callback thread.
    // We must invalidate the options menu on the UI thread
    this.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        WebLogger.getLogger(getAppName()).i(t, "hideWebkitView");
        // In the fragment UI, we want to return to not having any
        // instanceId defined.
        // JQueryODKView webkitView = (JQueryODKView)
        // findViewById(R.id.webkit);
        // setInstanceId(null);
        // setSectionScreenState(null,null);
        // setAuxillaryHash(null);
        // webkitView.loadPage();
        levelSafeInvalidateOptionsMenu();
      }
    });
  }

  public void swapToFragmentView(ScreenList newScreenType) {
    WebLogger.getLogger(getAppName()).i(t, "swapToFragmentView: " + newScreenType.name());
    FragmentManager mgr = getFragmentManager();
    FragmentTransaction trans = null;
    Fragment newFragment = null;
    if (newScreenType == ScreenList.MAIN_SCREEN) {
      throw new IllegalStateException("unexpected reference to generic main screen");
    } else if (newScreenType == ScreenList.FORM_CHOOSER) {
      newFragment = mgr.findFragmentByTag(newScreenType.name());
      if (newFragment == null) {
        newFragment = new FormChooserListFragment();
      }
    } else if (newScreenType == ScreenList.FRONT_PAGE) {
      newFragment = mgr.findFragmentByTag(newScreenType.name());
      if (newFragment == null) {
        newFragment = new FrontPageFragment();
      }
    } else if (newScreenType == ScreenList.INITIALIZATION_DIALOG) {
      newFragment = mgr.findFragmentByTag(newScreenType.name());
      if (newFragment == null) {
        newFragment = new InitializationFragment();
      }
    } else if (newScreenType == ScreenList.WEBKIT) {
      newFragment = mgr.findFragmentByTag(newScreenType.name());
      if (newFragment == null) {
        WebLogger.getLogger(getAppName()).i(t, "[" + this.hashCode() + "] creating new webkit fragment " + newScreenType.name());
        newFragment = new WebViewFragment();
      }
    } else if (newScreenType == ScreenList.ABOUT_MENU) {
      newFragment = mgr.findFragmentByTag(newScreenType.name());
      if (newFragment == null) {
        newFragment = new AboutMenuFragment();
      }

    } else {
      throw new IllegalStateException("Unrecognized ScreenList type");
    }

    boolean matchingBackStackEntry = false;
    for (int i = 0; i < mgr.getBackStackEntryCount(); ++i) {
      BackStackEntry e = mgr.getBackStackEntryAt(i);
      WebLogger.getLogger(getAppName()).i(t, "BackStackEntry["+i+"] " + e.getName());
      if (e.getName().equals(newScreenType.name())) {
        matchingBackStackEntry = true;
      }
    }

    if (matchingBackStackEntry) {
      if ( trans != null ) {
        WebLogger.getLogger(getAppName()).e(t,  "Unexpected active transaction when popping state!");
        trans = null;
      }
      // flush backward, to the screen we want to go back to
      currentFragment = newScreenType;
      WebLogger.getLogger(getAppName()).e(t,  "[" + this.hashCode() + "] popping back stack " + currentFragment.name());
      mgr.popBackStackImmediate(currentFragment.name(), 0);
    } else {
      // add transaction to show the screen we want
      if ( trans == null ) {
        trans = mgr.beginTransaction();
      }
      currentFragment = newScreenType;
      trans.replace(R.id.main_content, newFragment, currentFragment.name());
      WebLogger.getLogger(getAppName()).i(t,  "[" + this.hashCode() + "] adding to back stack " + currentFragment.name());
      trans.addToBackStack(currentFragment.name());
    }

    
    // and see if we should re-initialize...
    if ((currentFragment != ScreenList.INITIALIZATION_DIALOG)
        && ((Survey) getApplication()).shouldRunInitializationTask(getAppName())) {
      WebLogger.getLogger(getAppName()).i(t, "swapToFragmentView -- calling clearRunInitializationTask");
      // and immediately clear the should-run flag...
      ((Survey) getApplication()).clearRunInitializationTask(getAppName());
      // OK we should swap to the InitializationFragment view
      // this will skip the transition to whatever screen we were trying to 
      // go to and will instead show the InitializationFragment view. We
      // restore to the desired screen via the setFragmentToShowNext()
      //
      // NOTE: this discards the uncommitted transaction.
      // Robolectric complains about a recursive state transition.
      if ( trans != null ) {
        trans.commit();
      }
      swapToFragmentView(ScreenList.INITIALIZATION_DIALOG);
    } else {
      // before we actually switch to a WebKit, be sure
      // we have the form definition for it...
      if (currentFragment == ScreenList.WEBKIT && getCurrentForm() == null) {
        // we were sent off to the initialization dialog to try to
        // discover the form. We need to inquire about the form again
        // and, if we cannot find it, report an error to the user.
        final Uri uriFormsProvider = FormsProviderAPI.CONTENT_URI;
        Uri uri = getIntent().getData();
        Uri formUri = null;

        if (uri.getScheme().equalsIgnoreCase(uriFormsProvider.getScheme())
            && uri.getAuthority().equalsIgnoreCase(uriFormsProvider.getAuthority())) {
          List<String> segments = uri.getPathSegments();
          if (segments != null && segments.size() >= 2) {
            String appName = segments.get(0);
            setAppName(appName);
            String tableId = segments.get(1);
            String formId = (segments.size() > 2) ? segments.get(2) : null;
            formUri = Uri.withAppendedPath(
                Uri.withAppendedPath(
                    Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, appName),
                      tableId), formId);
          } else {
            swapToFragmentView(ScreenList.FRONT_PAGE);
            createErrorDialog(
                getString(R.string.invalid_uri_expecting_n_segments, uri.toString(), 2), EXIT);
            return;
          }
          // request specifies a specific formUri -- try to open that
          FormIdStruct newForm = FormIdStruct.retrieveFormIdStruct(getContentResolver(), formUri);
          if (newForm == null) {
            // error
            swapToFragmentView(ScreenList.FRONT_PAGE);
            createErrorDialog(getString(R.string.form_not_found, segments.get(1)), EXIT);
            return;
          } else {
            transitionToFormHelper(uri, newForm);
          }
        }
      }

      if ( trans != null ) {
        trans.commit();
      }
      invalidateOptionsMenu();
    }
  }

  private void levelSafeInvalidateOptionsMenu() {
    invalidateOptionsMenu();
  }

  /***********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * Interfaces to Javascript layer (also used in Java).
   */

  private void dumpScreenStateHistory() {
    WebLoggerIf l = WebLogger.getLogger(getAppName());

    l.d(t, "-------------*start* dumpScreenStateHistory--------------------");
    if (sectionStateScreenHistory.isEmpty()) {
      l.d(t, "sectionScreenStateHistory EMPTY");
    } else {
      for (int i = sectionStateScreenHistory.size() - 1; i >= 0; --i) {
        SectionScreenStateHistory thisSection = sectionStateScreenHistory.get(i);
        l.d(t, "[" + i + "] screenPath: " + thisSection.currentScreen.screenPath);
        l.d(t, "[" + i + "] state:      " + thisSection.currentScreen.state);
        if (thisSection.history.isEmpty()) {
          l.d(t, "[" + i + "] history[] EMPTY");
        } else {
          for (int j = thisSection.history.size() - 1; j >= 0; --j) {
            ScreenState ss = thisSection.history.get(j);
            l.d(t, "[" + i + "] history[" + j + "] screenPath: " + ss.screenPath);
            l.d(t, "[" + i + "] history[" + j + "] state:      " + ss.state);
          }
        }
      }
    }
    l.d(t, "------------- *end*  dumpScreenStateHistory--------------------");
  }

  @Override
  public void pushSectionScreenState() {
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "pushSectionScreenState: NULL!");
      return;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory
        .size() - 1);
    lastSection.history.add(new ScreenState(lastSection.currentScreen.screenPath,
        lastSection.currentScreen.state));
  }

  @Override
  public void setSectionScreenState(String screenPath, String state) {
    if (screenPath == null) {
      WebLogger.getLogger(getAppName()).e(t,
          "setSectionScreenState: NULL currentScreen.screenPath!");
      return;
    } else {
      String[] splits = screenPath.split("/");
      String sectionName = splits[0] + "/";

      WebLogger.getLogger(getAppName()).e(t,
          "setSectionScreenState( " + screenPath + ", " + state + ")");

      SectionScreenStateHistory lastSection;
      if (sectionStateScreenHistory.size() == 0) {
        sectionStateScreenHistory.add(new SectionScreenStateHistory());
        lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size() - 1);
        lastSection.currentScreen.screenPath = screenPath;
        lastSection.currentScreen.state = state;
        lastSection.history.clear();
      } else {
        lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size() - 1);
        if (lastSection.currentScreen.screenPath.startsWith(sectionName)) {
          lastSection.currentScreen.screenPath = screenPath;
          lastSection.currentScreen.state = state;
        } else {
          sectionStateScreenHistory.add(new SectionScreenStateHistory());
          lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size() - 1);
          lastSection.currentScreen.screenPath = screenPath;
          lastSection.currentScreen.state = state;
          lastSection.history.clear();
        }
      }
    }
  }

  @Override
  public void clearSectionScreenState() {
    sectionStateScreenHistory.clear();
    sectionStateScreenHistory.add(new SectionScreenStateHistory());
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory
        .size() - 1);
    lastSection.currentScreen.screenPath = "initial/0";
    lastSection.currentScreen.state = null;
    lastSection.history.clear();
  }

  @Override
  public String getControllerState() {
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "getControllerState: NULL!");
      return null;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory
        .size() - 1);
    return lastSection.currentScreen.state;
  }

  public String getScreenPath() {
    dumpScreenStateHistory();
    if (sectionStateScreenHistory.size() == 0) {
      WebLogger.getLogger(getAppName()).i(t, "getScreenPath: NULL!");
      return null;
    }
    SectionScreenStateHistory lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory
        .size() - 1);
    return lastSection.currentScreen.screenPath;
  }

  @Override
  public boolean hasScreenHistory() {
    // two or more sections -- there must be history
    if (sectionStateScreenHistory.size() > 1) {
      return true;
    }
    // no sections -- no history
    if (sectionStateScreenHistory.size() == 0) {
      return false;
    }

    SectionScreenStateHistory thisSection = sectionStateScreenHistory.get(0);
    return thisSection.history.size() != 0;
  }

  @Override
  public String popScreenHistory() {
    if (sectionStateScreenHistory.size() == 0) {
      return null;
    }

    SectionScreenStateHistory lastSection;
    lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size() - 1);
    if (lastSection.history.size() != 0) {
      ScreenState lastHistory = lastSection.history.remove(lastSection.history.size() - 1);
      lastSection.currentScreen.screenPath = lastHistory.screenPath;
      lastSection.currentScreen.state = lastHistory.state;
      return lastSection.currentScreen.screenPath;
    }

    // pop to an enclosing screen
    sectionStateScreenHistory.remove(sectionStateScreenHistory.size() - 1);

    if (sectionStateScreenHistory.size() == 0) {
      return null;
    }

    lastSection = sectionStateScreenHistory.get(sectionStateScreenHistory.size() - 1);
    return lastSection.currentScreen.screenPath;
  }

  @Override
  public boolean hasSectionStack() {
    return sectionStateScreenHistory.size() != 0;
  }

  @Override
  public String popSectionStack() {
    if (sectionStateScreenHistory.size() != 0) {
      sectionStateScreenHistory.remove(sectionStateScreenHistory.size() - 1);
    }

    if (sectionStateScreenHistory.size() != 0) {
      SectionScreenStateHistory lastSection = sectionStateScreenHistory
          .get(sectionStateScreenHistory.size() - 1);
      return lastSection.currentScreen.screenPath;
    }

    return null;
  }

  @Override
  public void setSessionVariable(String elementPath, String jsonValue) {
    sessionVariables.putString(elementPath, jsonValue);
  }

  @Override
  public String getSessionVariable(String elementPath) {
    return sessionVariables.getString(elementPath);
  }

  @Override
  //czemu mto sie nie wywoluje zawsze skoro powiunnno
  public void saveAllChangesCompleted(String instanceId, final boolean asComplete) {
    Intent result = new Intent();
    result.putExtra("instanceId", instanceId);
    result.putExtra("savepoint_type", "COMPLETE");
    // TODO: unclear what to put in the result intent...
    this.setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public void saveAllChangesFailed(String instanceId) {
    String amihere = "duypa";
    Intent result = new Intent();
    result.putExtra("instanceId", instanceId);
    // should we message anything?
  }

  @Override
  public void ignoreAllChangesCompleted(String instanceId) {
    Intent result = new Intent();
    result.putExtra("instanceId", instanceId);
    result.putExtra("savepoint_type", "INCOMPLETE");
    // TODO: unclear what to put in the result intent...
    this.setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public void ignoreAllChangesFailed(String instanceId) {
    // should we message anything?
  }

  /**
   * Invoked from within Javascript to launch an activity.
   *
   * @param dispatchString   Opaque string -- typically identifies prompt and user action
   *
   * @param action
   *          -- the intent to be launched
   * @param valueContentMap
   *          -- parameters to pass to the intent
   *          {
   *            uri: uriValue, // parse to a uri and set as the data of the
   *                           // intent
   *            extras: extrasMap, // added as extras to the intent
   *            package: packageStr, // the name of a package to launch
   *            type: typeStr, // will be set as the type
   *            data: dataUri // will be parsed to a uri and set as the data of
   *                          // the intent. For now this is equivalent to the
   *                          // uri field, although that name is less precise.
   *          }
   */
  @Override
  public String doAction(
      String dispatchString,
      String action,
      JSONObject valueContentMap) {

    // android.os.Debug.waitForDebugger();

    if (isWaitingForBinaryData()) {
      WebLogger.getLogger(getAppName()).w(t, "Already waiting for data -- ignoring");
      return "IGNORE";
    }

    Intent i;
    boolean isSurveyApp = false;
    boolean isOpendatakitApp = false;
    if (action.startsWith("org.opendatakit.survey")) {
      Class<?> clazz;
      try {
        clazz = Class.forName(action);
        i = new Intent(this, clazz);
        isSurveyApp = true;
      } catch (ClassNotFoundException e) {
        WebLogger.getLogger(getAppName()).printStackTrace(e);
        i = new Intent(action);
      }
    } else {
      i = new Intent(action);
    }

    if (action.startsWith("org.opendatakit.")) {
      isOpendatakitApp = true;
    }

    try {
      
      String uriKey = "uri";
      String extrasKey = "extras";
      String packageKey = "package";
      String typeKey = "type";
      String dataKey = "data";
      
      JSONObject valueMap = null;
      if (valueContentMap != null) {
        
        // do type first, as it says in the spec this call deletes any other
        // data (eg by setData()) on the intent.
        if (valueContentMap.has(typeKey)) {
          String type = valueContentMap.getString(typeKey);
          i.setType(type);
        }
        
        if (valueContentMap.has(uriKey) || valueContentMap.has(dataKey)) {
          // as it currently stands, the data property can be in either the uri
          // or data keys.
          String uriValueStr = null;
          if (valueContentMap.has(uriKey)) {
            uriValueStr = valueContentMap.getString(uriKey);
          }
          // go ahead and overwrite with data if it's present.
          if (valueContentMap.has(dataKey)) {
            uriValueStr = valueContentMap.getString(dataKey);
          }
          if (uriValueStr != null) {
            Uri uri = Uri.parse(uriValueStr);
            i.setData(uri);
          }
        }
        
        if (valueContentMap.has(extrasKey)) {
          valueMap = valueContentMap.getJSONObject(extrasKey);
        }
        
        if (valueContentMap.has(packageKey)) {
          String packageStr = valueContentMap.getString(packageKey);
          i.setPackage(packageStr);
        }
        
      }

      if (valueMap != null) {
        Bundle b;    
        PropertiesSingleton props = CommonToolProperties.get(MainMenuActivity.this, getAppName());

        final DynamicPropertiesCallback cb = new DynamicPropertiesCallback(getAppName(),
            getCurrentForm().tableId, getInstanceId(),
            props.getActiveUser(), props.getLocale(),
            props.getProperty(CommonToolProperties.KEY_USERNAME),
            props.getProperty(CommonToolProperties.KEY_ACCOUNT));

        b = SerializationUtils.convertToBundle(valueMap, new MacroStringExpander() {

          @Override
          public String expandString(String value) {
            if (value != null && value.startsWith("opendatakit-macro(") && value.endsWith(")")) {
              String term = value.substring("opendatakit-macro(".length(), value.length() - 1)
                  .trim();
              String v = mPropertyManager.getSingularProperty(term, cb);
              if (v != null) {
                return v;
              } else {
                WebLogger.getLogger(getAppName()).e(t, "Unable to process opendatakit-macro: " + value);
                throw new IllegalArgumentException(
                    "Unable to process opendatakit-macro expression: " + value);
              }
            } else {
              return value;
            }
          }
        });

        i.putExtras(b);
      }

      if (isSurveyApp || isOpendatakitApp) {
        // ensure that we supply our appName...
        if (!i.hasExtra(IntentConsts.INTENT_KEY_APP_NAME)) {
          i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, getAppName());
          WebLogger.getLogger(getAppName()).w(t, "doAction into Survey or Tables does not supply an appName. Adding: "
              + getAppName());
        }
      }
    } catch (Exception ex) {
      WebLogger.getLogger(getAppName()).e(t, "JSONException: " + ex.toString());
      WebLogger.getLogger(getAppName()).printStackTrace(ex);
      return "JSONException: " + ex.toString();
    }

    dispatchStringWaitingForData = dispatchString;
    actionWaitingForData = action;

    try {
      startActivityForResult(i, HANDLER_ACTIVITY_CODE);
      return "OK";
    } catch (ActivityNotFoundException ex) {
      WebLogger.getLogger(getAppName()).e(t, "Unable to launch activity: " + ex.toString());
      WebLogger.getLogger(getAppName()).printStackTrace(ex);
      return "Application not found";
    }
  }
  
  @Override
  public void queueActionOutcome(String outcome) {
    queuedActions.addLast(outcome);
  }
  
  @Override
  public void queueUrlChange(String hash) {
    try {
      String jsonEncoded = ODKFileUtils.mapper.writeValueAsString(hash);
      queuedActions.addLast(jsonEncoded);
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }
  
  @Override
  public String viewFirstQueuedAction() {
    String outcome = 
        queuedActions.isEmpty() ? null : queuedActions.getFirst();
    return outcome;
  }

  @Override
  public void removeFirstQueuedAction() {
    if ( !queuedActions.isEmpty() ) {
      queuedActions.removeFirst();
    }
  }

  @Override
  public void signalResponseAvailable(String responseJSON) {
    if ( responseJSON == null ) {
      WebLogger.getLogger(getAppName()).e(t, "signalResponseAvailable -- got null responseJSON!");
    } else {
      WebLogger.getLogger(getAppName()).e(t, "signalResponseAvailable -- got "
          + responseJSON.length() + " long responseJSON!");
    }
    if ( responseJSON != null) {
      this.queueResponseJSON.push(responseJSON);
      final ODKWebView webView = (ODKWebView) findViewById(R.id.webkit);
      if (webView != null) {
        WebLogger.getLogger(getAppName()).i(t, "[" + this.hashCode() + "][WebView: " + webView.hashCode() + "] signalResponseAvailable webView.loadUrl will be called");
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            webView.loadUrl("javascript:odkData.responseAvailable();");
          }
        });
      }
    }
  }

  @Override
  public String getResponseJSON() {
    if ( queueResponseJSON.isEmpty() ) {
      return null;
    }
    String responseJSON = queueResponseJSON.removeFirst();
    return responseJSON;
  }

  @Override
  public ExecutorProcessor newExecutorProcessor(ExecutorContext context) {
    return new SurveyDataExecutorProcessor(context);
  }

  @Override
  public void registerDatabaseConnectionBackgroundListener(DatabaseConnectionListener listener) {
    mIOdkDataDatabaseListener = listener;
  }

  @Override
  public UserDbInterface getDatabase() {
    return ((CommonApplication) getApplication()).getDatabase();
  }

  @Override
  public Bundle getIntentExtras() {
    return this.getIntent().getExtras();
  }

  /*
   * END - Interfaces to Javascript layer (also used in Java).
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   * *********************************************************************
   */

  public boolean isWaitingForBinaryData() {
    return actionWaitingForData != null;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    WebLogger.getLogger(getAppName()).i(t, "onActivityResult");
    ODKWebView view = (ODKWebView) findViewById(R.id.webkit);

    if (requestCode == HANDLER_ACTIVITY_CODE) {
      try {
        String jsonObject = null;
        Bundle b = (intent == null) ? null : intent.getExtras();
        JSONObject val = (b == null) ? null : SerializationUtils.convertFromBundle(getAppName(), b);
        JSONObject jsonValue = new JSONObject();
        jsonValue.put("status", resultCode);
        if ( val != null ) {
          jsonValue.put("result", val);
        }
        JSONObject result = new JSONObject();
        result.put("dispatchString", dispatchStringWaitingForData);
        result.put("action",  actionWaitingForData);
        result.put("jsonValue", jsonValue);
        
        String actionOutcome = result.toString();
        this.queueActionOutcome(actionOutcome);
        
        WebLogger.getLogger(getAppName()).i(t, "HANDLER_ACTIVITY_CODE: " + jsonObject);

        view.signalQueuedActionAvailable();
      } catch (Exception e) {
        try {
          JSONObject jsonValue = new JSONObject();
          jsonValue.put("status", 0);
          jsonValue.put("result", e.toString());
          JSONObject result = new JSONObject();
          result.put("dispatchString", dispatchStringWaitingForData);
          result.put("action",  actionWaitingForData);
          result.put("jsonValue", jsonValue);
          this.queueActionOutcome(result.toString());

          view.signalQueuedActionAvailable();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      } finally {
        dispatchStringWaitingForData = null;
        actionWaitingForData = null;
      }
    } else if (requestCode == SYNC_ACTIVITY_CODE) {
      ((Survey) getApplication()).setRunInitializationTask(getAppName());
      this.swapToFragmentView((currentFragment == null) ? ScreenList.FRONT_PAGE : currentFragment);
    }
  }

  public void onFragmentInteraction(ScreenList screen){
    swapToFragmentView(screen);
  }

  @Override
  public String getSubmenuPage(){
    return this.submenuPage;
  }

  public void setSubmenuPage(String value){
    this.submenuPage = value;
  }

  @Override
  public void saveFormSubformPair(String formUuid, String subformId, String subformTableName){
    Toast.makeText(this, formUuid + " " + subformId + " " + subformTableName, Toast.LENGTH_LONG).show();
    ContentValues values = new ContentValues();
    values.put("form_uuid", "formUuid");
    values.put("subform_id", "subformId");
    values.put("subform_table_id", "subformTableName");
    Uri uri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, getAppName() + "/"
            + "L_form_subform_pairs" );
    getContentResolver().insert(uri, values);
    //getContentResolver().update(uri, values, "", new String[]{});

  }
}
