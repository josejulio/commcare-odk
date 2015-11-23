package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.activities.GeoPointActivity;
import org.odk.collect.android.logic.PendingCalloutInterface;

import java.text.DecimalFormat;

/**
 * GeoPointWidget is the widget that allows the user to get GPS readings.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class GeoPointWidget extends QuestionWidget {
    private final Button mGetLocationButton;

    private final TextView mStringAnswer;
    private final TextView mAnswerDisplay;
    private boolean mUseMaps;
    public static final String LOCATION = "gp";

    private PendingCalloutInterface pendingCalloutInterface;

    public GeoPointWidget(Context context, final FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);

        this.pendingCalloutInterface = pic;

        mUseMaps = false;
        String appearance = prompt.getAppearanceHint();

        setOrientation(LinearLayout.VERTICAL);

        mStringAnswer = new TextView(getContext());
        mAnswerDisplay = new TextView(getContext());
        mAnswerDisplay.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mAnswerDisplay.setGravity(Gravity.CENTER);

        Spannable locButtonText;
        boolean viewButtonEnabled;

        String s = prompt.getAnswerText();
        if (s != null && !("".equals(s))) {
            setBinaryData(s);

            locButtonText = StringUtils.getStringSpannableRobust(getContext(),
                    R.string.replace_location);
            viewButtonEnabled = true;
        } else {
            locButtonText = StringUtils.getStringSpannableRobust(getContext(),
                    R.string.get_location);
            viewButtonEnabled = false;
        }

        mGetLocationButton = new Button(getContext());
        WidgetUtils.setupButton(mGetLocationButton,
                locButtonText,
                mAnswerFontsize,
                !prompt.isReadOnly());

        mGetLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i;
                i = new Intent(getContext(), GeoPointActivity.class);
                ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.LOCATION_CAPTURE);
                pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
            }
        });

        addView(mGetLocationButton);
        addView(mAnswerDisplay);
    }


    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        mAnswerDisplay.setText(null);
        mGetLocationButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.get_location));

    }


    @Override
    public IAnswerData getAnswer() {
        String s = mStringAnswer.getText().toString();
        if (s == null || s.equals("")) {
            return null;
        } else {
            try {
                // segment lat and lon
                String[] sa = s.split(" ");
                double gp[] = new double[4];
                gp[0] = Double.valueOf(sa[0]);
                gp[1] = Double.valueOf(sa[1]);
                gp[2] = Double.valueOf(sa[2]);
                gp[3] = Double.valueOf(sa[3]);

                return new GeoPointData(gp);
            } catch (Exception NumberFormatException) {
                return null;
            }
        }
    }


    private String truncateDouble(String s) {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(Double.valueOf(s));
    }


    private String formatGps(double coordinates, String type) {
        String location = Double.toString(coordinates);
        String degreeSign = "\u00B0";
        String degree = location.substring(0, location.indexOf(".")) + degreeSign;
        location = "0." + location.substring(location.indexOf(".") + 1);
        double temp = Double.valueOf(location) * 60;
        location = Double.toString(temp);
        String mins = location.substring(0, location.indexOf(".")) + "'";

        location = "0." + location.substring(location.indexOf(".") + 1);
        temp = Double.valueOf(location) * 60;
        location = Double.toString(temp);
        String secs = location.substring(0, location.indexOf(".")) + '"';
        if (type.equalsIgnoreCase("lon")) {
            if (degree.startsWith("-")) {
                degree = "W " + degree.replace("-", "") + mins + secs;
            } else
                degree = "E " + degree.replace("-", "") + mins + secs;
        } else {
            if (degree.startsWith("-")) {
                degree = "S " + degree.replace("-", "") + mins + secs;
            } else
                degree = "N " + degree.replace("-", "") + mins + secs;
        }
        return degree;
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    @Override
    public void setBinaryData(Object answer) {
        String s = (String)answer;
        mStringAnswer.setText(s);

        String[] sa = s.split(" ");
        mAnswerDisplay.setText(
                StringUtils.getStringSpannableRobust(getContext(), R.string.latitude) +
                        ": " + formatGps(Double.parseDouble(sa[0]), "lat") + "\n" +
                        StringUtils.getStringSpannableRobust(getContext(), R.string.longitude) +
                        ": " + formatGps(Double.parseDouble(sa[1]), "lon") + "\n" +
                        StringUtils.getStringSpannableRobust(getContext(), R.string.altitude) +
                        ": " + truncateDouble(sa[2]) + "m\n" +
                        StringUtils.getStringSpannableRobust(getContext(), R.string.accuracy) +
                        ": " + truncateDouble(sa[3]) + "m");
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mGetLocationButton.setOnLongClickListener(l);
        mStringAnswer.setOnLongClickListener(l);
        mAnswerDisplay.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mGetLocationButton.setOnLongClickListener(null);
        mStringAnswer.setOnLongClickListener(null);
        mAnswerDisplay.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mGetLocationButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
        mAnswerDisplay.cancelLongPress();
    }

}
