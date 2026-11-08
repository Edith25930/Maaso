package com.pca.lesotho.citizen.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PersonalInfoStepFragment extends Fragment {
    private final List<Integer> districtIds = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_personal, container, false);
        SessionManager session = new SessionManager(requireContext());
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        setIfEmpty(view, R.id.inputFullName, draft.complainantName, session.getFullName());
        setIfEmpty(view, R.id.inputPhone, draft.complainantPhone, session.getPhoneNumber());
        setIfEmpty(view, R.id.inputIdNumber, draft.complainantIdNumber, null);
        setIfEmpty(view, R.id.inputCurrentLocation, draft.complainantLocation, null);
        Spinner gender = view.findViewById(R.id.spinnerGender);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                Arrays.asList(getString(R.string.form_gender_unspecified), getString(R.string.form_gender_female),
                        getString(R.string.form_gender_male)));
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gender.setAdapter(genderAdapter);
        gender.setSelection("female".equals(draft.complainantGender) ? 1
                : "male".equals(draft.complainantGender) ? 2 : 0);
        loadDistricts(view.findViewById(R.id.spinnerDistrict), draft.complainantDistrictId);
        return view;
    }

    private void loadDistricts(Spinner spinner, Integer selectedId) {
        ApiClient.getService().getDistricts().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null
                        || !response.body().has("data")) return;
                List<String> labels = new ArrayList<>();
                labels.add(getString(R.string.form_choose_district));
                districtIds.clear();
                districtIds.add(null);
                response.body().getAsJsonArray("data").forEach(row -> {
                    JsonObject district = row.getAsJsonObject();
                    String nameKey = AppLanguage.isSesotho(requireContext()) ? "name_st" : "name_en";
                    labels.add(district.has(nameKey) && !district.get(nameKey).isJsonNull()
                            ? district.get(nameKey).getAsString() : district.get("name_en").getAsString());
                    districtIds.add(row.getAsJsonObject().get("district_id").getAsInt());
                });
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, labels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                if (selectedId != null && districtIds.contains(selectedId)) {
                    spinner.setSelection(districtIds.indexOf(selectedId));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (isAdded()) spinner.setPrompt(getString(R.string.form_choose_district));
            }
        });
    }

    private void setIfEmpty(View view, int id, String saved, String fallback) {
        EditText input = view.findViewById(id);
        input.setText(saved != null ? saved : fallback);
    }

    public boolean saveToDraft() {
        View view = getView();
        if (view == null) return true;
        EditText name = view.findViewById(R.id.inputFullName);
        EditText phone = view.findViewById(R.id.inputPhone);
        if (name.getText().toString().trim().isEmpty()) {
            name.setError(getString(R.string.error_required_field));
            return false;
        }
        if (phone.getText().toString().trim().isEmpty()) {
            phone.setError(getString(R.string.error_required_field));
            return false;
        }
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        draft.complainantName = name.getText().toString().trim();
        draft.complainantPhone = phone.getText().toString().trim();
        draft.complainantIdNumber = ((EditText) view.findViewById(R.id.inputIdNumber)).getText().toString().trim();
        draft.complainantLocation = ((EditText) view.findViewById(R.id.inputCurrentLocation)).getText().toString().trim();
        int genderPosition = ((Spinner) view.findViewById(R.id.spinnerGender)).getSelectedItemPosition();
        draft.complainantGender = genderPosition == 1 ? "female" : genderPosition == 2 ? "male" : "prefer_not_to_say";
        Spinner district = view.findViewById(R.id.spinnerDistrict);
        int districtPosition = district.getSelectedItemPosition();
        draft.complainantDistrictId = districtPosition >= 0 && districtPosition < districtIds.size()
                ? districtIds.get(districtPosition) : null;
        return true;
    }
}
