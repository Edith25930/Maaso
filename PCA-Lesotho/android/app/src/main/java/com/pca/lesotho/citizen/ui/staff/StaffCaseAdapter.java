package com.pca.lesotho.citizen.ui.staff;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.R;

import java.util.List;

/** One row per case in the staff dashboard list. */
public class StaffCaseAdapter extends RecyclerView.Adapter<StaffCaseAdapter.Holder> {

    public interface OnCaseClick { void onClick(JsonObject item); }

    private final List<JsonObject> items;
    private final OnCaseClick listener;

    public StaffCaseAdapter(List<JsonObject> items, OnCaseClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_staff_case, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        JsonObject c = items.get(position);
        String gcr = StaffUtil.str(c, "gcr_number");
        h.gcr.setText(gcr.isEmpty() ? "#" + StaffUtil.str(c, "complaint_id") : gcr);
        h.status.setText(StaffUtil.str(c, "status_label"));
        h.complainant.setText(StaffUtil.str(c, "complainant_name"));

        String category = StaffUtil.str(c, "category_name");
        String investigator = StaffUtil.str(c, "investigator_name");
        h.meta.setText((category.isEmpty() ? "Unspecified" : category)
                + "  •  " + StaffUtil.priorityLabel(StaffUtil.str(c, "case_priority"))
                + "  •  " + (investigator.isEmpty() ? "Unassigned" : investigator));

        String due = StaffUtil.str(c, "due_label");
        String expected = StaffUtil.str(c, "expected_completion_date");
        if (!due.isEmpty()) {
            h.due.setVisibility(View.VISIBLE);
            h.due.setText(expected.isEmpty() ? due : due + " – " + expected);
        } else {
            h.due.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(c));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView gcr, status, complainant, meta, due;
        Holder(@NonNull View v) {
            super(v);
            gcr = v.findViewById(R.id.tvCaseGcr);
            status = v.findViewById(R.id.tvCaseStatus);
            complainant = v.findViewById(R.id.tvCaseComplainant);
            meta = v.findViewById(R.id.tvCaseMeta);
            due = v.findViewById(R.id.tvCaseDue);
        }
    }
}
