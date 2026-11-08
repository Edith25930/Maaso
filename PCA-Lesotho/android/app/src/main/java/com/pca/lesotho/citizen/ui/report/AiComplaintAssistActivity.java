package com.pca.lesotho.citizen.ui.report;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiComplaintAssistActivity extends AppCompatActivity {
    private static final String TAG = "AiComplaintAssist";

    private final List<Map<String, String>> messages = new ArrayList<>();
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private EditText messageInput;
    private CheckBox consentCheckbox;
    private ProgressBar progressBar;
    private Button sendButton;
    private Button useReplyButton;
    private String latestAssistantReply;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_complaint_assist);

        chatMessages = findViewById(R.id.chatMessages);
        chatScroll = findViewById(R.id.scrollChat);
        messageInput = findViewById(R.id.inputChatMessage);
        consentCheckbox = findViewById(R.id.checkboxAiConsent);
        progressBar = findViewById(R.id.chatProgress);
        sendButton = findViewById(R.id.btnSendChat);
        useReplyButton = findViewById(R.id.btnUseAiReply);

        addBubble(getString(R.string.ai_chat_welcome), false);
        String draft = getIntent().getStringExtra("description");
        if (draft != null && !draft.trim().isEmpty()) {
            addMessage("user", getString(R.string.ai_draft_context, draft.trim()));
            addBubble(getString(R.string.ai_draft_shown, draft.trim()), true);
        }

        consentCheckbox.setOnCheckedChangeListener((button, checked) -> updateSendState());
        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendState();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        sendButton.setOnClickListener(v -> sendMessage());
        useReplyButton.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("assistant_reply", latestAssistantReply);
            setResult(RESULT_OK, result);
            finish();
        });
        findViewById(R.id.btnBackToComplaint).setOnClickListener(v -> finish());
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        SessionManager session = new SessionManager(this);
        if (text.isEmpty() || !consentCheckbox.isChecked()) {
            return;
        }

        if (session.getToken() == null) {
            setLoading(true);
            ApiClient.getService().guestSession().enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    setLoading(false);
                    if (response.isSuccessful() && response.body() != null
                            && response.body().has("data")
                            && response.body().getAsJsonObject("data").has("token")) {
                        JsonObject data = response.body().getAsJsonObject("data");
                        session.saveSession(data.get("token").getAsString(),
                                data.get("user_id").getAsLong(), "Guest", "", true);
                        sendMessage();
                    } else {
                        Toast.makeText(AiComplaintAssistActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    setLoading(false);
                    Toast.makeText(AiComplaintAssistActivity.this, R.string.ai_chat_connection_error, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        addMessage("user", text);
        addBubble(text, true);
        messageInput.setText("");
        setLoading(true);

        Map<String, Object> body = new HashMap<>();
        body.put("messages", recentMessages());
        body.put("language", AppLanguage.isSesotho(this) ? "st" : "en");
        ApiClient.getService().chatWithAssistant(ApiClient.bearer(session.getToken()), body)
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        setLoading(false);
                        JsonObject responseBody = response.body();
                        if (response.isSuccessful() && responseBody != null
                                && responseBody.has("success")
                                && responseBody.get("success").isJsonPrimitive()
                                && responseBody.get("success").getAsBoolean()
                                && responseBody.has("data")
                                && responseBody.get("data").isJsonObject()
                                && responseBody.getAsJsonObject("data").has("reply")) {
                            latestAssistantReply = responseBody.getAsJsonObject("data")
                                    .get("reply").getAsString();
                            addMessage("assistant", latestAssistantReply);
                            addBubble(latestAssistantReply, false);
                            useReplyButton.setVisibility(
                                    getIntent().hasExtra("description") ? View.VISIBLE : View.GONE);
                        } else {
                            Toast.makeText(AiComplaintAssistActivity.this,
                                    getErrorMessage(response), Toast.LENGTH_LONG).show();
                        }
                        updateSendState();
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        Log.w(TAG, "Live assistant request failed", t);
                        setLoading(false);
                        Toast.makeText(AiComplaintAssistActivity.this,
                                R.string.ai_chat_connection_error, Toast.LENGTH_LONG).show();
                        updateSendState();
                    }
                });
    }

    private void addMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        messages.add(message);
    }

    private List<Map<String, String>> recentMessages() {
        List<Map<String, String>> recent = new ArrayList<>();
        int totalLength = 0;
        for (int i = messages.size() - 1; i >= 0 && recent.size() < 12; i--) {
            Map<String, String> message = messages.get(i);
            int length = message.get("content").length();
            if (!recent.isEmpty() && totalLength + length > 7000) break;
            recent.add(0, message);
            totalLength += length;
        }
        return recent;
    }

    private void addBubble(String text, boolean fromUser) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setTextColor(fromUser ? Color.WHITE : Color.rgb(26, 26, 26));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(fromUser ? Color.rgb(11, 37, 69) : Color.WHITE);
        background.setCornerRadius(dp(14));
        bubble.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels * 4 / 5,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = fromUser ? Gravity.END : Gravity.START;
        params.setMargins(0, dp(5), 0, dp(5));
        chatMessages.addView(bubble, params);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        updateSendState();
    }

    private void updateSendState() {
        if (sendButton == null || messageInput == null || consentCheckbox == null || progressBar == null) {
            return;
        }
        sendButton.setEnabled(!progressBar.isShown()
                && consentCheckbox.isChecked()
                && !messageInput.getText().toString().trim().isEmpty());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getErrorMessage(Response<JsonObject> response) {
        JsonObject errorBody = response.body();
        if (errorBody == null && response.errorBody() != null) {
            try {
                JsonElement parsed = JsonParser.parseString(response.errorBody().string());
                if (parsed.isJsonObject()) errorBody = parsed.getAsJsonObject();
            } catch (IOException | JsonParseException e) {
                Log.w(TAG, "Could not read assistant error response", e);
            }
        }
        if (errorBody != null && errorBody.has("error")) {
            JsonElement error = errorBody.get("error");
            if (error.isJsonPrimitive() && !error.getAsString().trim().isEmpty()) {
                return error.getAsString().trim();
            }
        }
        return getString(R.string.error_generic);
    }
}
