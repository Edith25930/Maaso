package com.pca.lesotho.citizen.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.complaints.ComplaintDetailActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends Fragment {
    private final List<JsonObject> items = new ArrayList<>();
    private NotificationAdapter adapter;
    private RecyclerView recycler;
    private TextView empty;
    private TextView status;
    private Button markAllRead;
    private Button retryButton;
    private View progress;
    private String token;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);
        recycler = view.findViewById(R.id.recyclerNotifications);
        empty = view.findViewById(R.id.emptyStateNoNotifications);
        status = view.findViewById(R.id.notificationStatus);
        markAllRead = view.findViewById(R.id.btnMarkAllRead);
        retryButton = view.findViewById(R.id.btnRetryNotifications);
        progress = view.findViewById(R.id.notificationsProgress);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(items, this::openNotification);
        recycler.setAdapter(adapter);
        retryButton.setOnClickListener(v -> loadNotifications());
        markAllRead.setOnClickListener(v -> markRead(0, true));
        loadNotifications();
        return view;
    }

    private void loadNotifications() {
        SessionManager session = new SessionManager(requireContext());
        if (!session.isLoggedIn() || session.isGuest()) {
            showEmpty(getString(R.string.notifications_sign_in), false);
            return;
        }
        token = ApiClient.bearer(session.getToken());
        status.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        ApiClient.getService().getNotifications(token).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null
                        || !response.body().has("success") || !response.body().get("success").getAsBoolean()) {
                    showEmpty(getString(R.string.notifications_load_failed), true);
                    return;
                }
                items.clear();
                JsonArray rows = response.body().getAsJsonArray("data");
                rows.forEach(row -> items.add(row.getAsJsonObject()));
                adapter.notifyDataSetChanged();
                recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (items.isEmpty()) empty.setText(R.string.notifications_empty);
                markAllRead.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progress.setVisibility(View.GONE);
                showEmpty(getString(R.string.notifications_load_failed), true);
            }
        });
    }

    private void markRead(long notificationId, boolean all) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        if (!all) body.put("notification_id", notificationId);
        ApiClient.getService().markNotificationRead(token, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null
                        || !response.body().has("success") || !response.body().get("success").getAsBoolean()) {
                    Toast.makeText(requireContext(), R.string.notifications_mark_read_failed, Toast.LENGTH_LONG).show();
                } else if (all) {
                    loadNotifications();
                } else {
                    for (JsonObject item : items) {
                        if (item.get("notification_id").getAsLong() == notificationId) {
                            item.addProperty("is_read", 1);
                            adapter.notifyDataSetChanged();
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(requireContext(), R.string.notifications_mark_read_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openNotification(JsonObject item) {
        if (item.get("is_read").getAsInt() == 0) markRead(item.get("notification_id").getAsLong(), false);
        if (item.has("complaint_id") && !item.get("complaint_id").isJsonNull()) {
            long complaintId = item.get("complaint_id").getAsLong();
            Intent intent = new Intent(requireContext(), ComplaintDetailActivity.class);
            intent.putExtra("complaint_id", complaintId);
            startActivity(intent);
        }
    }

    private void showEmpty(String message, boolean canRetry) {
        recycler.setVisibility(View.GONE);
        empty.setVisibility(canRetry ? View.GONE : View.VISIBLE);
        empty.setText(message);
        status.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        if (canRetry) status.setText(message);
        markAllRead.setVisibility(View.GONE);
        retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private interface OnNotificationClick {
        void onClick(JsonObject item);
    }

    private static class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {
        private final List<JsonObject> items;
        private final OnNotificationClick click;

        NotificationAdapter(List<JsonObject> items, OnNotificationClick click) {
            this.items = items;
            this.click = click;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            JsonObject item = items.get(position);
            boolean sesotho = AppLanguage.isSesotho(holder.itemView.getContext());
            String suffix = sesotho ? "_st" : "_en";
            String fallbackSuffix = sesotho ? "_en" : "_st";
            String title = localizedValue(item, "title" + suffix, "title" + fallbackSuffix);
            String body = localizedValue(item, "body" + suffix, "body" + fallbackSuffix);
            holder.title.setText(title);
            holder.body.setText(body);
            holder.time.setText(value(item, "created_at"));
            holder.title.setTypeface(null, item.get("is_read").getAsInt() == 0
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                    item.get("is_read").getAsInt() == 0
                            ? R.string.notification_accessibility_unread
                            : R.string.notification_accessibility_read,
                    title, body, holder.time.getText()));
            holder.itemView.setFocusable(true);
            holder.itemView.setOnClickListener(v -> click.onClick(item));
        }

        private String localizedValue(JsonObject item, String preferredKey, String fallbackKey) {
            String preferred = value(item, preferredKey);
            return preferred.isEmpty() ? value(item, fallbackKey) : preferred;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static String value(JsonObject item, String key) {
            return item.has(key) && !item.get(key).isJsonNull() ? item.get(key).getAsString() : "";
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView title, body, time;

            Holder(View view) {
                super(view);
                title = view.findViewById(R.id.textNotificationTitle);
                body = view.findViewById(R.id.textNotificationBody);
                time = view.findViewById(R.id.textNotificationTime);
            }
        }
    }
}
