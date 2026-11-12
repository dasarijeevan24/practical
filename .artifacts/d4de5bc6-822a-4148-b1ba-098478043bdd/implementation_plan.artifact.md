# Attendance App Implementation Plan

Build an attendance app for students that verifies physical presence within a 100m radius of a examiner-specified center point, requires Firebase Authentication, uses Google Code Scanner for QR verification, checks location permissions, reverse geocodes the address, blocks duplicate daily entries, displays attendance history in a RecyclerView, and styles the status card dynamically (green when inside, red when outside) using theme color attributes.

## User Review Required

> [!IMPORTANT]
> **Firebase Configuration**: Ensure `google-services.json` is added to the `app/` directory if connecting to a live Firebase project, or the app will use Firebase Auth in test/offline-compatible modes where possible.
> **Center Location**: The default campus center point will be configured (e.g. sample coordinates with 100m radius), which can be adjusted or tested.

## Proposed Changes

### Dependencies & Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/build.gradle.kts)
- Add Firebase BOM and Firebase Auth dependencies.
- Add Google Code Scanner (`com.google.android.gms:play-services-code-scanner`).
- Add Play Services Location (`com.google.android.gms:play-services-location`).

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/AndroidManifest.xml)
- Add runtime location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`).
- Add Internet permission for Firebase Auth and Geocoder.

### Theming & Layouts
#### [MODIFY] [themes.xml](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/res/values/themes.xml) and `values-night/themes.xml`
- Define success/error color attributes or colors for Inside (Green) and Outside (Red) status cards.

#### [NEW] [activity_auth.xml](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/res/layout/activity_auth.xml)
- Login / Sign up screen using Firebase Auth (Email & Password).

#### [NEW] [activity_main.xml](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/res/layout/activity_main.xml)
- Attendance dashboard layout:
  - Status Card (Inside/Outside, distance in meters, address).
  - "Scan QR & Mark Attendance" button (Google Code Scanner).
  - Attendance History RecyclerView (newest first).
  - User info / Sign out.

#### [NEW] [item_attendance.xml](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/res/layout/item_attendance.xml)
- RecyclerView item layout for attendance history records.

### Logic & Activities
#### [NEW] [AuthActivity.kt](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/java/com/example/practical/AuthActivity.kt)
- Handles Firebase authentication (login & registration).

#### [NEW] [AttendanceRecord.kt](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/java/com/example/practical/AttendanceRecord.kt)
- Data class for attendance history (id, timestamp, date, address, distance, status, userEmail).

#### [NEW] [AttendanceAdapter.kt](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/java/com/example/practical/AttendanceAdapter.kt)
- RecyclerView Adapter for displaying attendance history (newest first).

#### [NEW] [MainActivity.kt](file:///C:/Users/jeeva/AndroidStudioProjects/practical/app/src/main/java/com/example/practical/MainActivity.kt)
- Manages location permission requests.
- Fetches current GPS location via `FusedLocationProviderClient`.
- Computes distance to campus center point (100m radius fence).
- Triggers Google Code Scanner (`GmsBarcodeScanning.getClient(context).startScan()`).
- Performs reverse geocoding via `Geocoder` to get the address.
- Blocks second entry on the same day.
- Saves and displays attendance history in RecyclerView.
- Updates status card color (Green when inside, Red when outside) using theme color attributes.

## Verification Plan

### Automated Tests
- Build verification using `gradle_build("app:assembleDebug")`.

### Manual Verification
- Verify login flow with Firebase Auth.
- Verify runtime location permission request and denial handling.
- Verify distance calculation and Inside/Outside status card update (Green/Red).
- Verify Google Code Scanner invocation (`GmsBarcodeScanning.getClient(context).startScan()`).
- Verify address resolution via Geocoder and duplicate check for the same day.
- Verify attendance history RecyclerView display (newest first).
