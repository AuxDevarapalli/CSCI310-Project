package com.team04.splitsmart.ui;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.team04.splitsmart.SplitSmartApp;
import com.team04.splitsmart.viewmodel.BaseViewModel;
import com.team04.splitsmart.viewmodel.ViewModelFactory;

/** Common setup for the group tabs: ViewModel creation and message display. */
public abstract class BaseFragment extends Fragment {

    protected ViewModelFactory getViewModelFactory() {
        return new ViewModelFactory(SplitSmartApp.from(requireContext()).getContainer());
    }

    /** A ViewModel owned by this fragment. */
    protected <T extends ViewModel> T obtainViewModel(Class<T> type) {
        return new ViewModelProvider(this, getViewModelFactory()).get(type);
    }

    /** A ViewModel shared with the hosting activity. */
    protected <T extends ViewModel> T obtainActivityViewModel(Class<T> type) {
        return new ViewModelProvider(requireActivity(), getViewModelFactory()).get(type);
    }

    protected void showMessage(String message) {
        if (getView() != null) {
            UiUtils.showSnackbar(getView(), message);
        }
    }

    protected void observeMessages(BaseViewModel viewModel) {
        viewModel.getError().observe(getViewLifecycleOwner(), event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showMessage(message);
            }
        });
        viewModel.getInfo().observe(getViewLifecycleOwner(), event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                showMessage(message);
            }
        });
    }
}
