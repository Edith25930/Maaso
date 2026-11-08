package com.pca.lesotho.citizen.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.report.AiComplaintAssistActivity;
import com.pca.lesotho.citizen.ui.report.ReportComplaintActivity;
import com.pca.lesotho.citizen.ui.staff.StaffDashboardActivity;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        SessionManager session = new SessionManager(requireContext());
        TextView welcome = view.findViewById(R.id.textWelcome);
        welcome.setText(getString(R.string.welcome_user, session.getFullName()));

        View staffDashboard = view.findViewById(R.id.btnHomeStaffDashboard);
        staffDashboard.setVisibility(isStaff(session) ? View.VISIBLE : View.GONE);
        staffDashboard.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), StaffDashboardActivity.class)));
        view.findViewById(R.id.btnReportComplaint).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ReportComplaintActivity.class)));
        view.findViewById(R.id.btnAiAssist).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AiComplaintAssistActivity.class)));
        view.findViewById(R.id.btnMyComplaints).setOnClickListener(v -> selectTab(R.id.nav_complaints));
        view.findViewById(R.id.btnLearn).setOnClickListener(v -> selectTab(R.id.nav_learn));

        return view;
    }

    private void selectTab(int itemId) {
        com.google.android.material.bottomnavigation.BottomNavigationView nav =
                requireActivity().findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(itemId);
    }

    private static boolean isStaff(SessionManager session) {
        String role = session.getRole();
        return session.isLoggedIn() && !session.isGuest()
                && ("admin".equals(role) || "investigator".equals(role));
    }
}
