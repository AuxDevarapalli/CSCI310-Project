package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.team04.splitsmart.R;
import com.team04.splitsmart.SplitSmartApp;
import com.team04.splitsmart.viewmodel.AppContainer;
import com.team04.splitsmart.viewmodel.BaseViewModel;
import com.team04.splitsmart.viewmodel.ViewModelFactory;

/**
 * Common setup for every screen: edge-to-edge drawing, ViewModel creation through the
 * {@link ViewModelFactory}, a session check, and one place to show messages.
 */
public abstract class BaseActivity extends AppCompatActivity {
    private ViewModelFactory factory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
    }

    /**
     * Sends the user to the login screen when nobody is logged in (for example after logging
     * out on this device). Returns false when the current screen should stop setting itself up.
     */
    protected boolean ensureSignedIn() {
        AppContainer container = SplitSmartApp.from(this).getContainer();
        if (container != null && container.getAuthRepository().isLoggedIn()) {
            return true;
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        return false;
    }

    /** Sets the layout and keeps its {@code root} view clear of the system bars. */
    protected void setContentWithInsets(@LayoutRes int layout) {
        setContentView(layout);
        UiUtils.applySystemBarInsets(findViewById(R.id.root));
    }

    protected ViewModelFactory getViewModelFactory() {
        if (factory == null) {
            factory = new ViewModelFactory(SplitSmartApp.from(this).getContainer());
        }
        return factory;
    }

    protected <T extends ViewModel> T obtainViewModel(Class<T> type) {
        return new ViewModelProvider(this, getViewModelFactory()).get(type);
    }

    protected void showMessage(String message) {
        UiUtils.showSnackbar(findViewById(R.id.root), message);
    }

    /** Shows the ViewModel's one-time error and info messages as snackbars. */
    protected void observeMessages(BaseViewModel viewModel) {
        viewModel.getError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showMessage(message);
            }
        });
        viewModel.getInfo().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showMessage(message);
            }
        });
    }
}
