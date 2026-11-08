# Fixing the oversized GitHub repository

The APK itself is only about 9.7 MB. The repository was about 70 MB because the Gradle
`build/` folders (65 MB), `.gradle/` and `.idea/` were being uploaded with it.

## 1. If the repo already exists on GitHub (run inside your local project folder)
```
git rm -r --cached android/app/build android/.gradle android/.idea android/local.properties
git add .gitignore
git commit -m "Stop tracking build output and local files"
git push
```
Old commits still contain those files, so the repo stays large. To shrink it, either
(a) create a new empty repository and push this cleaned folder as the first commit, or
(b) rewrite history: `pip install git-filter-repo`, then
`git filter-repo --path android/app/build --path android/.gradle --path android/.idea --invert-paths`
and `git push --force`.

## 2. Publish the APK as a Release, not as a file in the repo
GitHub page -> Releases -> Draft a new release -> attach `app-debug.apk` (or a release APK).
Release files can be up to 2 GB, and the repo stays small.

## 3. Before pushing, check for secrets
`google-services.json`, `*.local.php` and keystores are now ignored. If any of them were
ever pushed, treat the keys in them as exposed and replace them.
