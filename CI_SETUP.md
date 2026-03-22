# CI / Release Guide — SceneFind

## How the GitHub Actions workflow works

The file `.github/workflows/release.yml` contains two jobs:

| Job | Trigger | What it does |
|---|---|---|
| `build` | Push of any `v*` tag | Builds a **signed release APK**, creates a GitHub Release, attaches the APK |
| `build-debug` | Push to any branch / PR | Builds a **debug APK** as a quick sanity check, uploads it as a workflow artifact |

---

## One-time setup

### Step 1 — Generate a release keystore

Run the helper script from the project root:

```bash
chmod +x generate-keystore.sh
./generate-keystore.sh
```

It will:
- Create `release.jks` in the project root
- Create `app/keystore.properties` for local builds
- Write `keystore_base64.txt` containing the base64-encoded keystore
- Print all the values you need for GitHub Secrets

> ⚠️ **Back up `release.jks` securely.** You cannot update the app on the Play Store
> (or re-sign future versions) without the exact same keystore.
> **Never commit it to git** — it is in `.gitignore`.

---

### Step 2 — Add GitHub Secrets

Go to your repository → **Settings → Secrets and variables → Actions → New repository secret**

Add these four secrets:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Full contents of `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | The store password you entered |
| `KEY_ALIAS` | `sfm-release` (default) |
| `KEY_PASSWORD` | The key password you entered |

---

### Step 3 — Push the repo to GitHub

```bash
git init
git remote add origin https://github.com/YOUR_USERNAME/SceneFind.git
git add .
git commit -m "Initial commit"
git push -u origin main
```

---

## Releasing a new version

```bash
# 1. Make your changes, commit them
git add .
git commit -m "feat: my new feature"

# 2. Push the tag — this triggers the release workflow
git tag v1.0.0
git push origin v1.0.0
```

The workflow will:
1. Compile the release APK
2. Sign it with your keystore
3. Stamp the versionName (`1.0.0`) and versionCode (`10000`) automatically
4. Create a GitHub Release named **SceneFind v1.0.0**
5. Attach `SceneFind-v1.0.0.apk` to the release

Versions with a pre-release suffix (e.g. `v1.1.0-beta`) are automatically
marked as pre-releases on GitHub.

---

## Version code calculation

The workflow converts semantic versions to integer version codes:

| Tag | versionName | versionCode |
|---|---|---|
| `v1.0.0` | `1.0.0` | `10000` |
| `v1.2.3` | `1.2.3` | `10203` |
| `v2.0.0` | `2.0.0` | `20000` |
| `v10.5.1` | `10.5.1` | `100501` |

Formula: `major × 10000 + minor × 100 + patch`

---

## Local builds

### Debug (unsigned)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/SceneFind-debug-<version>-debug.apk
```

### Release (signed, requires keystore.properties)
```bash
# First run generate-keystore.sh to create app/keystore.properties
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/SceneFind-release-<version>.apk
```

### Install directly to connected device
```bash
./gradlew installDebug
```

---

## Troubleshooting

**`Keystore file not found`**
→ Make sure `release.jks` exists in the project root and `app/keystore.properties`
  has the correct relative path (`storeFile=../release.jks`).

**`Wrong passwords`**
→ Double-check `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets.
  The `storePassword` and `keyPassword` must match what you used when
  running `generate-keystore.sh`.

**`Gradle sync failed: compileSdkVersion 34 requires JDK 17`**
→ The workflow uses JDK 17. Locally, make sure Android Studio is using JDK 17
  (File → Project Structure → SDK Location → Gradle JDK).

**`No such property: android.injected.signing...`**
→ This means you ran `assembleRelease` locally without `app/keystore.properties`.
  Either run `generate-keystore.sh` or pass the properties manually:
  ```bash
  ./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file=/path/to/release.jks \
    -Pandroid.injected.signing.store.password=... \
    -Pandroid.injected.signing.key.alias=sfm-release \
    -Pandroid.injected.signing.key.password=...
  ```
