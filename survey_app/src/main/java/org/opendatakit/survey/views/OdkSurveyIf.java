/*
 * Copyright (C) 2012-2013 University of Washington
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

package org.opendatakit.survey.views;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.survey.R;
import org.opendatakit.utilities.ODKFileUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * The class mapped to 'odkSurvey' in the Javascript
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class OdkSurveyIf {
  Context mContext;
  public static final String t = "OdkSurveyIf";

  private WeakReference<OdkSurvey> weakSurvey;

  OdkSurveyIf(OdkSurvey odkData, Context c) {
    weakSurvey = new WeakReference<OdkSurvey>(odkData);
    mContext=c;
  }

  private boolean isInactive() {
    return (weakSurvey.get() == null) || (weakSurvey.get().isInactive());
  }

  /**
   * Clear the auxillary hash. The auxillary hash is used to pass initialization
   * parameters into a form. Once those parameters have been safely updated to the
   * database or stored in session variables, ODK Survey will clear them so that
   * the form logic can update or revise their values.
   */
  @android.webkit.JavascriptInterface
  public void clearAuxillaryHash() {
    if (isInactive()) return;
    weakSurvey.get().clearAuxillaryHash();
  }

  /**
   * Clear the instanceId. The ODK Survey webpage is no longer associated with a
   * specific instanceId or rowId.
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void clearInstanceId(String refId) {
    if (isInactive()) return;
    weakSurvey.get().clearInstanceId(refId);
  }

  /**
   * If refId is null, clears the instanceId. If refId matches the current
   * refId, sets the instanceId.
   *
   * @param refId
   * @param instanceId
   */
  @android.webkit.JavascriptInterface
  public void setInstanceId(String refId, String instanceId) {
    if (isInactive()) return;
    weakSurvey.get().setInstanceId(refId, instanceId);
  }

  /**
   * Get the instanceId for this web page.
   * Returns null if the refId does not match.
   *
   * @param refId
   * @return
   */
  @android.webkit.JavascriptInterface
  public String getInstanceId(String refId) {
    if (isInactive()) return null;
    return weakSurvey.get().getInstanceId(refId);
  }

  /**
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void pushSectionScreenState(String refId) {
    if (isInactive()) return;
    weakSurvey.get().pushSectionScreenState(refId);
  }

  /**
   *
   * @param refId
   * @param screenPath
   * @param state
   */
  @android.webkit.JavascriptInterface
  public void setSectionScreenState(String refId, String screenPath, String state) {
    if (isInactive()) return;
    weakSurvey.get().setSectionScreenState(refId, screenPath, state);
  }

  /**
   *
   * @param refId
   */
  @android.webkit.JavascriptInterface
  public void clearSectionScreenState(String refId) {
    if (isInactive()) return;
    weakSurvey.get().clearSectionScreenState(refId);
  }

  @android.webkit.JavascriptInterface
  public String getControllerState(String refId) {
    if (isInactive()) return null;
    return weakSurvey.get().getControllerState(refId);
  }

  @android.webkit.JavascriptInterface
  public String getScreenPath(String refId) {
    if (isInactive()) return null;
    return weakSurvey.get().getScreenPath(refId);
  }

  @android.webkit.JavascriptInterface
  public boolean hasScreenHistory(String refId) {
    if (isInactive()) return false;
    return weakSurvey.get().hasScreenHistory(refId);
  }

  @android.webkit.JavascriptInterface
  public String popScreenHistory(String refId) {
    if (isInactive()) return null;
    return weakSurvey.get().popScreenHistory(refId);
  }

  @android.webkit.JavascriptInterface
  public boolean hasSectionStack(String refId) {
    if (isInactive()) return false;
    return weakSurvey.get().hasSectionStack(refId);
  }

  @android.webkit.JavascriptInterface
  public String popSectionStack(String refId) {
    if (isInactive()) return null;
    return weakSurvey.get().popSectionStack(refId);
  }

  @android.webkit.JavascriptInterface
  public void frameworkHasLoaded(String refId, boolean outcome) {
    if (isInactive()) return;
    weakSurvey.get().frameworkHasLoaded(refId, outcome);
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesCompleted(String refId, String instanceId) {
    if (isInactive()) return;
    weakSurvey.get().ignoreAllChangesCompleted(refId, instanceId);
  }

  @android.webkit.JavascriptInterface
  public void ignoreAllChangesFailed(String refId, String instanceId) {
    if (isInactive()) return;
    weakSurvey.get().ignoreAllChangesFailed(refId, instanceId);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesCompleted(String refId, String instanceId, boolean asComplete) {
    if (isInactive()) return;
    weakSurvey.get().saveAllChangesCompleted(refId, instanceId, asComplete);
  }

  @android.webkit.JavascriptInterface
  public void saveAllChangesFailed(String refId, String instanceId) {
    if (isInactive()) return;
    weakSurvey.get().saveAllChangesFailed(refId, instanceId);
 }

  @JavascriptInterface
  public void syncSelectedForms(String ids) {
    Gson g = new Gson();
    Type listType = new TypeToken<ArrayList<String>>() {
    }.getType();
    ArrayList<String> list = g.fromJson(ids, listType);
    if (list.isEmpty()) {
      Toast.makeText(mContext, R.string.no_forms_selected_for_sync, Toast.LENGTH_SHORT).show();
    } else {
      Intent syncIntent = new Intent();
      syncIntent.setComponent(new ComponentName(
              IntentConsts.Sync.APPLICATION_NAME,
              IntentConsts.Sync.ACTIVITY_NAME));
      syncIntent.setAction(Intent.ACTION_DEFAULT);
      Bundle bundle = new Bundle();
      bundle.putString(IntentConsts.INTENT_KEY_APP_NAME, ODKFileUtils.getOdkDefaultAppName());
      syncIntent.putStringArrayListExtra("ids", list);
      syncIntent.putExtras(bundle);
      mContext.startActivity(syncIntent);
    }
  }

  @JavascriptInterface
  public String getSubmenuPage() {
    return weakSurvey.get().getSubmenuPage();
  }
}