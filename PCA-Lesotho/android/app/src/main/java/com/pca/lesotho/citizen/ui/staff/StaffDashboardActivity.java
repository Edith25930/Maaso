package com.pca.lesotho.citizen.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Native admin / investigator dashboard: summary figures, filters and the case list. */
public class StaffDashboardActivity extends AppCompatActivity {
    private static final int PAGE_SIZE = 20;

    private SessionManager session;
    private final List<JsonObject> cases = new ArrayList<>();
    private StaffCaseAdapter adapter;

    private final List<String> statusCodes = new ArrayList<>();
    private Spinner spStatus;
    private Spinner spQueue;
    private EditText etSearch;
    private TextView tvCount;
    private TextView tvEmpty;
    private ProgressBar progress;
    private Button btnLoadMore;

    private int page = 1;
    private int pages = 1;
    private boolean loading = false;
    private int generation = 0; // bumps on every reload so stale responses are ignored
    private boolean spinnersReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!StaffUtil.isStaff(session)) {
            Toast.makeText(this, R.string.staff_dashboard_sign_in_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_staff_dashboard);

        TextView role = findViewById(R.id.tvStaffRole);
        role.setText("admin".equals(session.getRole())
                ? R.string.staff_role_admin : R.string.staff_role_investigator);

        spStatus = findViewById(R.id.spStaffStatus);
        spQueue = findViewById(R.id.spStaffQueue);
        etSearch = findViewById(R.id.etStaffSearch);
        tvCount = findViewById(R.id.tvStaffCount);
        tvEmpty = findViewById(R.id.tvStaffEmpty);
        progress = findViewById(R.id.progressStaff);
        btnLoadMore = findViewById(R.id.btnStaffLoadMore);

        adapter = new StaffCaseAdapter(cases, item -> {
            Intent intent = new Intent(this, StaffCaseActivity.class);
            intent.putExtra(StaffCaseActivity.EXTRA_COMPLAINT_ID, item.get("complaint_id").getAsLong());
            startActivity(intent);
        });
        RecyclerView list = findViewById(R.id.rvStaffCases);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        setStatLabel(R.id.statTotal, R.string.staff_stat_total);
        setStatLabel(R.id.statActive, R.string.staff_stat_active);
        setStatLabel(R.id.statClosed, R.string.staff_stat_closed);
        setStatLabel(R.id.statOverdue, R.string.staff_stat_overdue);
        setStatLabel(R.id.statDueSoon, R.string.staff_stat_due_soon);

        ArrayAdapter<String> queueAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{getString(R.string.staff_queue_all),
                        getString(R.string.staff_queue_overdue),
                        getString(R.string.staff_queue_due_soon)});
        spQueue.setAdapter(queueAdapter);

        AdapterView.OnItemSelectedListener reload = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnersReady) reloadCases();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        spStatus.setOnItemSelectedListener(reload);
        spQueue.setOnItemSelectedListener(reload);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                reloadCases();
                return true;
            }
            return false;
        });
        btnLoadMore.setOnClickListener(v -> loadPage(page + 1));
        findViewById(R.id.btnStaffRefresh).setOnClickListener(v -> refreshAll());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning from a case so status/priority changes show up.
        if (session != null && StaffUtil.isStaff(session)) refreshAll();
    }

    private void setStatLabel(int includeId, int labelRes) {
        ((TextView) findViewById(includeId).findViewById(R.id.statLabel)).setText(labelRes);
    }

    private void setStatValue(int includeId, String value) {
        ((TextView) findViewById(includeId).findViewById(R.id.statValue)).setText(value);
    }

    private void refreshAll() {
        loadOverview();
    }

    private void loadOverview() {
        ApiClient.getService().staffOverview(ApiClient.bearer(session.getToken()))
                .enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        JsonObject data = StaffUtil.dataOrNull(StaffDashboardActivity.this, response);
                        if (data == null) return;
                        bindOverview(data);
                        reloadCases();
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                        Toast.makeText(StaffDashboardActivity.this,
                                R.string.staff_error_generic, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void bindOverview(JsonObject data) {
        JsonObject s = data.getAsJsonObject("summary");
        setStatValue(R.id.statTotal, String.valueOf(StaffUtil.num(s, "total")));
        setStatValue(R.id.statActive, String.valueOf(StaffUtil.num(s, "active")));
        setStatValue(R.id.statClosed, String.valueOf(StaffUtil.num(s, "closed")));
        setStatValue(R.id.statOverdue, String.valueOf(StaffUtil.num(s, "overdue")));
        setStatValue(R.id.statDueSoon, String.valueOf(StaffUtil.num(s, "due_soon")));

        StringBuilder breakdown = new StringBuilder();
        JsonArray byStatus = data.getAsJsonArray("by_status");
        for (JsonElement e : byStatus) {
            JsonObject row = e.getAsJsonObject();
            if (breakdown.length() > 0) breakdown.append("  •  ");
            breakdown.append(StaffUtil.str(row, "label")).append(": ").append(StaffUtil.str(row, "count"));
        }
        ((TextView) findViewById(R.id.tvStatusBreakdown)).setText(breakdown);

        // Fill the status filter once; keep the user's selection on refresh.
        if (!spinnersReady) {
            List<String> labels = new ArrayList<>();
            statusCodes.clear();
            labels.add(getString(R.string.staff_filter_all_statuses));
            statusCodes.add("");
            for (JsonElement e : data.getAsJsonArray("statuses")) {
                JsonObject row = e.getAsJsonObject();
                statusCodes.add(StaffUtil.str(row, "code"));
                String label = StaffUtil.str(row, "label_en");
                labels.add("SUBMITTED".equals(StaffUtil.str(row, "code")) ? "Pending" : label);
            }
            spStatus.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, labels));
            spinnersReady = true;
        }
    }

    private void reloadCases() {
        generation++;
        loading = false;
        cases.clear();
        adapter.notifyDataSetChanged();
        page = 1;
        pages = 1;
        loadPage(1);
    }

    private void loadPage(int target) {
        if (loading) return;
        loading = true;
        final int requestGeneration = generation;
        progress.setVisibility(View.VISIBLE);
        btnLoadMore.setVisibility(View.GONE);

        Map<String, String> filters = new HashMap<>();
        filters.put("page", String.valueOf(target));
        filters.put("per_page", String.valueOf(PAGE_SIZE));
        int statusPos = spStatus.getSelectedItemPosition();
        if (statusPos > 0 && statusPos < statusCodes.size()) filters.put("status", statusCodes.get(statusPos));
        int queuePos = spQueue.getSelectedItemPosition();
        if (queuePos == 1) filters.put("due_window", "overdue");
        if (queuePos == 2) filters.put("due_window", "due_soon");
        String query = etSearch.getText().toString().trim();
        if (!query.isEmpty()) filters.put("q", query);

        ApiClient.getService().staffComplaints(ApiClient.bearer(session.getToken()), filters)
                .enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (requestGeneration != generation) return;
                        loading = false;
                        progress.setVisibility(View.GONE);
                        JsonObject data = StaffUtil.dataOrNull(StaffDashboardActivity.this, response);
                        if (data == null) return;
                        page = StaffUtil.num(data, "page");
                        pages = StaffUtil.num(data, "pages");
                        int start = cases.size();
                        for (JsonElement e : data.getAsJsonArray("items")) cases.add(e.getAsJsonObject());
                        adapter.notifyItemRangeInserted(start, cases.size() - start);
                        tvCount.setText(getString(R.string.staff_cases_count, StaffUtil.num(data, "total")));
                        tvEmpty.setVisibility(cases.isEmpty() ? View.VISIBLE : View.GONE);
                        btnLoadMore.setVisibility(page < pages ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                        if (requestGeneration != generation) return;
                        loading = false;
                        progress.setVisibility(View.GONE);
                        Toast.makeText(StaffDashboardActivity.this,
                                R.string.staff_error_generic, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
