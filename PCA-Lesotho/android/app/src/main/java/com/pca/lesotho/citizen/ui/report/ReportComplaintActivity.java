package com.pca.lesotho.citizen.ui.report;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.SpannableString;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.data.ComplaintDraft;
import com.pca.lesotho.citizen.data.DBHelper;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.home.MainActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportComplaintActivity extends AppCompatActivity {
    public static final int STEP_PERSONAL = 0;
    public static final int STEP_INCIDENT = 1;
    public static final int STEP_OFFICER = 2;
    public static final int STEP_EVIDENCE = 3;
    public static final int STEP_REVIEW = 4;
    private static final int NAVY = Color.rgb(0, 35, 102);
    private static final long MAX_FILE_BYTES = 200L * 1024L * 1024L;
    private static final String[] DISTRICTS = {
            "Select District", "Mokhotlong", "Leribe", "Botha Bothe", "Berea", "Maseru",
            "Mafeteng", "Thaba-Tseka", "Mohale's Hoek", "Qacha's Nek", "Quthing"
    };
    private static final String[] REGIONS = {
            "", "Northern Region", "Northern Region", "Northern Region", "Central Region",
            "Central Region", "Southern Region", "Central Region", "Southern Region",
            "Southern Region", "Southern Region"
    };
    private static final String[] TYPES = {
            "Select", "Death in police custody", "Negligence of duty", "Assault-GBH and common",
            "Theft", "Poor Service Delivery", "Abuse of Power", "Sexual offences", "Other"
    };
    private static final String[] TYPES_ST = {
            "Khetha", "Ho Shoela Matsohong a Mapolesa", "Ho se tsotelle Mosebetsi",
            "Tlhekefetso ea bong", "Bosholu", "Tsebeletso tse sa khotsofatseng",
            "Tsebeliso E Mpe ea Matla", "Tlhekefetso ka thobalano", "Tse ling"
    };

    private final List<Uri> evidenceUris = new ArrayList<>();
    private final List<String> evidenceNames = new ArrayList<>();
    private ActivityResultLauncher<String[]> imagePicker;
    private ActivityResultLauncher<String[]> evidencePicker;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private Uri cameraOutput;
    private Uri idPhotoUri;
    private String idPhotoName = "";
    private String language = "en";
    private ScrollView scrollView;
    private LinearLayout page;
    private LinearLayout cardContent;
    private LinearLayout formContainer;
    private LinearLayout fieldsContainer;
    private LinearLayout reviewContainer;
    private TextView stepIndicator;
    private Button backButton;
    private final List<LinearLayout> formSections = new ArrayList<>();
    private TextView messageBox;
    private TextView idPhotoButton;
    private TextView evidenceButton;
    private Button reviewButton;
    private Button saveDraftButton;
    private EditText nameInput, contactInput, idInput, incidentDateInput, locationInput,
            currentLocationInput, descriptionInput, officersInput, witnessNameInput,
            witnessContactsInput, nextOfKinNameInput, nextOfKinAddressInput;
    private Spinner genderSpinner, districtSpinner, typeSpinner;
    private TextView regionInput;
    private CheckBox oathCheckBox;
    private Button submitButton;
    private View submitProgress;
    private SessionManager session;
    private DBHelper dbHelper;
    private final ComplaintDraft draft = new ComplaintDraft();
    private final Handler draftSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable draftSaveRunnable = this::saveFormStateImmediately;
    private boolean building;
    private String lastMessageEnglish;
    private String lastMessageSesotho;
    private boolean lastMessageSuccess;
    private boolean reviewingForm;
    private int currentStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        dbHelper = new DBHelper(this);
        language = getSharedPreferences("pca_app", MODE_PRIVATE)
                .getString("preferred_language", "en");
        registerPickers();
        if (savedInstanceState != null && savedInstanceState.containsKey("form_state")) {
            buildScreen(parseFormState(savedInstanceState.getString("form_state")));
        } else {
            String savedDraft = dbHelper.getReportFormState(session.getUserId());
            if (savedDraft == null) {
                buildScreen(null);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(tr("Continue your saved complaint?", "Tsoela pele ka tletlebo e bolokiloeng?"))
                        .setMessage(tr("A draft is stored on this device. Resume it or start a new complaint.",
                                "Moralo oa tletlebo o bolokiloe fonong ena. Tsoela pele kapa u qale tletlebo e ncha."))
                        .setPositiveButton(tr("Resume draft", "Tsoela pele"), (dialog, which) ->
                                buildScreen(parseFormState(savedDraft)))
                        .setNegativeButton(tr("Start new", "Qala e ncha"), (dialog, which) -> {
                            dbHelper.clearReportFormState(session.getUserId());
                            buildScreen(null);
                        })
                        .setCancelable(false)
                        .show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (scrollView != null) {
            saveDraft();
            outState.putString("form_state", new Gson().toJson(snapshot()));
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onStop() {
        if (scrollView != null) saveDraft();
        super.onStop();
    }

    public ComplaintDraft getDraft() {
        return draft;
    }

    public void saveDraft() {
        draftSaveHandler.removeCallbacks(draftSaveRunnable);
        saveFormStateImmediately();
    }

    public void jumpToStep(int position) {
        if (position < STEP_PERSONAL || position > STEP_REVIEW) {
            throw new IllegalArgumentException("Unknown complaint form section.");
        }
        scrollView.smoothScrollTo(0, formContainer.getTop());
    }

    private void registerPickers() {
        imagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            if (!retainUriPermission(uri)) showAttachmentPermissionWarning();
            idPhotoUri = uri;
            idPhotoName = getDisplayName(uri);
            refreshUploadLabels();
            persistFormState();
            updateSubmitState();
        });
        evidencePicker = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            List<String> oversized = new ArrayList<>();
            boolean permissionWarning = false;
            for (Uri uri : uris) {
                permissionWarning |= !retainUriPermission(uri);
                long size = getFileSize(uri);
                String fileName = getDisplayName(uri);
                if (size <= 0 || size > MAX_FILE_BYTES) {
                    oversized.add(fileName);
                    continue;
                }
                evidenceUris.add(uri);
                evidenceNames.add(fileName);
            }
            refreshUploadLabels();
            if (permissionWarning) showAttachmentPermissionWarning();
            persistFormState();
            updateSubmitState();
            if (!oversized.isEmpty()) {
                showMessage("Files exceed the 200 MB limit: " + join(oversized),
                        "Lifaele li feta moeli oa 200 MB: " + join(oversized), false);
            }
        });
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraOutput != null) {
                idPhotoUri = cameraOutput;
                idPhotoName = "id-photo.jpg";
                refreshUploadLabels();
                persistFormState();
                updateSubmitState();
            }
        });
    }

    private void buildScreen(Map<String, String> previous) {
        building = true;
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(245, 249, 252));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(24));
        scrollView.addView(page);
        setContentView(scrollView);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView logo = text("PCA", 28, NAVY, true);
        logo.setContentDescription("PCA logo — return home");
        logo.setOnClickListener(v -> goHome());
        top.addView(logo, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView languageButton = text("en".equals(language) ? "Sesotho? ST" : "English? EN",
                14, NAVY, true);
        languageButton.setGravity(Gravity.CENTER);
        languageButton.setPadding(dp(10), dp(8), dp(10), dp(8));
        languageButton.setBackground(border(Color.WHITE, NAVY, 4));
        languageButton.setOnClickListener(v -> switchLanguage());
        top.addView(languageButton);
        page.addView(top);

        CardView card = new CardView(this);
        card.setRadius(dp(10));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(12), 0, dp(20));
        page.addView(card, cardParams);
        cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.addView(cardContent);

        TextView title = text(tr("Complaint Form", "Foromo ea Tletlebo"), 23, NAVY, true);
        title.setGravity(Gravity.CENTER);
        cardContent.addView(title, matchWrap());

        stepIndicator = text("", 13, Color.rgb(82, 96, 112), true);
        stepIndicator.setGravity(Gravity.CENTER);
        stepIndicator.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams stepParams = matchWrap();
        stepParams.topMargin = dp(10);
        cardContent.addView(stepIndicator, stepParams);

        TextView guidance = text(tr(
                "You can save and return later. Only share information you feel safe providing.",
                "U ka boloka foromo ebe u khutla hamorao. Fana feela ka tlhahisoleseling eo u ikutloang u sireletsehile ho fana ka eona."),
                14, Color.rgb(82, 96, 112), false);
        guidance.setPadding(0, dp(8), 0, dp(4));
        cardContent.addView(guidance, matchWrap());
        saveDraftButton = new Button(this);
        saveDraftButton.setText(tr("Save draft and exit", "Boloka moralo ebe u tsoa"));
        saveDraftButton.setAllCaps(false);
        saveDraftButton.setOnClickListener(v -> {
            if (!hasFormProgress()) {
                Toast.makeText(this, tr("Enter some complaint details before saving.",
                        "Kenya lintlha tsa tletlebo pele u boloka."), Toast.LENGTH_LONG).show();
                return;
            }
            saveDraft();
            Toast.makeText(this, tr("Draft saved on this device.", "Moralo o bolokiloe fonong ena."),
                    Toast.LENGTH_LONG).show();
            goHome();
        });
        cardContent.addView(saveDraftButton, matchWrap());

        messageBox = text("", 14, Color.rgb(21, 87, 36), false);
        messageBox.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        messageBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        messageBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams messageParams = matchWrap();
        messageParams.topMargin = dp(16);
        cardContent.addView(messageBox, messageParams);

        formContainer = new LinearLayout(this);
        formContainer.setOrientation(LinearLayout.VERTICAL);
        formSections.clear();
        cardContent.addView(formContainer, matchWrap());
        fieldsContainer = formContainer;
        startSection("Your Details", "Lintlha tsa Hao");
        nameInput = addEdit("name", "Full Name", "Mabitso le Fane", "", true, false);
        nameInput.setText(session.getComplaintPrefillName());
        genderSpinner = addSpinner("gender", "Gender", "Bong",
                englishSesotho("Select", "Khetha", "Male", "Monna", "Female", "Mosali"));
        contactInput = addEdit("contact", "Contact Info", "Linomoro tsa Mohala",
                "", true, false);
        contactInput.setInputType(InputType.TYPE_CLASS_PHONE);
        idInput = addEdit("id_number", "ID Number", "Nomoro ea ID", "", true, false);
        idInput.setText(session.getComplaintPrefillIdNumber());

        startSection("Incident Details", "Lintlha tsa Ketsahalo");
        districtSpinner = addSpinner("district", "District", "Setereke", localizedDistricts());
        regionInput = addReadOnly("region", "Region", "Sebaka (region)");
        addSpinnerListener(districtSpinner, position -> {
            regionInput.setText(position == 0 ? "" : localizedRegion(position));
            updateSubmitState();
        });
        typeSpinner = addSpinner("type", "Type of Complaint", "Mofuta oa Tletlebo", localizedTypes());
        incidentDateInput = addReadOnly("incident_date", "Date & Time of Incident",
                "Letsatsi le Nako ea Ketsahalo");
        incidentDateInput.setHint(tr("Select date and time", "Khetha letsatsi le nako"));
        incidentDateInput.setOnClickListener(v -> pickDateAndTime());
        locationInput = addEdit("location",
                "Incident Location (where the incident occurred)",
                "Sebaka sa Ketsahalo (moo ketsahalo e etsahetseng)",
                tr("e.g., Street name, village/area where the incident occurred",
                        "Mohlala, lebitso la seterata, motse kapa sebaka seo ketsahalo e etsahetseng ho sona"),
                true, false);
        currentLocationInput = addEdit("current_location",
                "Current Location (where you live)", "Sebaka sa bolulo (moo u lulang)",
                tr("Your residential address or area", "Aterese kapa sebaka seo u lulang ho sona"),
                true, false);
        descriptionInput = addEdit("description",
                "Description (write full details of the incident)",
                "Tlhaloso ea ketsahalo (ngola lintlha tsohle ka botlalo)",
                tr("Include dates, times, what happened, who was involved, and any other relevant details.",
                        "Kenyelletsa matsatsi, linako, se etsahetseng, ba amehang le lintlha tse ling tsa bohlokoa."),
                true, true);
        descriptionInput.setMinHeight(dp(100));

        startSection("People Involved", "Batho ba Amehang");
        officersInput = addEdit("officers", "Officers Involved (if known)",
                "Mapolesa a Amelang", tr("Rank/Name/Badge if known", "Boemo/Lebitso/Nomoro ea beche haeba li tsejoa"),
                false, true);
        witnessNameInput = addEdit("witness_name", "Witness Full Name (must be 18+)",
                "Lebitso la Paki (e be kaholimo ho 18)",
                tr("Witness full name", "Lebitso le felletseng la paki"), false, false);
        witnessContactsInput = addEdit("witness_contacts", "Witness Contacts",
                "Linomoro tsa mohala tsa Paki", tr("Phone number with country code if outside Lesotho",
                        "Nomoro ea mohala le khoutu ea naha haeba e tsoa ka ntle ho Lesotho"),
                false, false);
        witnessContactsInput.setInputType(InputType.TYPE_CLASS_PHONE);
        nextOfKinNameInput = addEdit("next_of_kin_name",
                "Next of Kin Full Names (write relationship with this individual in brackets)",
                "Lebitso le Fane tsa Moholisi (Ngla hore le amana joang)",
                tr("Full names of next of kin", "Mabitso a felletseng a mong ka uena"), true, false);
        nextOfKinAddressInput = addEdit("next_of_kin_address", "Next of Kin Address",
                "Aterese ea Moholisi",
                tr("Address of next of kin", "Aterese ea mong ka uena"), true, true);

        startSection("Evidence and Declaration", "Bopaki le Kano");
        addLabel("Upload ID Photo", "Kenya Foto ea ID", true);
        idPhotoButton = uploadButton(tr("Choose from camera or gallery", "Khetha khamera kapa laeborari"));
        idPhotoButton.setOnClickListener(v -> chooseIdPhoto());
        fieldsContainer.addView(idPhotoButton, matchWrap());

        addLabel("Upload Evidence (PDF, images, video, audio, Word, etc.) (optional)",
                "Kenya Bopaki (PDF, litšoantšo, livideo, molumo, Word, jj.) (bo ikhethang)", false);
        evidenceButton = uploadButton(tr(
                "Accepted: PDF, images, videos, audios, Word, text, ZIP (multiple allowed)",
                "E amoheloa: PDF, litšoantšo, livideo, molumo, Word, mongolo, ZIP (tse ngata lia lumelloa)"));
        evidenceButton.setTextSize(13);
        evidenceButton.setOnClickListener(v -> evidencePicker.launch(new String[]{
                "application/pdf", "image/*", "video/*", "audio/*",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain", "application/zip", "application/x-zip-compressed"
        }));
        fieldsContainer.addView(evidenceButton, matchWrap());

        oathCheckBox = new CheckBox(this);
        oathCheckBox.setText(tr(
                "I understand and agree that providing false information is a serious offense and may lead to legal consequences.",
                "Kea utloisisa hore ho fana ka tlhaiso-leseling e fosahetseng ke phoso e tebileng 'me ho ka fella ka likotlo tsa molao"));
        oathCheckBox.setTextColor(NAVY);
        androidx.core.widget.CompoundButtonCompat.setButtonTintList(oathCheckBox,
                android.content.res.ColorStateList.valueOf(NAVY));
        oathCheckBox.setPadding(0, dp(8), 0, dp(8));
        oathCheckBox.setOnCheckedChangeListener((buttonView, checked) -> {
            updateSubmitState();
            persistFormState();
        });
        LinearLayout.LayoutParams oathParams = matchWrap();
        oathParams.topMargin = dp(12);
        fieldsContainer.addView(oathCheckBox, oathParams);

        reviewButton = new Button(this);
        reviewButton.setText(tr("Continue", "Tsoela Pele"));
        reviewButton.setTextColor(Color.WHITE);
        reviewButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        reviewButton.setAllCaps(false);
        reviewButton.setMinHeight(dp(48));
        reviewButton.setBackground(border(NAVY, NAVY, 4));
        reviewButton.setOnClickListener(v -> {
            if (!validateCurrentStep()) return;
            if (currentStep < formSections.size() - 1) showFormStep(currentStep + 1);
            else showReview();
        });
        backButton = new Button(this);
        backButton.setText(tr("Back", "Morao"));
        backButton.setAllCaps(false);
        backButton.setMinHeight(dp(48));
        backButton.setOnClickListener(v -> {
            if (currentStep > 0) showFormStep(currentStep - 1);
        });
        LinearLayout stepButtons = new LinearLayout(this);
        stepButtons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stepButtonsParams = matchWrap();
        stepButtonsParams.topMargin = dp(12);
        formContainer.addView(stepButtons, stepButtonsParams);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        backParams.setMarginEnd(dp(8));
        stepButtons.addView(backButton, backParams);
        stepButtons.addView(reviewButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        reviewContainer = new LinearLayout(this);
        reviewContainer.setOrientation(LinearLayout.VERTICAL);
        reviewContainer.setVisibility(View.GONE);
        cardContent.addView(reviewContainer, matchWrap());
        submitProgress = new android.widget.ProgressBar(this);
        submitProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER;
        reviewContainer.addView(submitProgress, progressParams);

        restore(previous);
        building = false;
        if (previous != null) {
            try {
                currentStep = Math.max(0, Math.min(formSections.size() - 1,
                        Integer.parseInt(previous.getOrDefault("step", "0"))));
            } catch (NumberFormatException e) {
                currentStep = 0;
            }
        }
        showFormStep(currentStep);
        updateSubmitState();
        if (reviewingForm) showReview();
        if (lastMessageEnglish != null) {
            showMessage(lastMessageEnglish, lastMessageSesotho, lastMessageSuccess);
        }
    }

    private String[] englishSesotho(String... values) {
        return new String[]{"en".equals(language) ? values[0] : values[1],
                "en".equals(language) ? values[2] : values[3],
                "en".equals(language) ? values[4] : values[5]};
    }

    private String[] localizedDistricts() {
        if ("en".equals(language)) return DISTRICTS.clone();
        return new String[]{"Khetha Setereke", "Mokhotlong", "Leribe", "Botha Bothe", "Berea",
                "Maseru", "Mafeteng", "Thaba-Tseka", "Mohale's Hoek", "Qacha's Nek", "Quthing"};
    }

    private String[] localizedTypes() {
        return "en".equals(language) ? TYPES.clone() : TYPES_ST.clone();
    }

    private String localizedRegion(int position) {
        if ("en".equals(language)) return REGIONS[position];
        if ("Northern Region".equals(REGIONS[position])) return "Lebatooa la Leboea";
        if ("Central Region".equals(REGIONS[position])) return "Lebatooa la Bohareng";
        return "Lebatooa la Boroa";
    }

    private void startSection(String english, String sesotho) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(12));
        section.setBackground(border(Color.rgb(245, 249, 252), Color.rgb(232, 237, 243), 6));
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(12);
        formContainer.addView(section, sectionParams);
        formSections.add(section);
        TextView title = text(tr(english, sesotho), 16, NAVY, true);
        section.addView(title, matchWrap());
        int sectionIndex = formSections.size() - 1;
        String guidance;
        switch (sectionIndex) {
            case STEP_PERSONAL:
                guidance = tr("Fields marked * are required. Your contact details help the PCA follow up.",
                        "Likarolo tse tšoailoeng ka * lia hlokahala. Lintlha tsa hao tsa boikopanyo li thusa PCA ho u sala morao.");
                break;
            case STEP_INCIDENT:
                guidance = tr("Describe what happened in your own words. If you are unsure of an exact time, give your best estimate.",
                        "Hlalosa se etsahetseng ka mantsoe a hao. Haeba u sa tsebe nako e nepahetseng, fana ka khakanyo.");
                break;
            case STEP_OFFICER:
                guidance = tr("Officer and witness details are optional if you do not know them. Do not guess.",
                        "Lintlha tsa lepolesa le paki ha li tlamehe haeba u sa li tsebe. U se ke ua hakanya.");
                break;
            default:
                guidance = tr("Supporting evidence is optional; an ID photo and declaration are required. Review your answers before submitting.",
                        "Bopaki bo tšehetsang ha bo tlamehe; foto ea ID le kano lia hlokahala. Hlahloba likarabo pele u romela.");
                break;
        }
        TextView helper = text(guidance, 13, Color.rgb(82, 96, 112), false);
        helper.setPadding(0, dp(6), 0, dp(4));
        section.addView(helper, matchWrap());
        fieldsContainer = section;
    }

    private void showFormStep(int step) {
        if (step < 0 || step >= formSections.size()) return;
        currentStep = step;
        for (int i = 0; i < formSections.size(); i++) {
            formSections.get(i).setVisibility(i == currentStep ? View.VISIBLE : View.GONE);
        }
        String[] stepNames = {
                tr("Your details", "Lintlha tsa hau"),
                tr("Incident details", "Lintlha tsa ketsahalo"),
                tr("People involved", "Batho ba amehang"),
                tr("Evidence and declaration", "Bopaki le kano")
        };
        stepIndicator.setText(tr("Step ", "Mohato ") + (currentStep + 1) + tr(" of ", " ho ")
                + formSections.size() + "  ·  " + stepNames[currentStep]);
        stepIndicator.announceForAccessibility(stepIndicator.getText());
        backButton.setVisibility(currentStep == 0 ? View.INVISIBLE : View.VISIBLE);
        reviewButton.setText(currentStep == formSections.size() - 1
                ? tr("Review Complaint", "Hlahloba Tletlebo")
                : tr("Continue", "Tsoela Pele"));
        scrollView.smoothScrollTo(0, 0);
        persistFormState();
    }

    private boolean validateCurrentStep() {
        switch (currentStep) {
            case STEP_PERSONAL:
                if (!filled(nameInput)) {
                    nameInput.setError(tr("Required", "Ho hlokahala"));
                    return false;
                }
                if (!selected(genderSpinner)) {
                    showMessage(tr("Please select your gender.", "Ka kopo khetha bong ba hau."),
                            null, false);
                    return false;
                }
                if (!validPhone(contactInput)) {
                    contactInput.setError(tr("Enter a valid phone number.",
                            "Kenya nomoro ea mohala e nepahetseng."));
                    return false;
                }
                if (!filled(idInput)) {
                    idInput.setError(tr("Required", "Ho hlokahala"));
                    return false;
                }
                return true;
            case STEP_INCIDENT:
                if (!selected(districtSpinner)) {
                    showMessage(tr("Please select a district.", "Ka kopo khetha setereke."),
                            null, false);
                    return false;
                }
                if (!selected(typeSpinner)) {
                    showMessage(tr("Please select a complaint type.",
                            "Ka kopo khetha mofuta oa tletlebo."), null, false);
                    return false;
                }
                if (!filled(incidentDateInput)) incidentDateInput.setError(tr("Required", "Ho hlokahala"));
                else if (!filled(locationInput)) locationInput.setError(tr("Required", "Ho hlokahala"));
                else if (!filled(currentLocationInput)) currentLocationInput.setError(tr("Required", "Ho hlokahala"));
                else if (!filled(descriptionInput)) descriptionInput.setError(tr("Required", "Ho hlokahala"));
                else return true;
                return false;
            case STEP_OFFICER:
                if (!filled(nextOfKinNameInput)) {
                    nextOfKinNameInput.setError(tr("Required", "Ho hlokahala"));
                    return false;
                }
                if (!filled(nextOfKinAddressInput)) {
                    nextOfKinAddressInput.setError(tr("Required", "Ho hlokahala"));
                    return false;
                }
                if (witnessContactsInput.getText().length() > 0 && !validPhone(witnessContactsInput)) {
                    witnessContactsInput.setError(tr("Enter a valid phone number.",
                            "Kenya nomoro ea mohala e nepahetseng."));
                    return false;
                }
                return true;
            case STEP_EVIDENCE:
                if (idPhotoUri == null) {
                    showMessage(tr("Please attach an ID photo.", "Ka kopo hokela foto ea ID."),
                            null, false);
                    return false;
                }
                if (!oathCheckBox.isChecked()) {
                    showMessage(tr("Please accept the declaration.", "Ka kopo amohela kano."),
                            null, false);
                    return false;
                }
                return true;
            default:
                return false;
        }
    }

    private void showReview() {
        reviewingForm = true;
        formContainer.setVisibility(View.GONE);
        reviewContainer.removeAllViews();
        reviewContainer.setVisibility(View.VISIBLE);
        TextView heading = text(tr("Review your complaint", "Hlahloba tletlebo ea hau"),
                20, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        reviewContainer.addView(heading, matchWrap());

        addReviewSection("Your Details", "Lintlha tsa Hao");
        addReviewItem("Full Name", "Mabitso le Fane", nameInput.getText().toString());
        addReviewItem("Gender", "Bong", String.valueOf(genderSpinner.getSelectedItem()));
        addReviewItem("Contact Info", "Linomoro tsa Mohala", contactInput.getText().toString());
        addReviewItem("ID Number", "Nomoro ea ID", idInput.getText().toString());

        addReviewSection("Incident Details", "Lintlha tsa Ketsahalo");
        addReviewItem("District", "Setereke", String.valueOf(districtSpinner.getSelectedItem()));
        addReviewItem("Region", "Sebaka", localizedRegion(districtSpinner.getSelectedItemPosition()));
        addReviewItem("Type of Complaint", "Mofuta oa Tletlebo",
                String.valueOf(typeSpinner.getSelectedItem()));
        addReviewItem("Date & Time of Incident", "Letsatsi le Nako ea Ketsahalo",
                incidentDateInput.getText().toString());
        addReviewItem("Incident Location", "Sebaka sa Ketsahalo", locationInput.getText().toString());
        addReviewItem("Current Location", "Sebaka sa bolulo", currentLocationInput.getText().toString());
        addReviewItem("Description", "Tlhaloso", descriptionInput.getText().toString());

        addReviewSection("People Involved", "Batho ba Amehang");
        addReviewItem("Officers Involved", "Mapolesa a Amelang", officersInput.getText().toString());
        addReviewItem("Witness Full Name", "Lebitso la Paki", witnessNameInput.getText().toString());
        addReviewItem("Witness Contacts", "Linomoro tsa Paki", witnessContactsInput.getText().toString());
        addReviewItem("Next of Kin Full Names", "Lebitso la Moholisi", nextOfKinNameInput.getText().toString());
        addReviewItem("Next of Kin Address", "Aterese ea Moholisi", nextOfKinAddressInput.getText().toString());

        addReviewSection("Evidence and Declaration", "Bopaki le Kano");
        addReviewItem("ID Photo", "Foto ea ID", idPhotoName);
        addReviewItem("Evidence Files", "Lifaele tsa Bopaki",
                evidenceNames.isEmpty() ? tr("None attached", "Ha ho tse kentsoeng") : join(evidenceNames));
        addReviewItem("Oath Accepted", "Kano e amohetsoe",
                oathCheckBox.isChecked() ? tr("Yes", "E") : tr("No", "Che"));

        Button editButton = new Button(this);
        editButton.setText(tr("Edit Complaint", "Fetola Tletlebo"));
        editButton.setAllCaps(false);
        editButton.setOnClickListener(v -> {
            reviewingForm = false;
            reviewContainer.setVisibility(View.GONE);
            formContainer.setVisibility(View.VISIBLE);
            showFormStep(STEP_EVIDENCE);
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(14);
        reviewContainer.addView(editButton, buttonParams);

        submitButton = new Button(this);
        submitButton.setText(tr("Submit Complaint", "Romela Tletlebo"));
        submitButton.setTextColor(Color.WHITE);
        submitButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        submitButton.setAllCaps(false);
        submitButton.setBackground(border(NAVY, NAVY, 4));
        submitButton.setOnClickListener(v -> submitComplaint());
        buttonParams = matchWrap();
        buttonParams.topMargin = dp(8);
        reviewContainer.addView(submitButton, buttonParams);
        reviewContainer.addView(submitProgress, new LinearLayout.LayoutParams(dp(36), dp(36)));
        updateSubmitState();
        scrollView.smoothScrollTo(0, 0);
    }

    private void addReviewSection(String english, String sesotho) {
        TextView header = text(tr(english, sesotho), 16, NAVY, true);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(18);
        reviewContainer.addView(header, params);
    }

    private void addReviewItem(String english, String sesotho, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        item.setBackground(border(Color.WHITE, Color.rgb(232, 237, 243), 4));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(6);
        reviewContainer.addView(item, params);
        item.addView(text(tr(english, sesotho), 12, NAVY, true), matchWrap());
        item.addView(text(value.trim().isEmpty() ? "—" : value, 14, Color.DKGRAY, false), matchWrap());
    }

    private EditText addEdit(String key, String enLabel, String stLabel, String hint,
                             boolean required, boolean multiline) {
        TextView label = addLabel(enLabel, stLabel, required);
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setTag(key);
        if ("id_number".equals(key)) {
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        }
        if ("contact".equals(key)) {
            input.setHint(tr("Phone number with country code if outside Lesotho",
                    "Nomoro ea mohala le khoutu ea naha haeba e tsoa ka ntle ho Lesotho"));
        }
        input.setTextSize(15);
        input.setMinHeight(dp(52));
        input.setTextColor(Color.rgb(30, 30, 30));
        input.setHint(hint);
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
        input.setBackground(border(Color.WHITE, Color.rgb(204, 204, 204), 4));
        input.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (multiline) {
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinHeight(dp(82));
        } else {
            input.setSingleLine(true);
        }
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(4);
        fieldsContainer.addView(input, params);
        input.addTextChangedListener(new SimpleWatcher(() -> {
            if (input.getError() != null) input.setError(null);
            updateSubmitState();
            persistFormState();
        }));
        return input;
    }

    private EditText addReadOnly(String key, String enLabel, String stLabel) {
        TextView label = addLabel(enLabel, stLabel, true);
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setTag(key);
        input.setTextSize(15);
        input.setTextColor(Color.rgb(30, 30, 30));
        input.setHintTextColor(Color.GRAY);
        input.setPadding(dp(10), dp(12), dp(10), dp(12));
        input.setFocusable(false);
        input.setClickable(true);
        input.setSingleLine(true);
        input.setMinHeight(dp(48));
        input.setBackground(border(Color.WHITE, Color.rgb(204, 204, 204), 4));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(4);
        fieldsContainer.addView(input, params);
        return input;
    }

    private Spinner addSpinner(String key, String enLabel, String stLabel, String[] choices) {
        TextView label = addLabel(enLabel, stLabel, true);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        label.setLabelFor(spinner.getId());
        spinner.setTag(key);
        spinner.setPadding(dp(6), dp(2), dp(6), dp(2));
        spinner.setBackground(border(Color.WHITE, Color.rgb(204, 204, 204), 4));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, choices) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(position == 0 ? Color.GRAY : Color.rgb(30, 30, 30));
                view.setPadding(dp(4), dp(7), dp(4), dp(7));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams params = matchWrap();
        params.height = dp(48);
        params.topMargin = dp(4);
        fieldsContainer.addView(spinner, params);
        addSpinnerListener(spinner, position -> updateSubmitState());
        return spinner;
    }

    private void addSpinnerListener(Spinner spinner, PositionListener listener) {
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                listener.onSelected(position);
                persistFormState();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                listener.onSelected(0);
                persistFormState();
            }
        });
    }

    private TextView addLabel(String en, String st, boolean required) {
        TextView label = text("", 14, NAVY, true);
        label.setText(labelText(en, st, required));
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(14);
        fieldsContainer.addView(label, params);
        return label;
    }

    private void switchLanguage() {
        Map<String, String> previous = snapshot();
        language = "en".equals(language) ? "st" : "en";
        getSharedPreferences("pca_app", MODE_PRIVATE).edit()
                .putString("preferred_language", language).apply();
        buildScreen(previous);
    }

    private Map<String, String> snapshot() {
        Map<String, String> values = new HashMap<>();
        saveText(values, "name", nameInput);
        saveText(values, "contact", contactInput);
        saveText(values, "id_number", idInput);
        saveText(values, "incident_date", incidentDateInput);
        saveText(values, "location", locationInput);
        saveText(values, "current_location", currentLocationInput);
        saveText(values, "description", descriptionInput);
        saveText(values, "officers", officersInput);
        saveText(values, "witness_name", witnessNameInput);
        saveText(values, "witness_contacts", witnessContactsInput);
        saveText(values, "next_of_kin_name", nextOfKinNameInput);
        saveText(values, "next_of_kin_address", nextOfKinAddressInput);
        if (genderSpinner != null) values.put("gender", Integer.toString(genderSpinner.getSelectedItemPosition()));
        values.put("step", Integer.toString(currentStep));
        values.put("reviewing", Boolean.toString(reviewingForm));
        if (districtSpinner != null && districtSpinner.getSelectedItemPosition() > 0) {
            values.put("district", DISTRICTS[districtSpinner.getSelectedItemPosition()]);
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() > 0) {
            values.put("type", TYPES[typeSpinner.getSelectedItemPosition()]);
        }
        if (oathCheckBox != null) values.put("oath", Boolean.toString(oathCheckBox.isChecked()));
        values.put("id_photo_uri", idPhotoUri == null ? "" : idPhotoUri.toString());
        values.put("id_photo_name", idPhotoName);
        List<String> evidenceUriStrings = new ArrayList<>();
        for (Uri uri : evidenceUris) evidenceUriStrings.add(uri.toString());
        values.put("evidence_uris", new Gson().toJson(evidenceUriStrings));
        values.put("evidence_names", new Gson().toJson(evidenceNames));
        return values;
    }

    private void restore(Map<String, String> values) {
        if (values == null) return;
        restoreText(values, "name", nameInput);
        restoreText(values, "contact", contactInput);
        restoreText(values, "id_number", idInput);
        restoreText(values, "incident_date", incidentDateInput);
        restoreText(values, "location", locationInput);
        restoreText(values, "current_location", currentLocationInput);
        restoreText(values, "description", descriptionInput);
        restoreText(values, "officers", officersInput);
        restoreText(values, "witness_name", witnessNameInput);
        restoreText(values, "witness_contacts", witnessContactsInput);
        restoreText(values, "next_of_kin_name", nextOfKinNameInput);
        restoreText(values, "next_of_kin_address", nextOfKinAddressInput);
        try {
            setSpinner(genderSpinner, Integer.parseInt(values.getOrDefault("gender", "0")));
        } catch (NumberFormatException e) {
            setSpinner(genderSpinner, 0);
        }
        int districtPosition = indexOf(DISTRICTS, values.get("district"));
        setSpinner(districtSpinner, districtPosition);
        regionInput.setText(districtPosition > 0 ? localizedRegion(districtPosition) : "");
        setSpinner(typeSpinner, indexOf(TYPES, values.get("type")));
        oathCheckBox.setChecked(Boolean.parseBoolean(values.get("oath")));
        String photoUri = values.get("id_photo_uri");
        idPhotoUri = photoUri == null || photoUri.isEmpty() ? null : Uri.parse(photoUri);
        idPhotoName = values.getOrDefault("id_photo_name", "");
        evidenceUris.clear();
        evidenceNames.clear();
        try {
            JsonElement uriElement = JsonParser.parseString(values.getOrDefault("evidence_uris", "[]"));
            JsonElement nameElement = JsonParser.parseString(values.getOrDefault("evidence_names", "[]"));
            if (uriElement.isJsonArray() && nameElement.isJsonArray()) {
                for (JsonElement element : uriElement.getAsJsonArray()) {
                    evidenceUris.add(Uri.parse(element.getAsString()));
                }
                for (JsonElement element : nameElement.getAsJsonArray()) {
                    evidenceNames.add(element.getAsString());
                }
                if (evidenceUris.size() != evidenceNames.size()) {
                    evidenceUris.clear();
                    evidenceNames.clear();
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ReportComplaint", "Could not restore saved evidence selections.", e);
            evidenceUris.clear();
            evidenceNames.clear();
        }
        reviewingForm = Boolean.parseBoolean(values.getOrDefault("reviewing", "false"));
        refreshUploadLabels();
    }

    private void setSpinner(Spinner spinner, int position) {
        if (spinner != null && position >= 0 && position < spinner.getCount()) spinner.setSelection(position);
    }

    private int indexOf(String[] values, String value) {
        if (value == null) return 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    private void restoreText(Map<String, String> values, String key, TextView target) {
        if (values.containsKey(key) && target != null) target.setText(values.get(key));
    }

    private void saveText(Map<String, String> values, String key, TextView source) {
        if (source != null) values.put(key, source.getText().toString());
    }

    private void updateSubmitState() {
        if (reviewButton == null || building) return;
        boolean valid = filled(nameInput) && selected(genderSpinner) && validPhone(contactInput)
                && filled(idInput) && selected(districtSpinner) && selected(typeSpinner)
                && regionInput != null && !regionInput.getText().toString().trim().isEmpty()
                && filled(incidentDateInput) && filled(locationInput) && filled(currentLocationInput)
                && filled(descriptionInput) && filled(nextOfKinNameInput) && filled(nextOfKinAddressInput)
                && idPhotoUri != null && oathCheckBox.isChecked()
                && (witnessContactsInput.getText().length() == 0 || validPhone(witnessContactsInput));
        reviewButton.setEnabled(true);
        reviewButton.setAlpha(1f);
        if (submitButton != null) {
            submitButton.setEnabled(valid);
            submitButton.setAlpha(valid ? 1f : 0.55f);
        }
    }

    private void persistFormState() {
        if (building) return;
        draftSaveHandler.removeCallbacks(draftSaveRunnable);
        if (!hasFormProgress()) {
            dbHelper.clearReportFormState(session.getUserId());
            return;
        }
        draftSaveHandler.postDelayed(draftSaveRunnable, 500);
    }

    private void saveFormStateImmediately() {
        if (hasFormProgress()) {
            dbHelper.saveReportFormState(new Gson().toJson(snapshot()), session.getUserId());
        } else {
            dbHelper.clearReportFormState(session.getUserId());
        }
    }

    private boolean hasFormProgress() {
        return filled(contactInput) || filled(descriptionInput) || filled(locationInput)
                || filled(currentLocationInput) || filled(incidentDateInput) || filled(officersInput)
                || filled(witnessNameInput) || filled(witnessContactsInput) || filled(nextOfKinNameInput)
                || filled(nextOfKinAddressInput) || selected(genderSpinner) || selected(districtSpinner)
                || selected(typeSpinner) || oathCheckBox != null && oathCheckBox.isChecked()
                || idPhotoUri != null || !evidenceUris.isEmpty()
                || filled(nameInput) && !nameInput.getText().toString().equals(session.getComplaintPrefillName())
                || filled(idInput) && !idInput.getText().toString().equals(session.getComplaintPrefillIdNumber());
    }

    private Map<String, String> parseFormState(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) values.put(entry.getKey(), value.getAsString());
            }
            return values;
        } catch (RuntimeException e) {
            android.util.Log.e("ReportComplaint", "Saved complaint draft could not be read.", e);
            dbHelper.clearReportFormState(session.getUserId());
            Toast.makeText(this, tr("The saved draft could not be opened.", "Moralo o bolokiloeng ha oa khona ho buloa."),
                    Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private boolean retainUriPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return true;
        } catch (SecurityException e) {
            android.util.Log.w("ReportComplaint", "Selected attachment permission could not be retained.", e);
            return false;
        }
    }

    private void showAttachmentPermissionWarning() {
        Toast.makeText(this, tr(
                "A selected file may need to be chosen again if you leave before submitting.",
                "U ka hloka ho khetha faele e khethiloeng hape haeba u ka tsoa pele u e romela."),
                Toast.LENGTH_LONG).show();
    }

    private boolean filled(TextView view) {
        return view != null && !view.getText().toString().trim().isEmpty();
    }

    private boolean selected(Spinner spinner) {
        return spinner != null && spinner.getSelectedItemPosition() > 0;
    }

    private boolean validPhone(EditText input) {
        return normalizedPhone(input) != null;
    }

    private String normalizedPhone(EditText input) {
        if (input == null || input.getText().toString().trim().isEmpty()) return null;
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number =
                    phoneUtil.parse(input.getText().toString().trim(), "LS");
            return phoneUtil.isPossibleNumber(number)
                    ? phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164) : null;
        } catch (NumberParseException e) {
            return null;
        }
    }

    private void chooseIdPhoto() {
        new android.app.AlertDialog.Builder(this)
                .setItems(new String[]{tr("Take a photo", "Nka foto"), tr("Choose from gallery", "Khetha laeboraring")},
                        (dialog, which) -> {
                            if (which == 0) takeIdPhoto();
                            else imagePicker.launch(new String[]{"image/*"});
                        }).show();
    }

    private void takeIdPhoto() {
        try {
            File folder = new File(getCacheDir(), "complaint-id");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Unable to create photo storage.");
            File photo = File.createTempFile("id_photo_", ".jpg", folder);
            cameraOutput = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            cameraLauncher.launch(cameraOutput);
        } catch (IOException | IllegalArgumentException e) {
            showMessage(tr("Camera unavailable: " + e.getMessage(), "Khamera ha e fumanehe: " + e.getMessage()), null, false);
        }
    }

    private void pickDateAndTime() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) ->
                new TimePickerDialog(this, (timeView, hour, minute) -> {
                    incidentDateInput.setText(String.format(Locale.US, "%04d-%02d-%02d %02d:%02d",
                            year, month + 1, day, hour, minute));
                    updateSubmitState();
                    persistFormState();
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true)
                        .show(), now.get(Calendar.YEAR), now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void refreshUploadLabels() {
        if (idPhotoButton != null) {
            idPhotoButton.setText(idPhotoName.isEmpty()
                    ? tr("Choose from camera or gallery", "Khetha khamera kapa laeborari")
                    : idPhotoName);
        }
        if (evidenceButton != null) {
            String note = tr("Accepted: PDF, images, videos, audios, Word, text, ZIP (multiple allowed)",
                    "E amoheloa: PDF, litšoantšo, livideo, molumo, Word, mongolo, ZIP (tse ngata lia lumelloa)");
            evidenceButton.setText(evidenceNames.isEmpty() ? note : note + "\n" + join(evidenceNames));
        }
    }

    private void submitComplaint() {
        if (!validPhone(contactInput)) {
            contactInput.setError(tr("Enter a valid phone number.",
                    "Kenya nomoro ea mohala e nepahetseng."));
            return;
        }
        if (witnessContactsInput.getText().length() > 0 && !validPhone(witnessContactsInput)) {
            witnessContactsInput.setError(tr("Enter a valid phone number.",
                    "Kenya nomoro ea mohala e nepahetseng."));
            return;
        }
        if (!submitButton.isEnabled()) {
            showMessage(tr("Please complete all required fields and accept the oath.",
                    "Ka kopo tlatsa likarolo tsohle tse hlokahalang 'me u amohele kano."), null, false);
            return;
        }
        if (!session.isLoggedIn() || session.isGuest()) {
            showMessage(tr("Please sign in to submit your complaint.", "Kena ho romela tletlebo ea hau."), null, false);
            return;
        }

        String district = DISTRICTS[districtSpinner.getSelectedItemPosition()];
        String complaintType = TYPES[typeSpinner.getSelectedItemPosition()];
        if ("Other".equals(complaintType) && complaintType.trim().isEmpty()) {
            showMessage(tr("Please specify the complaint type.", "Ka kopo hlalosa mofuta oa tletlebo."), null, false);
            return;
        }
        Map<String, RequestBody> fields = new HashMap<>();
        put(fields, "name", nameInput.getText().toString().trim());
        put(fields, "gender", genderSpinner.getSelectedItemPosition() == 1 ? "male" : "female");
        put(fields, "contact", normalizedPhone(contactInput));
        put(fields, "id_number", idInput.getText().toString().trim());
        put(fields, "district", district);
        put(fields, "region", REGIONS[districtSpinner.getSelectedItemPosition()]);
        put(fields, "type", complaintType.trim());
        put(fields, "incident_date", incidentDateInput.getText().toString());
        put(fields, "location", locationInput.getText().toString().trim());
        put(fields, "current_location", currentLocationInput.getText().toString().trim());
        put(fields, "description", descriptionInput.getText().toString().trim());
        put(fields, "officers", officersInput.getText().toString().trim());
        put(fields, "witness_name", witnessNameInput.getText().toString().trim());
        put(fields, "witness_contacts", witnessContactsInput.getText().length() == 0
                ? "" : normalizedPhone(witnessContactsInput));
        put(fields, "next_of_kin_name", nextOfKinNameInput.getText().toString().trim());
        put(fields, "next_of_kin_address", nextOfKinAddressInput.getText().toString().trim());
        put(fields, "oath", "1");
        List<MultipartBody.Part> files = new ArrayList<>();
        files.add(filePart("id_photo", idPhotoUri, idPhotoName));
        for (int i = 0; i < evidenceUris.size(); i++) {
            files.add(filePart("evidence[]", evidenceUris.get(i), evidenceNames.get(i)));
        }

        submitButton.setEnabled(false);
        submitProgress.setVisibility(View.VISIBLE);
        ApiClient.getService().submitComplaint(ApiClient.bearer(session.getToken()), fields, files)
                .enqueue(new Callback<JsonObject>() {
                    @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        submitProgress.setVisibility(View.GONE);
                        JsonObject body = response.body();
                        if (response.isSuccessful() && body != null && body.has("success")
                                && body.get("success").getAsBoolean()
                                && body.has("data") && body.getAsJsonObject("data").has("gcr_number")) {
                            String gcr = body.getAsJsonObject("data").get("gcr_number").getAsString();
                            showSuccess(gcr);
                        } else {
                            String error = responseError(response);
                            showMessage("Error: " + error, "Phoso: " + error, false);
                            updateSubmitState();
                        }
                    }

                    @Override public void onFailure(Call<JsonObject> call, Throwable error) {
                        submitProgress.setVisibility(View.GONE);
                        showMessage("Error: " + error.getMessage(),
                                "Phoso: " + error.getMessage(), false);
                        updateSubmitState();
                    }
                });
    }

    private void put(Map<String, RequestBody> fields, String key, String value) {
        fields.put(key, RequestBody.create(MediaType.parse("text/plain"), value));
    }

    private MultipartBody.Part filePart(String field, Uri uri, String filename) {
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "application/octet-stream";
        return MultipartBody.Part.createFormData(field, filename,
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

        @Override public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Could not open " + getDisplayName(uri));
                byte[] buffer = new byte[16 * 1024];
                long written = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    written += count;
                    if (written > MAX_FILE_BYTES) {
                        throw new IOException(getDisplayName(uri) + " exceeds the 200 MB limit.");
                    }
                    sink.write(buffer, 0, count);
                }
            }
        }
    }

    private String responseError(Response<JsonObject> response) {
        JsonObject body = response.body();
        if (body != null && body.has("error")) return body.get("error").getAsString();
        if (response.errorBody() != null) {
            try {
                JsonObject parsed = JsonParser.parseString(response.errorBody().string()).getAsJsonObject();
                if (parsed.has("error")) return parsed.get("error").getAsString();
            } catch (Exception e) {
                android.util.Log.w("ReportComplaint", "Could not parse submission error.", e);
            }
        }
        return tr("The complaint could not be submitted.", "Tletlebo ha ea khona ho romeloa.");
    }

    private void showSuccess(String gcr) {
        String noteEn = "Click the logo to return to the home page, and then click \"Login\" to track your complaint using your credentials.";
        String noteSt = "Tobetsa letšoao ho khutlela leqepheng la lapeng, ebe u tobetsa \"Kena\" ho latela tletlebo ea hau.";
        showMessage("✅ Complaint submitted successfully. GCR Number: " + gcr + "\n\n" + noteEn,
                "✅ Tletlebo e rometsoe ka katleho. Nomoro ea GCR: " + gcr + "\n\n" + noteSt, true);
        dbHelper.clearReportFormState(session.getUserId());
        clearFormAfterSuccess();
    }

    private void clearFormAfterSuccess() {
        draftSaveHandler.removeCallbacks(draftSaveRunnable);
        building = true;
        reviewingForm = false;
        reviewContainer.setVisibility(View.GONE);
        formContainer.setVisibility(View.VISIBLE);
        getSharedPreferences("pca_complaint_form", MODE_PRIVATE).edit().clear().apply();
        session.clearComplaintPrefill();
        nameInput.setText("");
        contactInput.setText("");
        idInput.setText("");
        incidentDateInput.setText("");
        locationInput.setText("");
        currentLocationInput.setText("");
        descriptionInput.setText("");
        officersInput.setText("");
        witnessNameInput.setText("");
        witnessContactsInput.setText("");
        nextOfKinNameInput.setText("");
        nextOfKinAddressInput.setText("");
        setSpinner(genderSpinner, 0);
        setSpinner(districtSpinner, 0);
        setSpinner(typeSpinner, 0);
        regionInput.setText("");
        oathCheckBox.setChecked(false);
        currentStep = 0;
        evidenceUris.clear();
        evidenceNames.clear();
        idPhotoUri = null;
        idPhotoName = "";
        refreshUploadLabels();
        building = false;
        updateSubmitState();
        showFormStep(currentStep);
    }

    private void showMessage(String english, String sesotho, boolean success) {
        if (messageBox == null) return;
        lastMessageEnglish = english;
        lastMessageSesotho = sesotho;
        lastMessageSuccess = success;
        messageBox.setText("en".equals(language) || sesotho == null ? english : sesotho);
        messageBox.setTextColor(success ? Color.rgb(21, 87, 36) : Color.rgb(114, 28, 36));
        messageBox.setBackground(border(success ? Color.rgb(212, 237, 218) : Color.rgb(248, 215, 218),
                success ? Color.rgb(212, 237, 218) : Color.rgb(248, 215, 218), 4));
        messageBox.setTypeface(success ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        messageBox.setVisibility(View.VISIBLE);
        scrollView.post(() -> scrollView.smoothScrollTo(0, messageBox.getTop()));
    }

    private void goHome() {
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    private long getFileSize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (RuntimeException e) {
            android.util.Log.w("ReportComplaint", "Could not read selected file size.", e);
        }
        return -1;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException e) {
            android.util.Log.w("ReportComplaint", "Could not read selected file name.", e);
        }
        return uri.getLastPathSegment() == null ? "attachment" : uri.getLastPathSegment();
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private SpannableString labelText(String en, String st, boolean required) {
        String base = tr(en, st);
        String value = required ? base + " *" : base;
        SpannableString result = new SpannableString(value);
        if (required) result.setSpan(new ForegroundColorSpan(Color.RED), value.length() - 1,
                value.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

    private String tr(String english, String sesotho) {
        return "en".equals(language) ? english : sesotho;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView uploadButton(String value) {
        TextView view = text(value, 14, NAVY, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(12), dp(10), dp(12));
        view.setMinHeight(dp(48));
        view.setBackground(border(Color.WHITE, Color.rgb(204, 204, 204), 4));
        return view;
    }

    private GradientDrawable border(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface PositionListener { void onSelected(int position); }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable callback;
        SimpleWatcher(Runnable callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { callback.run(); }
    }
}
