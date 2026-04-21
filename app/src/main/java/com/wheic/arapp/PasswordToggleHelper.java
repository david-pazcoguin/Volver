package com.wheic.arapp;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Reusable "SHOW/HIDE" password toggle wiring shared by Login, Register,
 * and AccountSetting activities.
 */
public final class PasswordToggleHelper {

    private static final String SHOW = "SHOW";
    private static final String HIDE = "HIDE";

    private PasswordToggleHelper() { /* utility */ }

    /** Wires a TextView to toggle the visibility of the supplied EditText. */
    public static void attach(TextView toggle, EditText field) {
        if (toggle == null || field == null) return;
        toggle.setText(SHOW);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        toggle.setOnClickListener(v -> {
            boolean currentlyHidden = SHOW.contentEquals(toggle.getText());
            toggle.setText(currentlyHidden ? HIDE : SHOW);
            field.setTransformationMethod(currentlyHidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            // Keep the cursor at the end after changing transformation
            field.setSelection(field.getText().length());
        });
    }
}
