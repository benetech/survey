package org.opendatakit.survey.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;

import org.opendatakit.consts.IntentConsts;
import org.opendatakit.survey.R;
import org.opendatakit.survey.activities.IOdkSurveyActivity;
import org.opendatakit.survey.activities.MainMenuActivity;


/**
 * Fragment displaying the main, front page of the app.
 */

public class FrontPageFragment extends Fragment implements Button.OnClickListener{
    public static final int ID = R.layout.front_page;

    private OnFragmentInteractionListener mListener;
    private Button send;

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(MainMenuActivity.ScreenList screen);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                       Bundle savedInstanceState) {
        View view = inflater.inflate(ID, container, false);
        send = (Button) view.findViewById(R.id.new_survey_btn);
        send.setOnClickListener(this);
        send = (Button) view.findViewById(R.id.submitted_btn);
        send.setOnClickListener(this);
        send = (Button) view.findViewById(R.id.in_progress_btn);
        send.setOnClickListener(this);
        send = (Button) view.findViewById(R.id.settings_btn);
        send.setOnClickListener(this);
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
                ((IOdkSurveyActivity) getActivity()).setSyncStateQueryValue(null);
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.in_progress_btn:
                ((IOdkSurveyActivity) getActivity()).setSyncStateQueryValue("new row");
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.submitted_btn:
                ((IOdkSurveyActivity) getActivity()).setSyncStateQueryValue("synced");
                mListener.onFragmentInteraction(MainMenuActivity.ScreenList.FORM_CHOOSER);
                break;
            case R.id.settings_btn:
                launchServicesSettings();
                break;
            default:
                ((IOdkSurveyActivity) getActivity()).setSyncStateQueryValue(null);
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
}
