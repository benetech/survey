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

package org.opendatakit.survey.android.tasks;

import org.opendatakit.survey.android.listeners.DeleteFormsListener;
import org.opendatakit.survey.android.provider.FormsProviderAPI;

import android.app.Application;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Task responsible for deleting selected forms.
 *
 * @author norman86@gmail.com
 * @author mitchellsundt@gmail.com
 *
 */
public class DeleteFormsTask extends AsyncTask<Long, Void, Integer> {
	private static final String t = "DeleteFormsTask";

   private Application appContext;
	private DeleteFormsListener dl;
   private String appName;

	private int successCount = 0;

	@Override
	protected Integer doInBackground(Long... params) {
		int deleted = 0;

		if (params == null || appContext == null || dl == null) {
			return deleted;
		}

		// delete files from database and then from file system
		for (int i = 0; i < params.length; i++) {
			if (isCancelled()) {
				break;
			}
			try {
				Uri deleteForm = Uri.withAppendedPath(
				    Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI,appName),
				      params[i].toString());
				deleted += appContext.getContentResolver().delete(deleteForm, null, null);
			} catch (Exception ex) {
				Log.e(t, "Exception during delete of: " + params[i].toString()
						+ " exception: " + ex.toString());
			}
		}
		successCount = deleted;
		return deleted;
	}

	@Override
	protected void onPostExecute(Integer result) {
	  appContext = null;
		if (dl != null) {
			dl.deleteFormsComplete(result);
		}
	}

	@Override
	protected void onCancelled(Integer result) {
	  appContext = null;
		// can be null if cancelled before task executes
		if ( result == null ) {
			successCount = 0;
		}
		if (dl != null) {
			dl.deleteFormsComplete(successCount);
		}
	}

   public int getDeleteCount() {
      return successCount;
   }

	public void setDeleteListener(DeleteFormsListener listener) {
		dl = listener;
	}

   public void setAppName(String appName) {
     synchronized (this) {
       this.appName = appName;
     }
   }

   public String getAppName() {
     return appName;
   }

   public void setApplication(Application appContext) {
     synchronized (this) {
       this.appContext = appContext;
     }
   }

   public Application getApplication() {
     return appContext;
   }
}