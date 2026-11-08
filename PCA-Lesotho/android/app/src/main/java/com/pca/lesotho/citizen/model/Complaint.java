package com.pca.lesotho.citizen.model;

import com.google.gson.annotations.SerializedName;

public class Complaint {
    @SerializedName("complaint_id")
    public long complaintId;
    @SerializedName("gcr_number")
    public String gcrNumber;
    @SerializedName("category_name_en")
    public String categoryNameEn;
    @SerializedName("category_name_st")
    public String categoryNameSt;
    @SerializedName("status_code")
    public String statusCode;
    @SerializedName("status_label_en")
    public String statusLabelEn;
    @SerializedName("status_label_st")
    public String statusLabelSt;
    @SerializedName("color_code")
    public String colorCode;
    @SerializedName("incident_date")
    public String incidentDate;
    @SerializedName("updated_at")
    public String updatedAt;
    @SerializedName("expected_completion_date")
    public String expectedCompletionDate;
    @SerializedName("latest_update_en")
    public String latestUpdateEn;
    @SerializedName("latest_update_st")
    public String latestUpdateSt;
}
