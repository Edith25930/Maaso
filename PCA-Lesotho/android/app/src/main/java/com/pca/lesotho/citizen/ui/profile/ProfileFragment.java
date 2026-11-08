package com.pca.lesotho.citizen.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.DeviceTokenRegistrar;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.auth.LoginActivity;
import com.pca.lesotho.citizen.ui.onboarding.OnboardingActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        SessionManager session = new SessionManager(requireContext());

        TextView name = view.findViewById(R.id.textProfileName);
        name.setText(session.getFullName());
        TextView languageButton = view.findViewById(R.id.btnProfileToggleLanguage);
        languageButton.setText(AppLanguage.isSesotho(requireContext())
                ? R.string.switch_to_english : R.string.switch_to_sesotho);
        ((TextView) view.findViewById(R.id.textProfilePhone)).setText(session.getPhoneNumber());
        View verifyEmail = view.findViewById(R.id.btnProfileVerifyEmail);
        verifyEmail.setVisibility(session.getEmail().isEmpty() || session.isGuest() ? View.GONE : View.VISIBLE);
        verifyEmail.setOnClickListener(v -> ApiClient.getService()
                .requestEmailVerification(ApiClient.bearer(session.getToken()))
                .enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        int message = response.isSuccessful() ? R.string.verification_email_requested : R.string.error_generic;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().has("data")
                                && response.body().getAsJsonObject("data").has("verified")
                                && response.body().getAsJsonObject("data").get("verified").getAsBoolean()) {
                            message = R.string.email_already_verified;
                        }
                        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show();
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                        android.widget.Toast.makeText(requireContext(), R.string.error_no_internet,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                }));
        View staffDashboard = view.findViewById(R.id.btnProfileStaffDashboard);
        staffDashboard.setVisibility(session.isLoggedIn() && !session.isGuest()
                && ("admin".equals(session.getRole()) || "investigator".equals(session.getRole()))
                ? View.VISIBLE : View.GONE);
        staffDashboard.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.pca.lesotho.citizen.ui.staff.StaffDashboardActivity.class)));
        view.findViewById(R.id.btnProfileComplaints).setOnClickListener(v -> selectTab(R.id.nav_complaints));
        view.findViewById(R.id.btnProfileNotifications).setOnClickListener(v -> selectTab(R.id.nav_notifications));
        view.findViewById(R.id.btnProfileLearn).setOnClickListener(v -> selectTab(R.id.nav_learn));
        view.findViewById(R.id.btnProfileGettingStarted).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OnboardingActivity.class)));
        view.findViewById(R.id.btnProfileToggleLanguage).setOnClickListener(v ->
                AppLanguage.set(requireContext(), AppLanguage.isSesotho(requireContext()) ? "en" : "st"));

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            String token = session.getToken();
            if (token != null) {
                DeviceTokenRegistrar.unregister(requireContext(), ApiClient.bearer(token));
                ApiClient.getService().logout(ApiClient.bearer(token)).enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) { }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) { }
                });
            }
            session.clear();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }

    private void selectTab(int itemId) {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                requireActivity().findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(itemId);
    }
}
