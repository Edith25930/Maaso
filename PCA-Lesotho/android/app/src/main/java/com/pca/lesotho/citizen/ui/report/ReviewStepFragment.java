package com.pca.lesotho.citizen.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;
import com.pca.lesotho.citizen.data.EvidenceAttachment;

import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;


import com.pca.lesotho.citizen.R;

/**
 * Step 5: collapsible summary of every prior step with per-section "Edit" (jumps the
 * parent ViewPager2 back to that step), the declaration checkbox, and the final
 * confirmation dialog shown by ReportComplaintActivity before calling the API.
 */
public class ReviewStepFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_review, container, false);
        bindSummary(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) bindSummary(getView());
    }

    private void bindSummary(View view) {
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        ((TextView) view.findViewById(R.id.summaryPersonal)).setText(
                value(draft.complainantName) + " · " + value(draft.complainantPhone)
                        + (empty(draft.complainantIdNumber) ? "" : "\nID: " + draft.complainantIdNumber));
        ((TextView) view.findViewById(R.id.summaryIncident)).setText(
                value(draft.incidentDate) + (empty(draft.incidentTime) ? "" : " " + draft.incidentTime)
                        + "\n" + value(draft.incidentLocationText)
                        + "\n" + (draft.categoryUnknown ? getString(R.string.form_unknown_complaint_type) : value(draft.categoryName))
                        + "\n" + value(draft.description));
        ((TextView) view.findViewById(R.id.summaryOfficer)).setText(draft.officerUnknown
                ? getString(R.string.form_unknown_officer)
                : value(draft.officerName) + " · " + value(draft.officerRank) + " · " + value(draft.badgeNumber));
        ((TextView) view.findViewById(R.id.summaryWitnesses)).setText(
                getString(R.string.form_witness_count, countWitnesses(draft.witnessesJson)));
        ((TextView) view.findViewById(R.id.summaryEvidence)).setText(
                getString(R.string.form_evidence_count, countEvidence(draft.evidenceJson)));
        CheckBox declaration = view.findViewById(R.id.checkboxDeclaration);
        declaration.setChecked(draft.declarationConfirmed);
        declaration.setOnCheckedChangeListener((button, checked) -> draft.declarationConfirmed = checked);
        view.findViewById(R.id.btnEditPersonal).setOnClickListener(v -> ((ReportComplaintActivity) requireActivity()).jumpToStep(ReportComplaintActivity.STEP_PERSONAL));
        view.findViewById(R.id.btnEditIncident).setOnClickListener(v -> ((ReportComplaintActivity) requireActivity()).jumpToStep(ReportComplaintActivity.STEP_INCIDENT));
        view.findViewById(R.id.btnEditOfficer).setOnClickListener(v -> ((ReportComplaintActivity) requireActivity()).jumpToStep(ReportComplaintActivity.STEP_OFFICER));
    }

    private int countWitnesses(String json) {
        try { return new Gson().fromJson(json, Object[].class).length; }
        catch (RuntimeException e) { return 0; }
    }

    private int countEvidence(String json) {
        try { return new Gson().fromJson(json, EvidenceAttachment[].class).length; }
        catch (RuntimeException e) { return 0; }
    }

    private boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private String value(String value) { return empty(value) ? "—" : value; }

    public boolean saveToDraft() {
        View view = getView();
        if (view == null) return true;
        CheckBox declaration = view.findViewById(R.id.checkboxDeclaration);
        if (!declaration.isChecked()) {
            Toast.makeText(requireContext(), R.string.error_declaration_required, Toast.LENGTH_LONG).show();
            return false;
        }
        ((ReportComplaintActivity) requireActivity()).getDraft().declarationConfirmed = true;
        return true;
    }
}
