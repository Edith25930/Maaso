package com.pca.lesotho.citizen.ui.report;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ReportStepAdapter extends FragmentStateAdapter {

    public ReportStepAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case ReportComplaintActivity.STEP_PERSONAL: return new PersonalInfoStepFragment();
            case ReportComplaintActivity.STEP_INCIDENT: return new IncidentInfoStepFragment();
            case ReportComplaintActivity.STEP_OFFICER: return new OfficerInfoStepFragment();
            case ReportComplaintActivity.STEP_EVIDENCE: return new EvidenceStepFragment();
            default: return new ReviewStepFragment();
        }
    }

    @Override
    public int getItemCount() { return 5; }
}
