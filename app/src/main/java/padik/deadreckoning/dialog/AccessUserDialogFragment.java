package padik.deadreckoning.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import padik.deadreckoning.R;
import padik.deadreckoning.activity.UserActivity;

public class AccessUserDialogFragment extends DialogFragment {

    private static final String ARG_USER_NAME = "user_name";
    private static final String ARG_STRIDE_LENGTH = "stride_length";

    private String userName;
    private String strideLength;

    /**
     * Creates a new instance of the dialog.
     */
    public static AccessUserDialogFragment newInstance(
            String userName,
            String strideLength
    ) {

        AccessUserDialogFragment fragment =
                new AccessUserDialogFragment();

        Bundle args = new Bundle();

        args.putString(ARG_USER_NAME, userName);
        args.putString(ARG_STRIDE_LENGTH, strideLength);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args != null) {

            userName = args.getString(
                    ARG_USER_NAME,
                    ""
            );

            strideLength = args.getString(
                    ARG_STRIDE_LENGTH,
                    ""
            );
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(
            @Nullable Bundle savedInstanceState
    ) {

        String dialogMessage =
                getString(
                        R.string.go_to_user_settings,
                        userName
                );

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.user_settings)
                .setMessage(dialogMessage)

                .setNegativeButton(
                        R.string.cancel,
                        null
                )

                .setPositiveButton(
                        R.string.okay,
                        (dialog, which) -> openUserSettings()
                )

                .create();
    }

    /**
     * Opens the User Settings screen.
     */
    private void openUserSettings() {

        Intent intent =
                new Intent(
                        requireContext(),
                        UserActivity.class
                );

        intent.putExtra(
                ARG_USER_NAME,
                userName
        );

        intent.putExtra(
                ARG_STRIDE_LENGTH,
                strideLength
        );

        startActivity(intent);
    }

    /**
     * Legacy setter support.
     *
     * Prefer using newInstance().
     */
    @Deprecated
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Legacy setter support.
     *
     * Prefer using newInstance().
     */
    @Deprecated
    public void setStrideLength(String strideLength) {
        this.strideLength = strideLength;
    }
}