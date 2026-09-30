        package padik.deadreckoning.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import padik.deadreckoning.interfaces.OnPreferredStepCounterListener;

public class StepCalibrationDialogFragment
        extends DialogFragment {

    private static final String TAG =
            "StepCalibrationDialog";

    private OnPreferredStepCounterListener
            onPreferredStepCounterListener;

    private String[] stepList;


    /**
     * Required empty constructor.
     */
    public StepCalibrationDialogFragment() {
    }


    /**
     * Creates the step-counter sensitivity dialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable Bundle savedInstanceState
    ) {

        /*
         * Prevent a crash if stepList has not been
         * supplied by the calling Activity/Fragment.
         */
        if (stepList == null) {

            stepList = new String[0];
        }

        AlertDialog.Builder builder =
                new AlertDialog.Builder(
                        requireContext()
                );

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

                        /*
                         * Notify the caller about the
                         * selected sensitivity.
                         */
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


    /**
     * Sets the listener that receives the selected
     * step-counter sensitivity.
     */
    public void setOnPreferredStepCounterListener(
            OnPreferredStepCounterListener listener
    ) {

        this.onPreferredStepCounterListener =
                listener;
    }


    /**
     * Sets the available step-counter sensitivities.
     *
     * A defensive copy is used so the original array
     * cannot unexpectedly modify the dialog.
     */
    public void setStepList(
            String[] stepList
    ) {

        if (stepList == null) {

            this.stepList =
                    new String[0];

        } else {

            this.stepList =
                    stepList.clone();
        }
    }


    /**
     * Returns the currently configured step list.
     */
    public String[] getStepList() {

        if (stepList == null) {

            return new String[0];
        }

        return stepList.clone();
    }
}
