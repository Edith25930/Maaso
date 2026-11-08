package com.pca.lesotho.citizen.ui.staff;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** One case: full details, evidence, progress history and the staff actions. */
public class StaffCaseActivity extends AppCompatActivity {
    public static final String EXTRA_COMPLAINT_ID = "complaint_id";
    private static final String[] PRIORITY_CODES = {"low", "normal", "high", "urgent"};
    private static final String[] PRIORITY_LABELS = {"Low", "Normal", "High", "Urgent"};

    private SessionManager session;
    private long complaintId;
    private boolean isAdmin;

    private TextView tvTitle, tvDetails, tvTimeline;
    private LinearLayout layoutEvidence, layoutActions, layoutAssign;
    private ProgressBar progress;
    private Spinner spStatus, spPriority, spInvestigator;
    private EditText etMessageEn, etMessageSt;
    private Button btnPickDate, btnPost, btnSavePriority, btnSaveAssignment;

    private final List<String> statusCodes = new ArrayList<>();
    private final List<Long> investigatorIds = new ArrayList<>();
    private String currentExpectedDate = "";
    private String newExpectedDate = null; // null = leave unchanged
    private boolean overviewLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        complaintId = getIntent().getLongExtra(EXTRA_COMPLAINT_ID, 0);
        if (!StaffUtil.isStaff(session) || complaintId == 0) {
            finish();
            return;
        }
        isAdmin = "admin".equals(session.getRole());
        setContentView(R.layout.activity_staff_case);

        tvTitle = findViewById(R.id.tvCaseTitle);
        tvDetails = findViewById(R.id.tvCaseDetails);
        tvTimeline = findViewById(R.id.tvCaseTimeline);
        layoutEvidence = findViewById(R.id.layoutEvidence);
        layoutActions = findViewById(R.id.layoutActions);
        layoutAssign = findViewById(R.id.layoutAssign);
        progress = findViewById(R.id.progressCase);
        spStatus = findViewById(R.id.spCaseStatus);
        spPriority = findViewById(R.id.spCasePriority);
        spInvestigator = findViewById(R.id.spCaseInvestigator);
        etMessageEn = findViewById(R.id.etMessageEn);
        etMessageSt = findViewById(R.id.etMessageSt);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnPost = findViewById(R.id.btnPostUpdate);
        btnSavePriority = findViewById(R.id.btnSavePriority);
        btnSaveAssignment = findViewById(R.id.btnSaveAssignment);

        spPriority.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PRIORITY_LABELS));
        btnPickDate.setOnClickListener(v -> pickDate());
        btnPost.setOnClickListener(v -> postUpdate());
        btnSavePriority.setOnClickListener(v -> savePriority());
        btnSaveAssignment.setOnClickListener(v -> saveAssignment());

        loadOverviewThenDetail();
    }

    private String auth() { return ApiClient.bearer(session.getToken()); }

    // ---------------------------------------------------------------- loading

    private void loadOverviewThenDetail() {
        ApiClient.getService().staffOverview(auth()).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonObject data = StaffUtil.dataOrNull(StaffCaseActivity.this, response);
                if (data != null) bindLookups(data);
                loadDetail();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) { loadDetail(); }
        });
    }

    private void bindLookups(JsonObject data) {
        List<String> labels = new ArrayList<>();
        statusCodes.clear();
        labels.add(getString(R.string.staff_keep_status));
        statusCodes.add("");
        for (JsonElement e : data.getAsJsonArray("statuses")) {
            JsonObject row = e.getAsJsonObject();
            String code = StaffUtil.str(row, "code");
            statusCodes.add(code);
            labels.add("SUBMITTED".equals(code) ? "Pending" : StaffUtil.str(row, "label_en"));
        }
        spStatus.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));

        if (isAdmin) {
            List<String> names = new ArrayList<>();
            investigatorIds.clear();
            names.add(getString(R.string.staff_unassigned));
            investigatorIds.add(0L);
            for (JsonElement e : data.getAsJsonArray("investigators")) {
                JsonObject row = e.getAsJsonObject();
                investigatorIds.add(row.get("user_id").getAsLong());
                names.add(StaffUtil.str(row, "full_name"));
            }
            spInvestigator.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, names));
            layoutAssign.setVisibility(View.VISIBLE);
        }
        overviewLoaded = true;
    }

    private void loadDetail() {
        progress.setVisibility(View.VISIBLE);
        ApiClient.getService().staffDetail(auth(), complaintId).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progress.setVisibility(View.GONE);
                JsonObject data = StaffUtil.dataOrNull(StaffCaseActivity.this, response);
                if (data == null) { return; }
                bindDetail(data);
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                progress.setVisibility(View.GONE);
                Toast.makeText(StaffCaseActivity.this, R.string.staff_error_generic, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(label).append(": ").append(value).append('\n');
    }

    private void bindDetail(JsonObject data) {
        JsonObject c = data.getAsJsonObject("complaint");
        String gcr = StaffUtil.str(c, "gcr_number");
        tvTitle.setText(gcr.isEmpty() ? "Case #" + complaintId : gcr);

        StringBuilder sb = new StringBuilder();
        line(sb, "Status", StaffUtil.str(c, "status_label"));
        line(sb, "Priority", StaffUtil.priorityLabel(StaffUtil.str(c, "case_priority")));
        line(sb, "Assigned to", StaffUtil.str(c, "investigator_name"));
        line(sb, "Expected completion", StaffUtil.str(c, "expected_completion_date"));
        line(sb, "Submitted", StaffUtil.str(c, "submitted_at"));
        line(sb, "Category", StaffUtil.str(c, "category_name"));
        sb.append('\n');
        line(sb, "Complainant", StaffUtil.str(c, "complainant_display_name"));
        line(sb, "Phone", StaffUtil.str(c, "complainant_display_phone"));
        line(sb, "Email", StaffUtil.str(c, "complainant_email"));
        line(sb, "ID number", StaffUtil.str(c, "complainant_id_number"));
        line(sb, "Gender", StaffUtil.str(c, "complainant_gender"));
        line(sb, "District", StaffUtil.str(c, "complainant_district"));
        line(sb, "Location", StaffUtil.str(c, "complainant_location"));
        line(sb, "Next of kin", StaffUtil.str(c, "next_of_kin_name"));
        line(sb, "Next of kin address", StaffUtil.str(c, "next_of_kin_address"));
        sb.append('\n');
        line(sb, "Incident date", StaffUtil.str(c, "incident_date"));
        line(sb, "Incident time", StaffUtil.str(c, "incident_time"));
        line(sb, "Incident district", StaffUtil.str(c, "district_name"));
        line(sb, "Incident place", StaffUtil.str(c, "incident_location_text"));
        line(sb, "Police station", StaffUtil.str(c, "incident_station_name"));
        if (StaffUtil.num(c, "officer_unknown") == 1) {
            line(sb, "Officer", "Unknown to complainant");
        } else {
            line(sb, "Officer", StaffUtil.str(c, "officer_name"));
            line(sb, "Rank", StaffUtil.str(c, "officer_rank"));
            line(sb, "Badge number", StaffUtil.str(c, "badge_number"));
            line(sb, "Officer station", StaffUtil.str(c, "officer_station_name"));
        }
        JsonArray witnesses = data.getAsJsonArray("witnesses");
        for (JsonElement e : witnesses) {
            JsonObject w = e.getAsJsonObject();
            line(sb, "Witness", StaffUtil.str(w, "full_name") + " " + StaffUtil.str(w, "contact_number"));
        }
        sb.append("\nStatement:\n").append(StaffUtil.str(c, "description"));
        tvDetails.setText(sb.toString().trim());

        bindEvidence(data.getAsJsonArray("evidence"));
        bindTimeline(data.getAsJsonArray("timeline"));

        currentExpectedDate = StaffUtil.str(c, "expected_completion_date");
        if (newExpectedDate == null) updateDateButton(currentExpectedDate);
        selectPriority(StaffUtil.str(c, "case_priority"));
        selectInvestigator(StaffUtil.str(c, "investigator_id"));
        layoutActions.setVisibility(overviewLoaded ? View.VISIBLE : View.GONE);
    }

    private void bindEvidence(JsonArray evidence) {
        layoutEvidence.removeAllViews();
        if (evidence.size() == 0) {
            TextView none = new TextView(this);
            none.setText(R.string.staff_no_evidence);
            none.setPadding(0, 8, 0, 8);
            layoutEvidence.addView(none);
            return;
        }
        for (JsonElement e : evidence) {
            JsonObject ev = e.getAsJsonObject();
            long id = ev.get("evidence_id").getAsLong();
            String name = StaffUtil.str(ev, "file_name");
            String size = StaffUtil.str(ev, "file_size_bytes");
            com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setAllCaps(false);
            b.setText(name + (size.isEmpty() ? "" : "  (" + readableSize(Long.parseLong(size)) + ")"));
            b.setOnClickListener(v -> openEvidence(id, name, b));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 8;
            layoutEvidence.addView(b, lp);
        }
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void bindTimeline(JsonArray timeline) {
        if (timeline.size() == 0) {
            tvTimeline.setText(R.string.staff_no_history);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = timeline.size() - 1; i >= 0; i--) {
            JsonObject t = timeline.get(i).getAsJsonObject();
            sb.append(StaffUtil.str(t, "changed_at")).append("  –  ")
                    .append(StaffUtil.str(t, "status_label"));
            String by = StaffUtil.str(t, "updated_by");
            if (!by.isEmpty()) sb.append("  (").append(by).append(')');
            sb.append('\n');
            String msg = StaffUtil.str(t, "message_en");
            if (!msg.isEmpty()) sb.append(msg).append('\n');
            sb.append('\n');
        }
        tvTimeline.setText(sb.toString().trim());
    }

    private void selectPriority(String code) {
        for (int i = 0; i < PRIORITY_CODES.length; i++) {
            if (PRIORITY_CODES[i].equals(code)) { spPriority.setSelection(i); return; }
        }
        spPriority.setSelection(1);
    }

    private void selectInvestigator(String idText) {
        if (!isAdmin || investigatorIds.isEmpty()) return;
        long id = 0;
        try { id = idText.isEmpty() ? 0 : Long.parseLong(idText); } catch (NumberFormatException ignored) { }
        int index = investigatorIds.indexOf(id);
        spInvestigator.setSelection(Math.max(index, 0));
    }

    // ---------------------------------------------------------------- actions

    private void pickDate() {
        Calendar cal = Calendar.getInstance();
        String source = newExpectedDate != null ? newExpectedDate : currentExpectedDate;
        if (source != null && source.matches("\\d{4}-\\d{2}-\\d{2}")) {
            cal.set(Integer.parseInt(source.substring(0, 4)),
                    Integer.parseInt(source.substring(5, 7)) - 1,
                    Integer.parseInt(source.substring(8, 10)));
        }
        new DatePickerDialog(this, (view, y, m, d) -> {
            newExpectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            updateDateButton(newExpectedDate);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButton(String date) {
        btnPickDate.setText(getString(R.string.staff_pick_date,
                date == null || date.isEmpty() ? getString(R.string.staff_no_date) : date));
    }

    private void postUpdate() {
        String messageEn = etMessageEn.getText().toString().trim();
        if (messageEn.isEmpty()) {
            Toast.makeText(this, R.string.staff_message_required, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("complaint_id", complaintId);
        body.put("message_en", messageEn);
        String messageSt = etMessageSt.getText().toString().trim();
        if (!messageSt.isEmpty()) body.put("message_st", messageSt);
        int pos = spStatus.getSelectedItemPosition();
        if (pos > 0 && pos < statusCodes.size()) body.put("status_code", statusCodes.get(pos));
        if (newExpectedDate != null) body.put("expected_completion_date", newExpectedDate);

        btnPost.setEnabled(false);
        ApiClient.getService().staffUpdate(auth(), body).enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                btnPost.setEnabled(true);
                if (StaffUtil.dataOrNull(StaffCaseActivity.this, response) == null) return;
                Toast.makeText(StaffCaseActivity.this, R.string.staff_saved, Toast.LENGTH_SHORT).show();
                etMessageEn.setText("");
                etMessageSt.setText("");
                spStatus.setSelection(0);
                newExpectedDate = null;
                loadDetail();
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                btnPost.setEnabled(true);
                Toast.makeText(StaffCaseActivity.this, R.string.staff_error_generic, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void savePriority() {
        Map<String, Object> body = new HashMap<>();
        body.put("complaint_id", complaintId);
        body.put("priority", PRIORITY_CODES[spPriority.getSelectedItemPosition()]);
        simplePost(ApiClient.getService().staffPriority(auth(), body), btnSavePriority);
    }

    private void saveAssignment() {
        int pos = spInvestigator.getSelectedItemPosition();
        if (pos < 0 || pos >= investigatorIds.size()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("complaint_id", complaintId);
        body.put("investigator_id", investigatorIds.get(pos));
        simplePost(ApiClient.getService().staffAssign(auth(), body), btnSaveAssignment);
    }

    private void simplePost(Call<JsonObject> call, Button button) {
        button.setEnabled(false);
        call.enqueue(new Callback<JsonObject>() {
            @Override public void onResponse(Call<JsonObject> c, Response<JsonObject> response) {
                button.setEnabled(true);
                if (StaffUtil.dataOrNull(StaffCaseActivity.this, response) == null) return;
                Toast.makeText(StaffCaseActivity.this, R.string.staff_saved, Toast.LENGTH_SHORT).show();
                loadDetail();
            }
            @Override public void onFailure(Call<JsonObject> c, Throwable t) {
                button.setEnabled(true);
                Toast.makeText(StaffCaseActivity.this, R.string.staff_error_generic, Toast.LENGTH_LONG).show();
            }
        });
    }

    // --------------------------------------------------------------- evidence

    /** Downloads the file with the staff token into the app cache, then opens it. */
    private void openEvidence(long evidenceId, String fileName, Button button) {
        button.setEnabled(false);
        ApiClient.getService().staffEvidence(auth(), evidenceId).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    button.setEnabled(true);
                    Toast.makeText(StaffCaseActivity.this, R.string.staff_evidence_open_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                final ResponseBody body = response.body();
                new Thread(() -> {
                    File saved = null;
                    try {
                        File dir = new File(getCacheDir(), "complaint-evidence");
                        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("cache dir");
                        String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                        File out = new File(dir, evidenceId + "_" + safe);
                        try (InputStream in = body.byteStream(); OutputStream os = new FileOutputStream(out)) {
                            byte[] buffer = new byte[8192];
                            int n;
                            while ((n = in.read(buffer)) != -1) os.write(buffer, 0, n);
                        }
                        saved = out;
                    } catch (Exception ignored) { }
                    final File result = saved;
                    runOnUiThread(() -> {
                        button.setEnabled(true);
                        if (result == null) {
                            Toast.makeText(StaffCaseActivity.this, R.string.staff_evidence_open_failed,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            launchViewer(result);
                        }
                    });
                }).start();
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) {
                button.setEnabled(true);
                Toast.makeText(StaffCaseActivity.this, R.string.staff_evidence_open_failed,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchViewer(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.US));
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime != null ? mime : "*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.staff_evidence_open_failed, Toast.LENGTH_LONG).show();
        }
    }
}
