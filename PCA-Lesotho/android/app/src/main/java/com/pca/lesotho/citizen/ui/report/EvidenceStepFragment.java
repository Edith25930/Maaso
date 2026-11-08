package com.pca.lesotho.citizen.ui.report;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;
import com.pca.lesotho.citizen.data.EvidenceAttachment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EvidenceStepFragment extends Fragment {
    private static final long MAX_EVIDENCE_BYTES = 5_000_000L;
    private final List<EvidenceAttachment> attachments = new ArrayList<>();
    private Uri cameraOutput;
    private EvidenceAdapter adapter;
    private TextView sizeSummary;
    private ActivityResultLauncher<String[]> documentPicker;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<Intent> audioRecorder;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        documentPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || !isAdded()) return;
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Toast.makeText(requireContext(), R.string.error_evidence_permission, Toast.LENGTH_LONG).show();
            }
            addAttachment(uri, null);
        });
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraOutput != null && isAdded()) addAttachment(cameraOutput, getString(R.string.form_camera_photo));
        });
        audioRecorder = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null
                    && result.getData().getData() != null && isAdded()) {
                addAttachment(result.getData().getData(), null);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_evidence, container, false);
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        EvidenceAttachment[] saved = new Gson().fromJson(draft.evidenceJson, EvidenceAttachment[].class);
        if (saved != null) {
            attachments.clear();
            attachments.addAll(java.util.Arrays.asList(saved));
        }
        RecyclerView recycler = view.findViewById(R.id.recyclerEvidence);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);
        adapter = new EvidenceAdapter(attachments, position -> {
            attachments.remove(position);
            saveEvidence();
        });
        recycler.setAdapter(adapter);
        sizeSummary = view.findViewById(R.id.textEvidenceSize);
        updateSizeSummary();

        view.findViewById(R.id.btnTakePhoto).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.btnChoosePhoto).setOnClickListener(v -> documentPicker.launch(new String[]{"image/*"}));
        view.findViewById(R.id.btnChooseVideo).setOnClickListener(v -> documentPicker.launch(new String[]{"video/*"}));
        view.findViewById(R.id.btnChooseDocument).setOnClickListener(v -> documentPicker.launch(new String[]{
                "application/pdf", "text/plain", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/rtf", "application/vnd.oasis.opendocument.text"
        }));
        view.findViewById(R.id.btnRecordAudio).setOnClickListener(v -> recordAudio());
        return view;
    }

    private void takePhoto() {
        try {
            File directory = new File(requireContext().getCacheDir(), "complaint-evidence");
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Cannot create photo cache.");
            File photo = File.createTempFile("pca_photo_", ".jpg", directory);
            cameraOutput = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photo);
            cameraLauncher.launch(cameraOutput);
        } catch (java.io.IOException | IllegalArgumentException | SecurityException e) {
            Toast.makeText(requireContext(), R.string.error_camera_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void recordAudio() {
        Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            audioRecorder.launch(intent);
        } else {
            documentPicker.launch(new String[]{"audio/*"});
        }
    }

    private void addAttachment(Uri uri, String forcedName) {
        try {
            String mime = requireContext().getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";
            long size = getSize(uri);
            if (size <= 0) {
                Toast.makeText(requireContext(), R.string.error_evidence_size_unknown, Toast.LENGTH_LONG).show();
                return;
            }
            if (totalBytes() + size > MAX_EVIDENCE_BYTES) {
                Toast.makeText(requireContext(), R.string.error_evidence_limit, Toast.LENGTH_LONG).show();
                return;
            }
            String name = forcedName == null ? getDisplayName(uri) : forcedName;
            attachments.add(new EvidenceAttachment(uri.toString(), name, mime, size));
            saveEvidence();
        } catch (IllegalArgumentException | SecurityException e) {
            Toast.makeText(requireContext(), R.string.error_evidence_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private long getSize(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        }
        return 0;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        return getString(R.string.form_evidence_fallback_name);
    }

    private long totalBytes() {
        long total = 0;
        for (EvidenceAttachment attachment : attachments) total += attachment.sizeBytes;
        return total;
    }

    private void saveEvidence() {
        ComplaintDraft draft = ((ReportComplaintActivity) requireActivity()).getDraft();
        draft.evidenceJson = new Gson().toJson(attachments);
        ((ReportComplaintActivity) requireActivity()).saveDraft();
        adapter.notifyDataSetChanged();
        updateSizeSummary();
    }

    private void updateSizeSummary() {
        if (sizeSummary == null) return;
        sizeSummary.setText(getString(R.string.form_evidence_size,
                (totalBytes() + 1023) / 1024, (MAX_EVIDENCE_BYTES + 1023) / 1024));
    }

    public boolean saveToDraft() {
        if (totalBytes() > MAX_EVIDENCE_BYTES) {
            Toast.makeText(requireContext(), R.string.error_evidence_limit, Toast.LENGTH_LONG).show();
            return false;
        }
        ((ReportComplaintActivity) requireActivity()).getDraft().evidenceJson = new Gson().toJson(attachments);
        return true;
    }

    private interface RemoveListener {
        void onRemove(int position);
    }

    private static class EvidenceAdapter extends RecyclerView.Adapter<EvidenceAdapter.Holder> {
        private final List<EvidenceAttachment> items;
        private final RemoveListener removeListener;

        EvidenceAdapter(List<EvidenceAttachment> items, RemoveListener removeListener) {
            this.items = items;
            this.removeListener = removeListener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_evidence_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            EvidenceAttachment item = items.get(position);
            holder.name.setText(item.name);
            holder.status.setText(holder.itemView.getContext().getString(R.string.form_evidence_file_size,
                    Math.max(1, (item.sizeBytes + 1023) / 1024)));
            holder.remove.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) removeListener.onRemove(adapterPosition);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView status;
            final View remove;

            Holder(View view) {
                super(view);
                name = view.findViewById(R.id.textFileName);
                status = view.findViewById(R.id.textUploadStatus);
                remove = view.findViewById(R.id.btnRemoveEvidence);
            }
        }
    }
}
