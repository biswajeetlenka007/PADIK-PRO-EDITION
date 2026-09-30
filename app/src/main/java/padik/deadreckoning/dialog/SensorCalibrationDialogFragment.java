package padik.deadreckoning.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import padik.deadreckoning.interfaces.OnPreferredStepCounterListener;

public class SensorCalibrationDialogFragment
        extends DialogFragment {

    private OnPreferredStepCounterListener
            onPreferredStepCounterListener;

    private String[] stepList;


    public SensorCalibrationDialogFragment() {
        // Required empty constructor
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        /*
         * Make sure stepList is not null.
         */
        if (stepList == null) {
            stepList = new String[0];
        }

        AlertDialog.Builder builder =
                new AlertDialog.Builder(requireActivity());

        builder.setTitle(
                "Pick the sensitivity that best " +
                        "matches your step count:"
        );

        builder.setItems(
                stepList,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which
                    ) {

                        if (onPreferredStepCounterListener
                                != null) {

                            onPreferredStepCounterListener
                                    .onPreferredStepCounter(
                                            which
                                    );
                        }
                    }
                }
        );

        builder.setNegativeButton(
                android.R.string.cancel,
                null
        );

        return builder.create();
    }


    public void setOnPreferredStepCounterListener(
            OnPreferredStepCounterListener listener
    ) {

        this.onPreferredStepCounterListener =
                listener;
    }


    public void setStepList(
            String[] stepList
    ) {

        if (stepList != null) {

            this.stepList =
                    stepList.clone();

        } else {

            this.stepList =
                    new String[0];
        }
    }
}
