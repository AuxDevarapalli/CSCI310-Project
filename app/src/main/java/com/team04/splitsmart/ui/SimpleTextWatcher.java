package com.team04.splitsmart.ui;

import android.text.Editable;
import android.text.TextWatcher;

/** A {@link TextWatcher} that only reports the text after each change. */
public class SimpleTextWatcher implements TextWatcher {
    public interface OnTextChanged {
        void onTextChanged(String text);
    }

    private final OnTextChanged listener;

    public SimpleTextWatcher(OnTextChanged listener) {
        this.listener = listener;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        listener.onTextChanged(s == null ? "" : s.toString());
    }
}
