# Deploying the backend

Works on any host that offers **PHP 8.1+**, **MySQL/MariaDB**, **HTTPS**, and the PHP extensions
`pdo_mysql`, `curl`, `fileinfo`. You do not need XAMPP or a laptop: a web host's control panel
(file manager + phpMyAdmin) is enough.

## 1. Database
1. Create an empty MySQL database and a user for it in your host's control panel.
2. In phpMyAdmin, import `database/pca_mobile_schema.sql` (this already includes every change
   made by the migrations in `database/migrations/`, so a fresh install does **not** need them).
   Only if you are upgrading an older database, run the migrations `001` to `009` in order.

## 2. Upload the backend
Upload the **contents** of the `backend/` folder to the web root of your site (so that
`https://your-domain/auth/login.php` exists). Keep the supplied `.htaccess` files.
`backend/vendor/` (the email library) is included, so Composer is not needed.

## 3. Database credentials
Copy `config/database.local.php.example` to `config/database.local.php` and fill in the
host, database name, user and password from the control panel. (Or set the environment variables
`PCA_DB_HOST`, `PCA_DB_NAME`, `PCA_DB_USER`, `PCA_DB_PASS`.)

## 4. Uploads
Evidence files are limited to 200 MB each by the API. Your host's PHP settings
(`upload_max_filesize`, `post_max_size`) and request limits must allow what you expect; many hosts
set lower limits. Evidence is stored in `storage/evidence/`, which must be writable and must never
be browsable (`storage/.htaccess` denies access on Apache; add an equivalent rule on nginx).
For production, point `PCA_EVIDENCE_DIR` at a folder outside the web root.

## 5. Optional services
Copy the matching `config/*.local.php.example` to `*.local.php` (or use environment variables):

| Feature | File | Needs |
|---|---|---|
| Verification / reset / status emails | `email.local.php` | SMTP account, `PCA_PUBLIC_BASE_URL` |
| AI complaint assistant | `ai.local.php` | Gemini API key (never put it in the app) |
| SMS with the GCR number | `sms.local.php` | Africa's Talking account |
| Push notifications | `push.local.php` | Firebase service-account JSON stored outside the web root |

The host must allow outbound HTTPS requests from PHP for the AI, SMS and push features.

## 6. Check it works
* Open `https://your-domain/complaints/categories.php`: you should see JSON.
* Register a test account in the app, then follow the admin steps in the main README.
* Use only test data until PCA has approved the host's privacy, backup and access arrangements.

## 7. Build the app for this backend
Set `PCA_API_BASE_URL` in `android/gradle.properties` to `https://your-domain/` (the web root, with a
trailing slash), then build as described in the main README.
