package padik.deadreckoning.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import padik.deadreckoning.R;

/**
 * DialogFragment used to display information related to step detection/calibration.
 */
public class StepInfoDialogFragment extends DialogFragment {

    private String message;

    public StepInfoDialogFragment() {
        // Required empty constructor
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        // Use a safe Fragment context instead of getActivity()
        AlertDialog.Builder dialogBuilder =
                new AlertDialog.Builder(requireContext());

        // Use a default message if none was provided
        String displayMessage = message;

        if (displayMessage == null || displayMessage.trim().isEmpty()) {
            displayMessage = "No information available.";
        }

        dialogBuilder
                .setMessage(displayMessage)
                .setPositiveButton(
                        R.string.okay,
                        (dialog, which) -> dismiss()
                );

        return dialogBuilder.create();
    }

    /**
     * Sets the message displayed in the dialog.
     *
     * @param message message to display
     */
    public void setDialogMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the current dialog message.
     */
    public String getDialogMessage() {
        return message;
    }
}