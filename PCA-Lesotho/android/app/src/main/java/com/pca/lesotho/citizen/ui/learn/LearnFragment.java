package com.pca.lesotho.citizen.ui.learn;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LearnFragment extends Fragment {
    private LinearLayout content;
    private TextView status;
    private final List<JsonObject> articles = new ArrayList<>();
    private final List<JsonObject> aboutSections = new ArrayList<>();
    private int requestsComplete;
    private int requestsFailed;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_learn, container, false);
        content = view.findViewById(R.id.learnContent);
        status = view.findViewById(R.id.learnStatus);
        loadArticles();
        loadAboutPca();
        return view;
    }

    private void loadArticles() {
        ApiClient.getService().getArticles().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonArray rows = successfulData(response);
                if (rows == null) requestsFailed++;
                else rows.forEach(row -> articles.add(row.getAsJsonObject()));
                onRequestComplete();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                requestsFailed++;
                onRequestComplete();
            }
        });
    }

    private void loadAboutPca() {
        ApiClient.getService().getAboutPcaSections().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonArray rows = successfulData(response);
                if (rows == null) requestsFailed++;
                else rows.forEach(row -> aboutSections.add(row.getAsJsonObject()));
                onRequestComplete();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                requestsFailed++;
                onRequestComplete();
            }
        });
    }

    private JsonArray successfulData(Response<JsonObject> response) {
        if (!response.isSuccessful() || response.body() == null
                || !response.body().has("success") || !response.body().get("success").getAsBoolean()
                || !response.body().has("data")) return null;
        return response.body().getAsJsonArray("data");
    }

    private void onRequestComplete() {
        requestsComplete++;
        if (requestsComplete < 2 || !isAdded()) return;
        renderContent();
        if (requestsFailed > 0) {
            status.setText(R.string.learn_content_partial);
            status.setVisibility(View.VISIBLE);
            if (articles.isEmpty() && aboutSections.isEmpty()) {
                Toast.makeText(requireContext(), R.string.learn_content_load_failed, Toast.LENGTH_LONG).show();
            }
        } else {
            status.setVisibility(View.GONE);
        }
    }

    private void renderContent() {
        content.removeAllViews();
        if (!articles.isEmpty()) {
            addSectionHeading(getString(R.string.learn_misconduct_topics));
            for (JsonObject article : articles) {
                addCard(localizedText(article, "title"), localizedText(article, "definition"));
                addOptionalDetail(article, "examples", R.string.learn_examples);
                addOptionalDetail(article, "when_to_report", R.string.learn_when_to_report);
                addOptionalDetail(article, "useful_info", R.string.learn_useful_information);
            }
        } else {
            addSectionHeading(getString(R.string.learn_misconduct_topics));
            addCard(getString(R.string.learn_misconduct_topics), getString(R.string.learn_articles_not_published));
        }
        if (!aboutSections.isEmpty()) {
            addSectionHeading(getString(R.string.learn_about_pca));
            for (JsonObject section : aboutSections) {
                addCard(localizedText(section, "title"), localizedText(section, "body"));
            }
        }
    }

    private void addOptionalDetail(JsonObject article, String key, int labelId) {
        String value = localizedText(article, key);
        if (!value.isEmpty()) addCard(getString(labelId), value);
    }

    private String localizedText(JsonObject object, String key) {
        boolean sesotho = AppLanguage.isSesotho(requireContext());
        String preferredKey = key + (sesotho ? "_st" : "_en");
        String fallbackKey = key + (sesotho ? "_en" : "_st");
        String preferred = text(object, preferredKey);
        return preferred.isEmpty() ? text(object, fallbackKey) : preferred;
    }

    private String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private void addSectionHeading(String label) {
        TextView heading = new TextView(requireContext());
        heading.setText(label);
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(ContextCompat.getColor(requireContext(), R.color.pca_navy));
        heading.setPadding(0, 20, 0, 10);
        content.addView(heading);
    }

    private void addCard(String title, String body) {
        TextView card = new TextView(requireContext());
        card.setText(title + "\n\n" + body);
        card.setTextSize(15);
        card.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        card.setBackgroundResource(R.drawable.bg_learn_card);
        card.setPadding(16, 14, 16, 14);
        card.setLineSpacing(3, 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 10;
        content.addView(card, params);
    }
}
