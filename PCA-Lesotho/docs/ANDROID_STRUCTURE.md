# Android app structure and navigation

Open the `android` directory in Android Studio. The app module is `app/`;
application Java source lives under
`app/src/main/java/com/pca/lesotho/citizen/`.

## Package map

Packages are grouped by responsibility, with screen code grouped by feature:

```text
com.pca.lesotho.citizen/
├── adapters/       RecyclerView adapters
├── data/           SQLite helpers and complaint draft/evidence data
├── model/          API/domain models
├── network/        Retrofit API, session management, push messaging
└── ui/
    ├── splash/         SplashActivity
    ├── onboarding/     Language selection and onboarding screens
    ├── auth/           Login and registration screens
    ├── home/           MainActivity and HomeFragment
    ├── complaints/     ComplaintsFragment and ComplaintDetailActivity
    ├── learn/          LearnFragment
    ├── notifications/  NotificationsFragment
    ├── profile/        ProfileFragment
    ├── staff/         StaffDashboardActivity + StaffCaseActivity (native admin screens)
    └── report/         Complaint form, its step fragments, and AI assistance
```

Each feature's Activity and Fragment stay together in the same package. Shared
adapters, data, models, and networking code live outside `ui/`. XML screens are
in `app/src/main/res/layout/`; the Android manifest is at
`app/src/main/AndroidManifest.xml`.

## Screen and navigation map

```text
SplashActivity
  ├── signed in + onboarding complete ──> MainActivity
  └── otherwise ──> LanguageSelectActivity
                      └──> OnboardingActivity
                            └──> LoginActivity
                                  ├──> RegisterActivity
                                  └──> MainActivity
                                        ├── HomeFragment
                                        ├── ComplaintsFragment
                                        ├── LearnFragment
                                        ├── NotificationsFragment
                                        └── ProfileFragment
                                              └── StaffDashboardActivity ──> StaffCaseActivity (admin or investigator only)

HomeFragment ──> ReportComplaintActivity
  └── prominent Admin / staff dashboard shortcut (staff login required)
  └── one scrollable bilingual complaint form
      ├── autosaved on this device with resume-or-start-new prompt
      ├── camera/gallery ID photo and multiple evidence files
      └── successful multipart submission ──> confirmation with GCR number

ComplaintsFragment / NotificationsFragment
  └── select a complaint ──> ComplaintDetailActivity
```

`MainActivity` switches the five bottom-navigation Fragments in its container.
The other arrows are Activity launches using explicit `Intent`s in the source
screen's click handler or flow callback. For example, the complaint card's
Intent is in `adapters/ComplaintAdapter.java`; the destination screen is
`ui/complaints/ComplaintDetailActivity.java`. System actions, such as opening an
audio recorder, use an implicit Intent in `ui/report/EvidenceStepFragment.java`.

To find a navigation action, open the source feature package and search for
`new Intent` or `startActivity`. To see which Activities Android can launch,
check the `<activity>` entries in `app/src/main/AndroidManifest.xml`.

The complaint form saves in-progress answers locally as the user types and when
the app is backgrounded; attachments use retained document permissions where
the provider supports them. The saved draft remains on the device until submitted
or replaced with a new complaint. Device notifications use generic previews and
open the associated complaint when a valid complaint ID is included in the FCM
data payload. Configure Firebase for the app and apply migration 007 for device
token registration. For server push delivery, configure the PHP FCM service-account
credentials and apply the setup in the database README. Apply migration 006 for the
staff case-priority feature and migration 008 for email action tokens. The in-app
notifications inbox remains available independently.
