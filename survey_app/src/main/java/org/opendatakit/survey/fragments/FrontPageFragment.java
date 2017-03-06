package org.opendatakit.survey.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.TextView;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.provider.FormsColumns;
import org.opendatakit.provider.FormsProviderAPI;
import org.opendatakit.provider.InstanceProviderAPI;
import org.opendatakit.survey.R;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.survey.activities.MainMenuActivity;


/**
 * Fragment displaying the main, front page of the app.
 */

public class FrontPageFragment extends Fragment implements Button.OnClickListener{
    public static final int ID = R.layout.front_page;

    private OnFragmentInteractionListener mListener;
    private Button subMenu;
    private TextView counter;

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(MainMenuActivity.ScreenList screen);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                       Bundle savedInstanceState) {
        View view = inflater.inflate(ID, container, false);
        subMenu = (Button) view.findViewById(R.id.new_survey_btn);
        subMenu.setOnClickListener(this);
        subMenu = (Button) view.findViewById(R.id.submitted_btn);
        subMenu.setOnClickListener(this);
        subMenu = (Button) view.findViewById(R.id.in_progress_btn);
        subMenu.setOnClickListener(this);
        subMenu = (Button) view.findViewById(R.id.settings_btn);
        subMenu.setOnClickListener(this);
        counter = (TextView) view.findViewById(R.id.in_progress_counter);
        counter.setText(Integer.toString(getFormInstancesCount("new_row")));
        counter = (TextView) view.findViewById(R.id.submitted_counter);
        counter.setText(Integer.toString(getFormInstancesCount("synced")));
        return view;
    }

    @Override public void onResume() {
        super.onResume();
    }

    @Override public void onPause() {
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.new_survey_btn:
                ((IOdkSurveyActivity) getActivity()).setSubmenuPage("new_survey");
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.in_progress_btn:
                ((IOdkSurveyActivity) getActivity()).setSubmenuPage("new_row");
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.submitted_btn:
                ((IOdkSurveyActivity) getActivity()).setSubmenuPage("synced");
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.settings_btn:
                launchServicesSettings();
                break;
            default:
                ((IOdkSurveyActivity) getActivity()).setSubmenuPage(null);
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
        }
    }

    private void launchServicesSettings(){
        Intent settingsIntent = new Intent();
        settingsIntent.setComponent(new ComponentName(IntentConsts.AppProperties.APPLICATION_NAME,
                IntentConsts.AppProperties.ACTIVITY_NAME));
        settingsIntent.setAction(Intent.ACTION_DEFAULT);
        this.startActivity(settingsIntent);
    }

    private int getFormInstancesCount(String arg){
        int counter = 0;

        Uri baseUri = Uri.withAppendedPath(FormsProviderAPI.CONTENT_URI, ((MainMenuActivity)getActivity()).getAppName());
        Cursor c = null;
        try {
            c = getActivity().getContentResolver().query(baseUri, null, null, null, null);

            if ( c.moveToFirst() ) {
                int idxTableId = c.getColumnIndex(FormsColumns.TABLE_ID);
                int idxFormVersion = c.getColumnIndex(FormsColumns.FORM_VERSION);
                do {
                    if(!c.getString(idxFormVersion).contains("sub")) {
                        Uri formUri = Uri.withAppendedPath(InstanceProviderAPI.CONTENT_URI, ((MainMenuActivity)getActivity()).getAppName() + "/"
                                + c.getString(idxTableId) );

                        String[] whereArgs = new String[] {
                                arg
                        };

                        Cursor f = getActivity().getContentResolver().query(formUri, null, "_sync_state=?", whereArgs, null);
                        counter += f.getCount();

                        if(f!=null) {
                            f.close();
                        }
                    }
                } while ( c.moveToNext());
            }
        } finally {
            if ( c != null && !c.isClosed() ) {
                c.close();
            }
        }
        return counter;
    }
}
