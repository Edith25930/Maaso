package com.pca.lesotho.citizen.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfficerInfoStepFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_officer, container, false);
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        setText(view, R.id.inputOfficerName, draft.officerName);
        setText(view, R.id.inputOfficerRank, draft.officerRank);
        setText(view, R.id.inputBadgeNumber, draft.badgeNumber);
        setText(view, R.id.inputStation, draft.officerStationName);
        CheckBox unknown = view.findViewById(R.id.checkboxUnknownOfficer);
        unknown.setChecked(draft.officerUnknown);
        unknown.setOnCheckedChangeListener((button, checked) -> setOfficerFieldsEnabled(view, !checked));
        setOfficerFieldsEnabled(view, !draft.officerUnknown);

        LinearLayout witnessContainer = view.findViewById(R.id.witnessContainer);
        CheckBox noWitnesses = view.findViewById(R.id.checkboxNoWitnesses);
        noWitnesses.setChecked("[]".equals(draft.witnessesJson));
        noWitnesses.setOnCheckedChangeListener((button, checked) -> {
            witnessContainer.setVisibility(checked ? View.GONE : View.VISIBLE);
            view.findViewById(R.id.btnAddWitness).setEnabled(!checked);
            if (checked) witnessContainer.removeAllViews();
        });
        Type witnessType = new TypeToken<List<Map<String, String>>>() {}.getType();
        List<Map<String, String>> savedWitnesses = new Gson().fromJson(draft.witnessesJson, witnessType);
        if (savedWitnesses != null) {
            for (Map<String, String> witness : savedWitnesses) {
                addWitness(inflater, witnessContainer, witness.get("full_name"), witness.get("contact_number"));
            }
        }
        view.findViewById(R.id.btnAddWitness).setOnClickListener(v -> {
            noWitnesses.setChecked(false);
            addWitness(inflater, witnessContainer, null, null);
        });
        return view;
    }

    private void setText(View view, int id, String value) {
        ((EditText) view.findViewById(id)).setText(value == null ? "" : value);
    }

    private void setOfficerFieldsEnabled(View view, boolean enabled) {
        view.findViewById(R.id.inputOfficerName).setEnabled(enabled);
        view.findViewById(R.id.inputOfficerRank).setEnabled(enabled);
        view.findViewById(R.id.inputBadgeNumber).setEnabled(enabled);
        view.findViewById(R.id.inputStation).setEnabled(enabled);
    }

    private void addWitness(LayoutInflater inflater, LinearLayout container, String name, String contact) {
        View row = inflater.inflate(R.layout.item_witness_row, container, false);
        EditText witnessName = row.findViewById(R.id.inputWitnessName);
        EditText witnessContact = row.findViewById(R.id.inputWitnessContact);
        witnessName.setText(name == null ? "" : name);
        witnessContact.setText(contact == null ? "" : contact);
        row.findViewById(R.id.btnRemoveWitness).setOnClickListener(v -> container.removeView(row));
        container.addView(row);
    }

    public boolean saveToDraft() {
        View view = getView();
        if (view == null) return true;
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        draft.officerUnknown = ((CheckBox) view.findViewById(R.id.checkboxUnknownOfficer)).isChecked();
        draft.officerName = ((EditText) view.findViewById(R.id.inputOfficerName)).getText().toString().trim();
        draft.officerRank = ((EditText) view.findViewById(R.id.inputOfficerRank)).getText().toString().trim();
        draft.badgeNumber = ((EditText) view.findViewById(R.id.inputBadgeNumber)).getText().toString().trim();
        draft.officerStationName = ((EditText) view.findViewById(R.id.inputStation)).getText().toString().trim();

        List<Map<String, String>> witnesses = new ArrayList<>();
        LinearLayout container = view.findViewById(R.id.witnessContainer);
        boolean noWitnesses = ((CheckBox) view.findViewById(R.id.checkboxNoWitnesses)).isChecked();
        for (int i = 0; !noWitnesses && i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            String name = ((EditText) row.findViewById(R.id.inputWitnessName)).getText().toString().trim();
            String contact = ((EditText) row.findViewById(R.id.inputWitnessContact)).getText().toString().trim();
            if (name.isEmpty() && contact.isEmpty()) continue;
            if (name.isEmpty()) {
                ((EditText) row.findViewById(R.id.inputWitnessName)).setError(getString(R.string.error_required_field));
                return false;
            }
            Map<String, String> witness = new HashMap<>();
            witness.put("full_name", name);
            witness.put("contact_number", contact);
            witnesses.add(witness);
        }
        draft.witnessesJson = new Gson().toJson(witnesses);
        if (witnesses.isEmpty() && !noWitnesses) {
            ((CheckBox) view.findViewById(R.id.checkboxNoWitnesses)).setChecked(true);
        }
        return true;
    }
}
