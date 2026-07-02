# Báo Cáo Dự Án TrekMate MVP

> Cập nhật lần cuối: 02/07/2026  
> Repository: https://github.com/Psasvirgo139/EXETrekMate  
> Nhánh: `main`

---

## 1. Tổng Quan

TrekMate là ứng dụng Android Native (Kotlin) dạng Proof of Concept, phát hiện thành viên lạc đoàn bằng **Bluetooth Low Energy (BLE)** giữa các điện thoại cá nhân. Không dùng GPS, WiFi positioning, hay thiết bị BLE ngoài.

---

## 2. Những Gì Đã Được Triển Khai

### 2.1 Cấu Trúc Dự Án

```
EXETREKMATE/
  app/
    src/main/java/com/trekmate/app/
      core/
        model/         ← Domain models (CurrentUser, CurrentTour, TourMember, BleObservation, MemberPresence)
        storage/       ← Room entities, DAOs, AppDatabase, DataStore
        network/       ← Retrofit API service, DTOs
        ble/           ← BLE packet encoder/decoder (không phụ thuộc Android API)
        time/          ← ClockProvider (injectable cho test)
      feature/
        auth/          ← AuthRepository, UserIdGenerator, AuthState
        tour/          ← TourRepository, TourViewModel, ApiSyncRepository
        qr/            ← QrPayloadParser, QrCodeRenderer (ZXing), QrScannerViewModel
        tracking/      ← PresenceRepository, LostDetectionEngine, TrackingViewModel
        notification/  ← TrekMateNotificationManager (2 kênh thông báo)
      service/
        TrekMateBleService.kt    ← Foreground Service chính
        BleAdvertiserController.kt
        BleScannerController.kt
      ui/
        screens/       ← 7 màn hình Compose
        Navigation.kt
        MainActivity.kt
        MainViewModel.kt
      di/              ← 9 Hilt module
```

---

### 2.2 Danh Sách Module Đã Hoàn Thành

| # | Module | File chính | Trạng thái |
|---|--------|-----------|------------|
| 01 | **Authentication** | `AuthRepositoryImpl.kt`, `UserIdGenerator.kt` | ✅ Hoàn thành |
| 02 | **Tour Lifecycle** | `TourRepositoryImpl.kt`, `TourViewModel.kt` | ✅ Hoàn thành |
| 03 | **QR Code** | `QrPayloadParser.kt`, `QrCodeRenderer.kt`, `QrScanScreen.kt` | ✅ Hoàn thành |
| 04 | **Local Storage** | `AppDatabase.kt`, `UserPreferencesDataStore.kt`, 3 DAOs | ✅ Hoàn thành |
| 05 | **BLE Packet** | `BlePacketEncoder.kt`, `BlePacketDecoder.kt` | ✅ Hoàn thành |
| 06 | **BLE Advertising** | `BleAdvertiserController.kt` | ✅ Hoàn thành |
| 07 | **BLE Scanning** | `BleScannerController.kt` | ✅ Hoàn thành |
| 08 | **RSSI/Presence** | `PresenceRepositoryImpl.kt`, `BleObservationStoreImpl.kt` | ✅ Hoàn thành |
| 09 | **Lost Detection** | `LostDetectionEngineImpl.kt` | ✅ Hoàn thành |
| 10 | **Background Service** | `TrekMateBleService.kt` | ✅ Hoàn thành |
| 11 | **Notification** | `TrekMateNotificationManagerImpl.kt` | ✅ Hoàn thành |
| 12 | **UI** | 7 màn hình Compose + Navigation | ✅ Hoàn thành |
| 13 | **API Sync** | `TourApiService.kt`, `ApiSyncRepositoryImpl.kt` | ✅ Hoàn thành |

---

### 2.3 Chức Năng Chi Tiết

#### Authentication
- Tự động sinh `userId` (16 ký tự hex compact) khi cài app lần đầu
- Lưu vào DataStore, giữ nguyên sau khi tắt/bật lại app
- Không cần login/password

#### Tour Lifecycle
- **Leader tạo tour**: gọi API `POST /tours` → nhận `tourId`, `groupId`, `joinCode`, `qrPayload`
- **Member tham gia**: nhập join code (`POST /tours/join`) hoặc scan QR
- **Kết thúc tour**: Leader gọi `POST /tours/end` → xóa tour/member/BLE data local
- Lưu toàn bộ state vào Room Database

#### QR Code
- QR payload format: `trekmate://join?code=JOINCODE`
- Leader: hiển thị QR bitmap (ZXing) trên màn hình
- Member: scan QR bằng camera (CameraX + ZXing)

#### BLE Packet
- **Payload 16 bytes**: 8 bytes `userId` + 8 bytes `groupId`
- Manufacturer ID: `0x4D54` ("TM" - TrekMate)
- Đặt trong `AdvertiseData.addManufacturerData()`
- Encode/decode không phụ thuộc Android BLE API → test được offline

#### BLE Advertising & Scanning
- Tự động bắt đầu khi có `groupId` trong local storage
- **Scanner**: lọc theo manufacturer ID `0x4D54`, bỏ qua gói khác group, bỏ qua gói của chính mình
- **Advertiser**: low-latency mode, high TX power
- Chạy trong Foreground Service

#### Lost Detection (60 giây)
- **Member**: cảnh báo nếu không nhận BLE từ Leader trong 60 giây
- **Leader**: đánh dấu Member là "lost" nếu không nhận BLE trong 60 giây
- Evaluate mỗi 10 giây trong service
- Không trigger khi `lastSeenAt == null` (chưa bắt đầu theo dõi)
- Xóa cảnh báo khi thấy lại

#### Notification
- **Kênh "Tracking"**: thông báo persistent khi service đang chạy
- **Kênh "Lost Alert"**: cảnh báo khi có thành viên lạc đoàn
- Dedup: không spam notification nếu state không đổi

#### UI (Jetpack Compose)
- `HomeScreen`: hiển thị userId, nút Create / Join by Code / Join by QR
- `CreateTourScreen`: tạo tour, hiển thị join code và QR bitmap
- `JoinTourScreen`: nhập join code
- `QrScanScreen`: camera preview + ZXing decode
- `LeaderDashboardScreen`: danh sách member, RSSI, last seen, lost warning
- `MemberTrackingScreen`: trạng thái kết nối với leader, danh sách member
- `PermissionScreen`: hướng dẫn cấp quyền BLE/camera/notification

#### Tech Stack
- Kotlin + Android Native
- Jetpack Compose (UI)
- Hilt (DI)
- Room + DataStore (Storage)
- Retrofit + OkHttp + Gson (Network)
- ZXing + CameraX (QR)
- Android BLE APIs (BluetoothLeAdvertiser, BluetoothLeScanner)
- Foreground Service
- Kotlin Coroutines + Flow

---

### 2.4 Unit Tests Đã Có

| File test | Covers |
|-----------|--------|
| `AuthRepositoryTest.kt` | Tạo mới user, đọc user đã có, userId không blank |
| `TourRepositoryTest.kt` | Create/join/end tour, lỗi API không lưu partial state |
| `QrPayloadParserTest.kt` | Parse valid/invalid QR, build payload |
| `BlePacketTest.kt` | Encode/decode round-trip, empty/truncated/random bytes, blank id |
| `LostDetectionEngineTest.kt` | 59s không cảnh báo, 60s cảnh báo, clear sau khi thấy lại, leader exclude self, empty tour |

---

## 3. Những Gì Cần Làm Tiếp Theo

### 3.1 Bắt Buộc Trước Khi Chạy

#### ✏️ Thay đổi BASE_URL (quan trọng nhất)

File: `app/build.gradle.kts`

```kotlin
defaultConfig {
    // Thay URL này bằng URL backend thực của bạn (release)
    buildConfigField("String", "BASE_URL", "\"https://api.trekmate.dev/\"")
}

buildTypes {
    debug {
        // Khi test với emulator: 10.0.2.2 trỏ về localhost của máy tính
        // Khi test với điện thoại thật: thay bằng IP LAN của máy chạy backend
        buildConfigField("String", "BASE_URL", "\"http://192.168.1.100:8080/\"")
    }
}
```

> **Lưu ý**: Nếu dùng HTTP (không HTTPS) cần thêm Network Security Config:
> ```xml
> <!-- app/src/main/res/xml/network_security_config.xml -->
> <network-security-config>
>     <domain-config cleartextTrafficPermitted="true">
>         <domain includeSubdomains="true">192.168.1.100</domain>
>     </domain-config>
> </network-security-config>
> ```
> Rồi khai báo trong `AndroidManifest.xml`:
> ```xml
> android:networkSecurityConfig="@xml/network_security_config"
> ```

---

#### ✏️ Backend API phải có các endpoints sau

App Android đang gọi các endpoint này (xem `TourApiService.kt`):

| Method | Path | Request body | Response |
|--------|------|-------------|----------|
| `POST` | `/tours` | `{ "leader_id": "..." }` | `{ "tour_id", "group_id", "join_code", "qr_payload", "leader_id" }` |
| `POST` | `/tours/join` | `{ "user_id": "...", "join_code": "..." }` | `{ "tour_id", "group_id", "leader_id", "join_code", "qr_payload", "members": [...] }` |
| `POST` | `/tours/end` | `{ "tour_id": "...", "leader_id": "..." }` | `{ "success": true }` |
| `GET` | `/tours/{tourId}/members` | — | `{ "members": [{ "user_id", "is_leader" }] }` |

> Nếu chưa có backend, có thể dùng **mock server** (ví dụ: MockWebServer, Postman Mock, json-server) để test luồng tour.

---

#### ✏️ App Icon

Hiện app dùng icon placeholder `@mipmap/ic_launcher`. Cần thêm:
- `app/src/main/res/mipmap-*/ic_launcher.png` (các kích thước: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- `app/src/main/res/mipmap-*/ic_launcher_round.png`

Hoặc dùng Android Studio → Image Asset để tự sinh.

---

### 3.2 Tích Hợp Bản Đồ (Map)

#### Lựa chọn A: Offline Map với file GeoPackage (.gpkg)

GeoPackage là định dạng SQLite chứa dữ liệu bản đồ vector/raster offline. Phù hợp khi nhóm muốn bản đồ chạy không cần internet.

**Thư viện đề xuất**: [OSMDroid](https://github.com/osmdroid/osmdroid) hoặc [MapLibre Android](https://github.com/maplibre/maplibre-gl-native)

**Các bước tích hợp GeoPackage với OSMDroid:**

1. Thêm dependency vào `app/build.gradle.kts`:
```kotlin
implementation("org.osmdroid:osmdroid-android:6.1.18")
implementation("mil.nga.geopackage:geopackage-android:6.6.0")
```

2. Đặt file `.gpkg` vào `assets/` hoặc download về bộ nhớ thiết bị

3. Tạo `MapScreen.kt`:
```kotlin
// Khởi tạo OSMDroid với GeoPackage tile provider
val geoPackage = GeoPackageManager.open(context, "maps/area.gpkg")
val tileProvider = GeoPackageTileProvider(geoPackage, "tiles_table")
mapView.tileProvider = tileProvider
```

4. Quyền cần thêm vào `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

**Tính năng bản đồ cần triển khai:**
- Hiển thị vị trí của leader/member (fake location từ BLE RSSI, không dùng GPS)
- Vẽ marker cho từng thành viên
- Highlight thành viên bị đánh dấu lost

---

#### Lựa chọn B: Online Map với Google Maps / Mapbox

Nếu không cần offline:
```kotlin
// build.gradle.kts
implementation("com.google.maps.android:maps-compose:4.3.3")
implementation("com.google.android.gms:play-services-maps:18.2.0")
```
Cần đăng ký Google Maps API key và thêm vào `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY" />
```

---

### 3.3 Database URL (Nếu Dùng Database Từ Xa)

Hiện app chỉ dùng **Room (SQLite local)**. Nếu muốn sync data lên server database:

#### Cách 1: Qua REST API (đã có sẵn)
Backend tự quản lý database, app gọi qua các endpoint đã định nghĩa. Không cần thêm gì trên Android.

#### Cách 2: Firebase Realtime Database / Firestore
Thêm dependency:
```kotlin
// build.gradle.kts
implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
implementation("com.google.firebase:firebase-database-ktx")
// hoặc Firestore:
implementation("com.google.firebase:firebase-firestore-ktx")
```

Thêm `google-services.json` vào `app/` (tải từ Firebase Console).

Thêm plugin vào `build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services")
}
```

#### Cách 3: Supabase (PostgreSQL qua REST/Realtime)
```kotlin
implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.2")
implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.2")
```
Cần cấu hình URL và anon key:
```kotlin
// Thêm vào BuildConfig hoặc local.properties
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

### 3.4 Cấu Hình local.properties (Không Commit Lên Git)

Tạo file `local.properties` (đã gitignore) để lưu các giá trị nhạy cảm:

```properties
# Android SDK path (tự sinh bởi Android Studio)
sdk.dir=C\:\\Users\\NgocKhoi\\AppData\\Local\\Android\\Sdk

# Backend API
BASE_URL_DEBUG=http://192.168.1.100:8080/
BASE_URL_RELEASE=https://api.yourdomain.com/

# Google Maps (nếu dùng)
GOOGLE_MAPS_KEY=AIzaSy...

# Firebase (nếu dùng - thay bằng google-services.json)
# FIREBASE_URL=https://your-project.firebaseio.com/

# Supabase (nếu dùng)
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...
```

Rồi đọc vào `build.gradle.kts`:
```kotlin
import java.util.Properties
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

buildConfigField("String", "BASE_URL", "\"${localProps["BASE_URL_DEBUG"] ?: "http://10.0.2.2:8080/"}\"")
```

---

### 3.5 Những Việc Còn Lại Để Hoàn Thiện MVP

| Việc cần làm | Mức ưu tiên | Mô tả |
|-------------|------------|-------|
| **Cấu hình BASE_URL** | 🔴 Cao | App không gọi API được nếu chưa đúng URL |
| **Triển khai Backend** | 🔴 Cao | Cần 4 endpoints tour (xem bảng ở 3.1) |
| **Tải gradle-wrapper.jar** | 🔴 Cao | File nhị phân không commit lên git, Android Studio tự tải khi Sync Gradle |
| **Thêm App Icon** | 🟡 Trung bình | Thay thế icon placeholder |
| **Test BLE trên thiết bị thật** | 🔴 Cao | BLE không chạy trên emulator, cần ít nhất 2-3 điện thoại Android |
| **Network Security Config** | 🟡 Trung bình | Nếu backend dùng HTTP (không HTTPS) |
| **Tích hợp Map** | 🟢 Thấp | Có thể làm sau khi BLE hoạt động ổn |
| **Xử lý Bluetooth tắt** | 🟡 Trung bình | Hiện app báo lỗi nhưng chưa có nút "Bật Bluetooth" |
| **Sync member khi app restart** | 🟡 Trung bình | Gọi `syncMembers()` khi app khởi động lại với tour đang active |
| **Thêm displayName cho User** | 🟢 Thấp | Hiện userId là hex 16 ký tự, khó nhận ra người |
| **Test unit tests** | 🟡 Trung bình | Chạy `./gradlew test` để verify 5 test suites |

---

### 3.6 Checklist Để Chạy Được Trên Thiết Bị Thật

```
[ ] 1. Mở project trong Android Studio (File → Open → chọn thư mục EXETREKMATE)
[ ] 2. Sync Gradle (sẽ tự tải dependencies và gradle-wrapper.jar)
[ ] 3. Sửa BASE_URL trong app/build.gradle.kts trỏ đến backend
[ ] 4. Nếu dùng HTTP: thêm network_security_config.xml
[ ] 5. Chạy backend server với 4 endpoints
[ ] 6. Build và cài app lên ít nhất 2 điện thoại Android thật (API 26+)
[ ] 7. Cấp đầy đủ permissions: Bluetooth, Location (Android 11 trở xuống), Notification
[ ] 8. Phone 1: Create Tour → chú ý join code
[ ] 9. Phone 2: Join Tour bằng join code hoặc scan QR từ Phone 1
[ ] 10. Hai điện thoại cùng ở màn hình tracking → thấy nhau qua BLE
[ ] 11. Di chuyển Phone 1 xa ra → sau 60s Phone 2 cảnh báo "possible lost"
```

---

### 3.7 Kiến Trúc Mở Rộng Sau MVP (Tham Khảo)

| Tính năng | Ghi chú |
|-----------|---------|
| **GPS tracking** | Tích hợp FusedLocationProvider, lưu track route |
| **Offline map (.gpkg)** | OSMDroid + GeoPackage, file tải trước |
| **Push notification** | FCM để server notify member khi tour kết thúc |
| **Leader broadcast member status** | BLE packet mở rộng chứa danh sách status (cần test payload limit) |
| **RSSI distance estimation** | Path-loss model đơn giản, hiển thị khoảng cách ước tính |
| **Tour history** | Lưu lịch sử tour vào Room hoặc server |
| **Group chat** | Chỉ nên dùng Internet (không qua BLE) |
| **Android Wear support** | Hiển thị lost alert trên đồng hồ |
| **Kalman filter RSSI** | Giảm nhiễu RSSI cho estimation chính xác hơn |

---

## 4. Cấu Trúc Commit Trên GitHub

```
70d7ef0  readme (trước khi bắt đầu)
3a73b6a  chore: init Android project skeleton
40fcc23  feat(auth+storage): Module 01 + 04
e913b5c  feat(api-sync): Module 13
0c06112  feat(tour): Module 02
66a3d53  feat(qr): Module 03
d3ef7d5  feat(ble-packet): Module 05
e80cd97  feat(ble-scan+adv): Module 07 + 06
7c78241  feat(presence+lost): Module 08 + 09
af3d235  feat(service+notification): Module 10 + 11
76ac9c5  feat(ui): Module 12
a48aa77  fix: cleanup imports và logic fixes
```

---

## 5. Thống Kê

- **Tổng số file Kotlin**: 68 file
- **Tổng số test**: 5 test suites, 22+ test cases
- **Dependency chính**: Gradle 8.9, AGP 8.5.2, Kotlin 2.0, Compose BOM 2024.09
- **minSdk**: 26 (Android 8.0)
- **targetSdk**: 35 (Android 15)
