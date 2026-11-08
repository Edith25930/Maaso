package com.pca.lesotho.citizen.ui.complaints;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ComplaintDetailActivity extends AppCompatActivity {
    private TextView gcr, category, status, nextStep, expectedCompletion, incidentDate, description, error;
    private LinearLayout timeline, evidence;
    private ProgressBar progress;
    private Button btnAddEvidence;
    private ActivityResultLauncher<String[]> evidencePicker;
    private long complaintId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint_detail);
        complaintId = getIntent().getLongExtra("complaint_id", -1);
        gcr = findViewById(R.id.textDetailGcr);
        category = findViewById(R.id.textDetailCategory);
        status = findViewById(R.id.textDetailStatus);
        nextStep = findViewById(R.id.textDetailNextStep);
        expectedCompletion = findViewById(R.id.textDetailExpectedCompletion);
        incidentDate = findViewById(R.id.textDetailIncidentDate);
        description = findViewById(R.id.textDetailDescription);
        error = findViewById(R.id.textDetailError);
        timeline = findViewById(R.id.timelineContainer);
        evidence = findViewById(R.id.evidenceContainer);
        progress = findViewById(R.id.detailProgress);
        btnAddEvidence = findViewById(R.id.btnAddEvidence);

        evidencePicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                uploadEvidenceFiles(uris);
            }
        });
        btnAddEvidence.setOnClickListener(v -> evidencePicker.launch(new String[]{"*/*"}));

        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        findViewById(R.id.btnRetryDetail).setOnClickListener(v -> loadComplaint());
        View shareBtn = findViewById(R.id.btnShareReference);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> shareReference());
        }
        loadComplaint();
    }

    private void shareReference() {
        String gcrText = gcr != null ? gcr.getText().toString() : "Pending";
        String catText = category != null ? category.getText().toString() : "Unspecified";
        String statusText = status != null ? status.getText().toString() : "Unknown";
        String dateText = incidentDate != null ? incidentDate.getText().toString() : "—";

        String summary = getString(R.string.share_gcr_summary_format,
                gcrText, catText, statusText, dateText);

        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "PCA Complaint Reference - " + gcrText);
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, summary);
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_gcr_reference)));
    }

    private void loadComplaint() {
        complaintId = getIntent().getLongExtra("complaint_id", -1);
        SessionManager session = new SessionManager(this);
        if (complaintId <= 0 || !session.isLoggedIn() || session.isGuest()) {
            showError(getString(R.string.error_complaint_unavailable));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        error.setVisibility(View.GONE);
        findViewById(R.id.btnRetryDetail).setVisibility(View.GONE);
        ApiClient.getService().getComplaintDetail(ApiClient.bearer(session.getToken()), complaintId)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        progress.setVisibility(View.GONE);
                        if (!response.isSuccessful() || response.body() == null
                                || !response.body().has("success") || !response.body().get("success").getAsBoolean()) {
                            showError(getString(R.string.error_loading_complaint));
                            return;
                        }
                        JsonObject data = response.body().getAsJsonObject("data");
                        bindComplaint(data.getAsJsonObject("complaint"));
                        bindTimeline(data.getAsJsonArray("timeline"));
                        bindEvidence(data.getAsJsonArray("evidence"));
                        btnAddEvidence.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        showError(getString(R.string.error_no_internet));
                    }
                });
    }

    private void uploadEvidenceFiles(List<Uri> uris) {
        SessionManager session = new SessionManager(this);
        if (complaintId <= 0 || !session.isLoggedIn()) return;
        progress.setVisibility(View.VISIBLE);

        List<MultipartBody.Part> files = new ArrayList<>();
        for (Uri uri : uris) {
            files.add(filePart(uri, getDisplayName(uri)));
        }

        ApiClient.getService().addComplaintEvidence(ApiClient.bearer(session.getToken()), complaintId, files)
                .enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        progress.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().has("success") && response.body().get("success").getAsBoolean()) {
                            Toast.makeText(ComplaintDetailActivity.this, R.string.evidence_uploaded, Toast.LENGTH_LONG).show();
                            loadComplaint();
                        } else {
                            Toast.makeText(ComplaintDetailActivity.this, R.string.evidence_upload_failed, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(ComplaintDetailActivity.this, R.string.error_no_internet, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String getDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) { }
        String path = uri.getLastPathSegment();
        return path != null && !path.isEmpty() ? path : "evidence_file";
    }

    private long getFileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private MultipartBody.Part filePart(Uri uri, String filename) {
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        return MultipartBody.Part.createFormData("evidence[]", filename,
                new UriRequestBody(uri, mime, getFileSize(uri)));
    }

    private final class UriRequestBody extends RequestBody {
        private final Uri uri;
        private final MediaType mediaType;
        private final long size;

        UriRequestBody(Uri uri, String mime, long size) {
            this.uri = uri;
            this.mediaType = MediaType.parse(mime);
            this.size = size;
        }

        @Override public MediaType contentType() { return mediaType; }
        @Override public long contentLength() { return size; }

        @Override public void writeTo(@androidx.annotation.NonNull BufferedSink sink) throws IOException {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Could not open " + getDisplayName(uri));
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    sink.write(buffer, 0, count);
                }
            }
        }
    }

    private void bindComplaint(JsonObject complaint) {
        boolean sesotho = AppLanguage.isSesotho(this);
        gcr.setText(value(complaint, "gcr_number", getString(R.string.complaint_reference_pending)));
        category.setText(localizedValue(complaint, "category_name_en", "category_name_st",
                getString(R.string.category_unspecified), sesotho));
        status.setText(localizedValue(complaint, "status_label_en", "status_label_st",
                getString(R.string.complaint_status_unknown), sesotho));
        nextStep.setText(getNextStep(value(complaint, "status_code", "")));
        status.announceForAccessibility(getString(R.string.complaint_current_status_accessibility,
                status.getText()));
        expectedCompletion.setText(expectedCompletionText(complaint, sesotho));
        incidentDate.setText(getString(R.string.complaint_incident_date, value(complaint, "incident_date", "—")));
        description.setText(value(complaint, "description", "—"));
    }

    private String getNextStep(String statusCode) {
        switch (statusCode) {
            case "INFO_REQUESTED":
                return getString(AppLanguage.isSesotho(this)
                        ? R.string.complaint_next_step_information_st : R.string.complaint_next_step_information);
            case "COMPLETED":
            case "CLOSED":
                return getString(AppLanguage.isSesotho(this)
                        ? R.string.complaint_next_step_resolved_st : R.string.complaint_next_step_resolved);
            default:
                return getString(AppLanguage.isSesotho(this)
                        ? R.string.complaint_next_step_waiting_st : R.string.complaint_next_step_waiting);
        }
    }

    private String expectedCompletionText(JsonObject complaint, boolean sesotho) {
        String date = value(complaint, "expected_completion_date", "");
        if (date.isEmpty()) {
            return getString(R.string.complaint_expected_completion,
                    getString(R.string.complaint_date_not_set));
        }
        String statusCode = value(complaint, "status_code", "");
        boolean active = !("COMPLETED".equals(statusCode) || "CLOSED".equals(statusCode));
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Calendar dueSoon = Calendar.getInstance();
        dueSoon.add(Calendar.DAY_OF_YEAR, 7);
        String dueSoonDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dueSoon.getTime());
        if (active && date.compareTo(today) < 0) {
            return getString(sesotho ? R.string.complaint_expected_overdue_st
                    : R.string.complaint_expected_overdue, date);
        }
        if (active && date.compareTo(dueSoonDate) <= 0) {
            return getString(sesotho ? R.string.complaint_expected_soon_st
                    : R.string.complaint_expected_soon, date);
        }
        return getString(R.string.complaint_expected_completion, date);
    }

    private void bindTimeline(JsonArray rows) {
        timeline.removeAllViews();
        if (rows == null || rows.size() == 0) {
            addText(timeline, getString(R.string.complaint_timeline_empty), false);
            return;
        }
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            boolean isSesotho = AppLanguage.isSesotho(this);
            addText(timeline, localizedValue(row, "label_en", "label_st",
                    getString(R.string.complaint_status_unknown), isSesotho), true);
            String message = localizedValue(row, "message_en", "message_st", "", isSesotho);
            if (!message.isEmpty()) addText(timeline, message, false);
            addText(timeline, value(row, "changed_at", ""), false);
        }
    }

    private void bindEvidence(JsonArray rows) {
        evidence.removeAllViews();
        if (rows == null || rows.size() == 0) {
            addText(evidence, getString(R.string.complaint_evidence_empty), false);
            return;
        }
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            addText(evidence, value(row, "file_name", getString(R.string.form_evidence_fallback_name))
                    + " · " + value(row, "file_type", ""), false);
        }
    }

    private void addText(LinearLayout container, String text, boolean heading) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextSize(heading ? 16 : 14);
        if (heading) item.setTypeface(null, android.graphics.Typeface.BOLD);
        item.setPadding(0, 8, 0, 8);
        container.addView(item);
    }

    private String value(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || element.getAsString().isEmpty()
                ? fallback : element.getAsString();
    }

    private String localizedValue(JsonObject object, String englishKey, String sesothoKey,
                                  String fallback, boolean sesotho) {
        String preferred = value(object, sesotho ? sesothoKey : englishKey, "");
        if (!preferred.isEmpty()) return preferred;
        String alternate = value(object, sesotho ? englishKey : sesothoKey, "");
        return alternate.isEmpty() ? fallback : alternate;
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        findViewById(R.id.btnRetryDetail).setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
