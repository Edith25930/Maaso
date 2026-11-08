package com.pca.lesotho.citizen.network;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/** Maps 1:1 onto /API_ENDPOINTS.md and the PHP backend in /backend. */
public interface ApiService {

    @POST("auth/register.php")
    Call<JsonObject> register(@Body Map<String, Object> body);

    @POST("auth/login.php")
    Call<JsonObject> login(@Body Map<String, Object> body);

    @POST("auth/logout.php")
    Call<JsonObject> logout(@Header("Authorization") String bearerToken);

    @POST("auth/request-password-reset.php")
    Call<JsonObject> requestPasswordReset(@Body Map<String, Object> body);

    @POST("auth/request-email-verification.php")
    Call<JsonObject> requestEmailVerification(@Header("Authorization") String bearerToken);

    @POST("devices/register.php")
    Call<JsonObject> registerDeviceToken(
            @Header("Authorization") String bearerToken,
            @Body Map<String, Object> body);

    @POST("devices/unregister.php")
    Call<JsonObject> unregisterDeviceToken(
            @Header("Authorization") String bearerToken,
            @Body Map<String, Object> body);

    @POST("auth/guest.php")
    Call<JsonObject> guestSession();

    @POST("ai/chat.php")
    Call<JsonObject> chatWithAssistant(
            @Header("Authorization") String bearerToken,
            @Body Map<String, Object> body);

    @GET("complaints/categories.php")
    Call<JsonObject> getCategories();

    @GET("complaints/list.php")
    Call<JsonObject> getComplaints(@Header("Authorization") String bearerToken, @Query("status") String status);

    @GET("complaints/detail.php")
    Call<JsonObject> getComplaintDetail(@Header("Authorization") String bearerToken, @Query("id") long id);

    @Multipart
    @POST("complaints/create.php")
    Call<JsonObject> submitComplaint(
            @Header("Authorization") String bearerToken,
            @PartMap Map<String, okhttp3.RequestBody> fields,
            @Part List<okhttp3.MultipartBody.Part> files);

    @GET("reference/districts.php")
    Call<JsonObject> getDistricts();

    @GET("notifications/list.php")
    Call<JsonObject> getNotifications(@Header("Authorization") String bearerToken);

    @POST("notifications/mark_read.php")
    Call<JsonObject> markNotificationRead(@Header("Authorization") String bearerToken, @Body Map<String, Object> body);

    @GET("learn/articles.php")
    Call<JsonObject> getArticles();

    @GET("faqs/list.php")
    Call<JsonObject> getFaqs();

    @GET("reference/about_pca.php")
    Call<JsonObject> getAboutPcaSections();

    // ---- Staff (admin / investigator) case management ----

    @GET("staff/overview.php")
    Call<JsonObject> staffOverview(@Header("Authorization") String bearerToken);

    @GET("staff/complaints.php")
    Call<JsonObject> staffComplaints(@Header("Authorization") String bearerToken,
                                     @QueryMap Map<String, String> filters);

    @GET("staff/detail.php")
    Call<JsonObject> staffDetail(@Header("Authorization") String bearerToken, @Query("id") long id);

    @POST("staff/update.php")
    Call<JsonObject> staffUpdate(@Header("Authorization") String bearerToken,
                                 @Body Map<String, Object> body);

    @POST("staff/priority.php")
    Call<JsonObject> staffPriority(@Header("Authorization") String bearerToken,
                                   @Body Map<String, Object> body);

    @POST("staff/assign.php")
    Call<JsonObject> staffAssign(@Header("Authorization") String bearerToken,
                                 @Body Map<String, Object> body);

    @retrofit2.http.Streaming
    @GET("staff/evidence.php")
    Call<okhttp3.ResponseBody> staffEvidence(@Header("Authorization") String bearerToken,
                                             @Query("id") long evidenceId);

    @Multipart
    @POST("complaints/evidence.php")
    Call<JsonObject> addComplaintEvidence(
            @Header("Authorization") String bearerToken,
            @Query("id") long complaintId,
            @Part List<okhttp3.MultipartBody.Part> files);
}
