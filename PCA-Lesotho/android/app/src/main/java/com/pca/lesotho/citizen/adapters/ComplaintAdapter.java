package com.pca.lesotho.citizen.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.model.Complaint;
import com.pca.lesotho.citizen.ui.complaints.ComplaintDetailActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ComplaintAdapter extends RecyclerView.Adapter<ComplaintAdapter.ComplaintHolder> {

    private final List<Complaint> complaints;

    public ComplaintAdapter(List<Complaint> complaints) {
        this.complaints = complaints;
    }

    @NonNull
    @Override
    public ComplaintHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_complaint_card, parent, false);
        return new ComplaintHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ComplaintHolder holder, int position) {
        Complaint c = complaints.get(position);
        boolean sesotho = AppLanguage.isSesotho(holder.itemView.getContext());
        holder.gcr.setText(c.gcrNumber == null || c.gcrNumber.isEmpty()
                ? holder.itemView.getContext().getString(R.string.complaint_reference_pending) : c.gcrNumber);
        String category = sesotho ? c.categoryNameSt : c.categoryNameEn;
        if (category == null || category.isEmpty()) category = sesotho ? c.categoryNameEn : c.categoryNameSt;
        holder.category.setText(category != null && !category.isEmpty()
                ? category : holder.itemView.getContext().getString(R.string.category_unspecified));
        String status = sesotho ? c.statusLabelSt : c.statusLabelEn;
        if (status == null || status.isEmpty()) status = sesotho ? c.statusLabelEn : c.statusLabelSt;
        holder.status.setText(status == null || status.isEmpty()
                ? holder.itemView.getContext().getString(R.string.complaint_status_unknown) : status);
        holder.expectedCompletion.setText(expectedCompletionLabel(holder.itemView, c, sesotho));
        String latestUpdate = sesotho ? c.latestUpdateSt : c.latestUpdateEn;
        if (latestUpdate == null || latestUpdate.isEmpty()) {
            latestUpdate = sesotho ? c.latestUpdateEn : c.latestUpdateSt;
        }
        holder.latestUpdate.setText(latestUpdate == null || latestUpdate.isEmpty()
                ? holder.itemView.getContext().getString(R.string.complaint_no_office_update)
                : holder.itemView.getContext().getString(R.string.complaint_latest_office_update, latestUpdate));
        holder.updated.setText(holder.itemView.getContext().getString(R.string.last_updated_format, c.updatedAt));
        holder.itemView.setContentDescription(holder.itemView.getContext().getString(
                R.string.complaint_card_accessibility, holder.gcr.getText(), holder.status.getText(),
                holder.expectedCompletion.getText()));
        holder.itemView.setFocusable(true);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ComplaintDetailActivity.class);
            intent.putExtra("complaint_id", c.complaintId);
            v.getContext().startActivity(intent);
        });
    }

    private String expectedCompletionLabel(View view, Complaint complaint, boolean sesotho) {
        String date = complaint.expectedCompletionDate;
        if (date == null || date.isEmpty()) {
            return view.getContext().getString(R.string.complaint_expected_completion,
                    view.getContext().getString(R.string.complaint_date_not_set));
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        Calendar dueSoon = Calendar.getInstance();
        dueSoon.add(Calendar.DAY_OF_YEAR, 7);
        String dueSoonDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dueSoon.getTime());
        boolean active = complaint.statusCode == null
                || !("COMPLETED".equals(complaint.statusCode) || "CLOSED".equals(complaint.statusCode));
        if (active && date.compareTo(today) < 0) {
            return view.getContext().getString(sesotho
                    ? R.string.complaint_expected_overdue_st : R.string.complaint_expected_overdue, date);
        }
        if (active && date.compareTo(dueSoonDate) <= 0) {
            return view.getContext().getString(sesotho
                    ? R.string.complaint_expected_soon_st : R.string.complaint_expected_soon, date);
        }
        return view.getContext().getString(R.string.complaint_expected_completion, date);
    }

    @Override
    public int getItemCount() { return complaints.size(); }

    static class ComplaintHolder extends RecyclerView.ViewHolder {
        TextView gcr, category, status, expectedCompletion, latestUpdate, updated;
        ComplaintHolder(View itemView) {
            super(itemView);
            gcr = itemView.findViewById(R.id.textGcr);
            category = itemView.findViewById(R.id.textCategory);
            status = itemView.findViewById(R.id.textStatus);
            expectedCompletion = itemView.findViewById(R.id.textExpectedCompletion);
            latestUpdate = itemView.findViewById(R.id.textLatestUpdate);
            updated = itemView.findViewById(R.id.textUpdated);
        }
    }
}
