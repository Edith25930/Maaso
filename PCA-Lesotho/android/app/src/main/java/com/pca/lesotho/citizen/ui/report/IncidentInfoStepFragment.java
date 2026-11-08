package com.pca.lesotho.citizen.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;
import com.pca.lesotho.citizen.network.ApiClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Step 2: complaint type (8 categories + "I don't know"), incident date/time, district,
 * location, police station, description, and the "Need help describing your complaint?"
 * entry point into AI Complaint Assistance.
 */
public class IncidentInfoStepFragment extends Fragment {
    private final List<Integer> categoryIds = new ArrayList<>();
    private final List<Integer> districtIds = new ArrayList<>();
    private ActivityResultLauncher<Intent> assistantLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assistantLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != android.app.Activity.RESULT_OK
                            || result.getData() == null || getView() == null) {
                        return;
                    }
                    String reply = result.getData().getStringExtra("assistant_reply");
                    if (reply != null && !reply.trim().isEmpty()) {
                        ((EditText) getView().findViewById(R.id.inputDescription)).setText(reply);
                        ((ReportComplaintActivity) requireActivity()).getDraft().description = reply;
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_incident, container, false);
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();

        EditText incidentDate = view.findViewById(R.id.inputIncidentDate);
        incidentDate.setText(draft.incidentDate == null ? "" : draft.incidentDate);
        incidentDate.setOnClickListener(v -> showDatePicker(incidentDate));
        EditText incidentTime = view.findViewById(R.id.inputIncidentTime);
        incidentTime.setText(draft.incidentTime == null ? "" : draft.incidentTime);
        incidentTime.setOnClickListener(v -> showTimePicker(incidentTime));

        setText(view, R.id.inputIncidentLocation, draft.incidentLocationText);
        setText(view, R.id.inputPoliceStation, draft.incidentStationName);
        setText(view, R.id.inputDescription, draft.description);
        CheckBox unknownType = view.findViewById(R.id.checkboxUnknownType);
        Spinner categorySpinner = view.findViewById(R.id.spinnerComplaintType);
        unknownType.setChecked(draft.categoryUnknown);
        categorySpinner.setEnabled(!draft.categoryUnknown);
        unknownType.setOnCheckedChangeListener((button, checked) -> categorySpinner.setEnabled(!checked));
        loadCategories(categorySpinner, draft.categoryId);
        loadDistricts(view.findViewById(R.id.spinnerDistrict), draft.districtId);

        view.findViewById(R.id.btnAiAssist).setOnClickListener(v ->
        {
            Intent intent = new Intent(requireContext(), AiComplaintAssistActivity.class);
            intent.putExtra("description", ((EditText) view.findViewById(R.id.inputDescription)).getText().toString());
            assistantLauncher.launch(intent);
        });

        return view;
    }

    private void setText(View view, int id, String value) {
        ((EditText) view.findViewById(id)).setText(value == null ? "" : value);
    }

    private void loadCategories(Spinner spinner, Integer selectedId) {
        ApiClient.getService().getCategories().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null
                        || !response.body().has("data")) return;
                List<String> labels = new ArrayList<>();
                labels.add(getString(R.string.form_choose_complaint_type));
                categoryIds.clear();
                categoryIds.add(null);
                response.body().getAsJsonArray("data").forEach(row -> {
                    JsonObject category = row.getAsJsonObject();
                    labels.add(localizedName(category, "name_en", "name_st"));
                    categoryIds.add(category.get("category_id").getAsInt());
                });
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, labels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                if (selectedId != null && categoryIds.contains(selectedId)) {
                    spinner.setSelection(categoryIds.indexOf(selectedId));
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), R.string.error_loading_categories, Toast.LENGTH_LONG).show();
            }
        });
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
                    labels.add(localizedName(district, "name_en", "name_st"));
                    districtIds.add(district.get("district_id").getAsInt());
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
                if (isAdded()) Toast.makeText(requireContext(), R.string.error_loading_districts, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String localizedName(JsonObject item, String englishKey, String sesothoKey) {
        String key = AppLanguage.isSesotho(requireContext()) ? sesothoKey : englishKey;
        if (item.has(key) && !item.get(key).isJsonNull()
                && !item.get(key).getAsString().trim().isEmpty()) {
            return item.get(key).getAsString();
        }
        return item.has(englishKey) && !item.get(englishKey).isJsonNull()
                ? item.get(englishKey).getAsString() : "";
    }

    private void showDatePicker(EditText input) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            calendar.set(year, month, day);
            input.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(EditText input) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(requireContext(), (picker, hour, minute) ->
                input.setText(String.format(Locale.US, "%02d:%02d", hour, minute)),
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    public boolean saveToDraft() {
        View view = getView();
        if (view == null) return true;
        EditText date = view.findViewById(R.id.inputIncidentDate);
        EditText description = view.findViewById(R.id.inputDescription);
        if (date.getText().toString().trim().isEmpty()) {
            date.setError(getString(R.string.error_required_field));
            return false;
        }
        if (description.getText().toString().trim().isEmpty()) {
            description.setError(getString(R.string.error_required_field));
            return false;
        }
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        draft.incidentDate = date.getText().toString().trim();
        String time = ((EditText) view.findViewById(R.id.inputIncidentTime)).getText().toString().trim();
        draft.incidentTime = time.isEmpty() ? null : time;
        draft.categoryUnknown = ((CheckBox) view.findViewById(R.id.checkboxUnknownType)).isChecked();
        Spinner category = view.findViewById(R.id.spinnerComplaintType);
        int categoryPosition = category.getSelectedItemPosition();
        draft.categoryId = !draft.categoryUnknown && categoryPosition > 0 && categoryPosition < categoryIds.size()
                ? categoryIds.get(categoryPosition) : null;
        draft.categoryName = draft.categoryId == null ? null : String.valueOf(category.getSelectedItem());
        if (!draft.categoryUnknown && draft.categoryId == null) {
            Toast.makeText(requireContext(), R.string.error_choose_complaint_type, Toast.LENGTH_LONG).show();
            return false;
        }
        Spinner district = view.findViewById(R.id.spinnerDistrict);
        int districtPosition = district.getSelectedItemPosition();
        draft.districtId = districtPosition > 0 && districtPosition < districtIds.size()
                ? districtIds.get(districtPosition) : null;
        draft.incidentLocationText = ((EditText) view.findViewById(R.id.inputIncidentLocation)).getText().toString().trim();
        draft.incidentStationName = ((EditText) view.findViewById(R.id.inputPoliceStation)).getText().toString().trim();
        draft.description = description.getText().toString().trim();
        return true;
    }
}
