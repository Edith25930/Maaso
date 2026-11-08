package com.pca.lesotho.citizen.data;

import android.content.ContentValues;

import java.util.UUID;

/** Plain model for a complaint being built across the 5-step form; backed by SQLite until submission. */
public class ComplaintDraft {

    public String localUuid = UUID.randomUUID().toString();
    public String complainantName;
    public String complainantPhone;
    public String complainantIdNumber;
    public String complainantLocation;
    public String complainantGender;
    public Integer complainantDistrictId;
    public Integer categoryId;
    public String categoryName;
    public boolean categoryUnknown;
    public String incidentDate;      // yyyy-MM-dd
    public String incidentTime;      // HH:mm
    public Integer districtId;
    public String incidentLocationText;
    public String incidentStationName;
    public Integer incidentStationId;
    public Double latitude;
    public Double longitude;
    public boolean locationShared;
    public String description;

    public boolean officerUnknown;
    public String officerName;
    public String officerRank;
    public String badgeNumber;
    public String officerStationName;

    public String witnessesJson = "[]";
    public String evidenceJson = "[]";
    public boolean declarationConfirmed;
    public String syncStatus = "draft";
    public String gcrNumber;

    public ContentValues toContentValues() {
        ContentValues cv = new ContentValues();
        cv.put("local_uuid", localUuid);
        cv.put("complainant_name", complainantName);
        cv.put("complainant_phone", complainantPhone);
        cv.put("complainant_id_number", complainantIdNumber);
        cv.put("complainant_location", complainantLocation);
        cv.put("complainant_gender", complainantGender);
        if (complainantDistrictId != null) cv.put("complainant_district_id", complainantDistrictId);
        if (categoryId != null) cv.put("category_id", categoryId);
        cv.put("category_name", categoryName);
        cv.put("category_unknown", categoryUnknown ? 1 : 0);
        cv.put("incident_date", incidentDate);
        cv.put("incident_time", incidentTime);
        if (districtId != null) cv.put("district_id", districtId);
        cv.put("incident_location_text", incidentLocationText);
        cv.put("incident_station_name", incidentStationName);
        if (incidentStationId != null) cv.put("incident_station_id", incidentStationId);
        if (latitude != null) cv.put("latitude", latitude);
        if (longitude != null) cv.put("longitude", longitude);
        cv.put("location_shared", locationShared ? 1 : 0);
        cv.put("description", description);
        cv.put("officer_unknown", officerUnknown ? 1 : 0);
        cv.put("officer_name", officerName);
        cv.put("officer_rank", officerRank);
        cv.put("badge_number", badgeNumber);
        cv.put("officer_station_name", officerStationName);
        cv.put("witnesses_json", witnessesJson);
        cv.put("evidence_json", evidenceJson);
        cv.put("declaration_confirmed", declarationConfirmed ? 1 : 0);
        cv.put("sync_status", syncStatus);
        cv.put("gcr_number", gcrNumber);
        return cv;
    }
}
