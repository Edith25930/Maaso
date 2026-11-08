package com.pca.lesotho.citizen.ui.complaints;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.adapters.ComplaintAdapter;
import com.pca.lesotho.citizen.model.Complaint;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** "All | Active | Resolved" tabs would wrap this fragment via a TabLayout + ViewPager2
 *  in production; each tab instance passes its filter through a Bundle argument. */
public class ComplaintsFragment extends Fragment {

    private RecyclerView recyclerView;
    private View emptyState;
    private android.widget.TextView emptyText;
    private View progress;
    private View retry;
    private String activeFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_complaints, container, false);
        recyclerView = view.findViewById(R.id.recyclerComplaints);
        emptyState = view.findViewById(R.id.emptyStateNoComplaints);
        emptyText = view.findViewById(R.id.textComplaintsEmpty);
        progress = view.findViewById(R.id.complaintsProgress);
        retry = view.findViewById(R.id.btnRetryComplaints);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        view.findViewById(R.id.filterAll).setOnClickListener(v -> loadComplaints("all"));
        view.findViewById(R.id.filterActive).setOnClickListener(v -> loadComplaints("active"));
        view.findViewById(R.id.filterResolved).setOnClickListener(v -> loadComplaints("resolved"));
        retry.setOnClickListener(v -> loadComplaints(activeFilter));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (recyclerView != null) loadComplaints(activeFilter);
    }

    private void loadComplaints(String filter) {
        activeFilter = filter;
        updateFilterSelection();
        SessionManager session = new SessionManager(requireContext());
        if (!session.isLoggedIn() || session.isGuest()) {
            showEmpty(getString(R.string.please_sign_in_to_submit), false);
            return;
        }

        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.loading_complaints);
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);

        ApiClient.getService().getComplaints(ApiClient.bearer(session.getToken()), filter)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        List<Complaint> complaints = new ArrayList<>();
                        if (response.isSuccessful() && response.body() != null && response.body().has("success")
                                && response.body().get("success").getAsBoolean()) {
                            Gson gson = new Gson();
                            response.body().getAsJsonArray("data").forEach(el ->
                                    complaints.add(gson.fromJson(el, Complaint.class)));
                        }
                        if (complaints.isEmpty()) {
                            boolean success = response.isSuccessful() && response.body() != null
                                    && response.body().has("success") && response.body().get("success").getAsBoolean();
                            showEmpty(success ? getString(R.string.complaints_empty)
                                    : getString(R.string.complaints_load_failed), !success);
                        } else {
                            recyclerView.setVisibility(View.VISIBLE);
                            emptyState.setVisibility(View.GONE);
                            recyclerView.setAdapter(new ComplaintAdapter(complaints));
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        showEmpty(getString(R.string.complaints_load_failed), true);
                    }
                });
    }

    private void updateFilterSelection() {
        int[] ids = {R.id.filterAll, R.id.filterActive, R.id.filterResolved};
        String[] filters = {"all", "active", "resolved"};
        for (int i = 0; i < ids.length; i++) {
            android.widget.Button button = getView() == null ? null : getView().findViewById(ids[i]);
            if (button == null) return;
            boolean selected = filters[i].equals(activeFilter);
            button.setSelected(selected);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(selected ? R.color.pca_navy : R.color.surface)));
            button.setTextColor(requireContext().getColor(
                    selected ? R.color.white : R.color.pca_navy));
        }
    }

    private void showEmpty(String message, boolean canRetry) {
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        emptyText.setText(message);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }
}
