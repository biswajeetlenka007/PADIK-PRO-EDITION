package padik.deadreckoning.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import padik.deadreckoning.R;
import padik.deadreckoning.activity.StepCalibrationActivity;
import padik.deadreckoning.interfaces.OnUserUpdateListener;

public class UserDetailsDialogFragment extends DialogFragment {

    public static final String USER_TAG = "USER";
    public static final String STRIDE_LENGTH_TAG = "STRIDE_LENGTH_TAG";
    public static final String PREFERRED_STEP_COUNTER = "PREFERRED_STEP_COUNTER";

    private static final String STEP_CALIBRATION_USER_NAME = "user_name";
    private static final int STEP_CALIBRATION_REQUEST_CODE = 0;

    private OnUserUpdateListener onUserUpdateListener;
    private boolean addingUser;
    private String userName;

    public UserDetailsDialogFragment() {
        // Required empty constructor
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View dialogBox = View.inflate(requireContext(), R.layout.dialog_user_details, null);

        AlertDialog.Builder alertDialogBuilder =
                new AlertDialog.Builder(requireContext());

        alertDialogBuilder.setView(dialogBox);

        // Find views
        TextView textName = dialogBox.findViewById(R.id.textDialogName);
        EditText textStrideLength = dialogBox.findViewById(R.id.textDialogStride);
        TextView textStrideLengthMessage =
                dialogBox.findViewById(R.id.textDialogStrideMessage);

        // Set information message
        textStrideLengthMessage.setText(R.string.user_details_msg);

        /*
         * If editing an existing user:
         * - Don't allow the user name to be changed.
         * - Display the existing name.
         */
        if (!addingUser) {
            textName.setEnabled(false);

            if (userName != null) {
                textName.setText(userName);
            }
        }

        /*
         * CANCEL BUTTON
         */
        alertDialogBuilder.setNegativeButton(
                R.string.cancel,
                (dialog, which) -> dismiss()
        );

        /*
         * OK BUTTON
         */
        alertDialogBuilder.setPositiveButton(
                R.string.okay,
                null
        );

        /*
         * STRIDE LENGTH CALIBRATION BUTTON
         */
        alertDialogBuilder.setNeutralButton(
                R.string.stride_length_calc,
                null
        );

        AlertDialog dialog = alertDialogBuilder.create();

        /*
         * Set OK button listener after creating the dialog.
         *
         * This is important because the default AlertDialog behavior
         * automatically dismisses the dialog when a positive button
         * is clicked. We want to keep it open when validation fails.
         */
        dialog.setOnShowListener(dialogInterface -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {

                        String enteredUserName =
                                textName.getText().toString().trim();

                        String strideLength =
                                textStrideLength.getText().toString().trim();

                        // Validate user name
                        if (checkInvalidUserName(enteredUserName)) {

                            Toast.makeText(
                                    requireContext(),
                                    R.string.invalid_name,
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        // Validate stride length
                        if (checkInvalidStrideLength(strideLength)) {

                            Toast.makeText(
                                    requireContext(),
                                    R.string.invalid_stride,
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        /*
                         * Prepare data for the calling Activity.
                         */
                        Bundle bundle = new Bundle();

                        bundle.putString(USER_TAG, enteredUserName);
                        bundle.putString(STRIDE_LENGTH_TAG, strideLength);

                        /*
                         * Only new users need a default step counter.
                         */
                        if (addingUser) {
                            bundle.putString(
                                    PREFERRED_STEP_COUNTER,
                                    "default"
                            );
                        }

                        /*
                         * Notify the Activity only if a listener
                         * has been attached.
                         */
                        if (onUserUpdateListener != null) {
                            onUserUpdateListener.onUserUpdateListener(bundle);
                        }

                        dismiss();
                    });

            /*
             * STRIDE LENGTH CALIBRATION
             */
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> {

                        String enteredUserName =
                                textName.getText().toString().trim();

                        if (checkInvalidUserName(enteredUserName)) {

                            Toast.makeText(
                                    requireContext(),
                                    R.string.invalid_name,
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        /*
                         * Start the stride calibration Activity.
                         */
                        Intent intent = new Intent(
                                requireContext(),
                                StepCalibrationActivity.class
                        );

                        intent.putExtra(
                                STEP_CALIBRATION_USER_NAME,
                                enteredUserName
                        );

                        startActivityForResult(
                                intent,
                                STEP_CALIBRATION_REQUEST_CODE
                        );

                        dismiss();
                    });
        });

        return dialog;
    }

    /**
     * Sets the listener that receives updated user information.
     */
    public void setOnUserUpdateListener(
            OnUserUpdateListener onUserUpdateListener) {

        this.onUserUpdateListener = onUserUpdateListener;
    }

    /**
     * Determines whether a new user is being created.
     */
    public void setAddingUser(boolean addingUser) {
        this.addingUser = addingUser;
    }

    /**
     * Sets the existing user's name.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Checks whether the stride length is invalid.
     */
    private boolean checkInvalidStrideLength(String strideLength) {
        return TextUtils.isEmpty(strideLength);
    }

    /**
     * Checks whether the user name is invalid.
     */
    private boolean checkInvalidUserName(String userName) {
        return TextUtils.isEmpty(userName);
    }
}