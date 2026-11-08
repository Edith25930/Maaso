<p align="center">
  <img src="docs/cover.jpg" alt="Police Complaints Authority – Lesotho" width="360">
</p>

# PCA Lesotho – Citizen & Admin App

Android app and PHP/MySQL backend for the **Police Complaints Authority (PCA), Lesotho**.

* **Citizens** register (or continue as guest), file a complaint with evidence, track it with a
  GCR number, and read PCA information. English and Sesotho.
* **Admins and investigators** use the *same app* to manage cases: dashboard figures, search and
  filters, full case details, evidence viewing, progress updates, priority, expected completion
  date, and (admins only) assigning investigators. No laptop or browser dashboard is needed.

```
android/    Android Studio project (Java, XML)
backend/    PHP REST API (upload to your web host)
database/   MySQL schema + migrations
docs/       Deployment guide, Android structure notes, cover image
```

## 1. Run the Android app

1. Open the **`android`** folder in Android Studio (Hedgehog or newer) and let Gradle sync.
2. Open `android/gradle.properties` and set your backend address (HTTPS, trailing slash):
   ```
   PCA_API_BASE_URL=https://your-domain.example/
   ```
3. Press **Run**, or build an APK from a terminal:
   ```
   cd android
   ./gradlew assembleDebug
   ```
   The APK is written to `android/app/build/outputs/apk/debug/`.

You can also pass the address on the command line instead:
`./gradlew assembleDebug -PPCA_API_BASE_URL=https://your-domain.example/`

## 2. Set up the backend

The app needs the backend online with HTTPS. Follow **[docs/DEPLOY_BACKEND.md](docs/DEPLOY_BACKEND.md)**
(any PHP 8.1+ / MySQL host works).

## 3. Admin and investigator accounts

1. Register the person in the app like any other user.
2. In phpMyAdmin (your host's control panel, usable from a phone browser), run:
   ```sql
   UPDATE users SET role = 'admin'        WHERE phone_number = '+266XXXXXXXX';
   UPDATE users SET role = 'investigator' WHERE phone_number = '+266YYYYYYYY';
   ```
3. Sign in again. An **Admin / staff dashboard** button now appears on Home and Profile.

| | Admin | Investigator |
|---|---|---|
| See cases | all submitted cases | only cases assigned to them |
| Post updates, set priority and due date | yes | yes (own cases) |
| Open evidence files | yes | yes (own cases) |
| Assign investigators | yes | no |

Permissions are enforced by the server, not just hidden in the app.

## Notes

* The staff screens are English-only; citizen screens are English and Sesotho.
* Push notifications are optional: add your own `android/app/google-services.json` from Firebase.
* Never commit `backend/config/*.local.php`, `google-services.json`, or anything in
  `backend/storage/evidence/`.
