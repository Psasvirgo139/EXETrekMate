# TrekMate EXE — Quá trình phát triển & xử lý sự cố

Tài liệu này ghi lại toàn bộ nội dung cuộc trò chuyện phát triển dự án TrekMate Android MVP và backend mock, bao gồm các yêu cầu, quyết định kỹ thuật, lỗi gặp phải và các chỉnh sửa code đã thực hiện.

---

## 1. Bối cảnh ban đầu

- Dự án Android MVP TrekMate được triển khai theo `task.md`, `plan.md` và các module trong `modules/`.
- Repo Android: `EXETREKMATE/` (GitHub, nhánh `main`).
- Backend mock riêng: `Backend/exe-backend/` (quản lý bằng git riêng, không commit qua agent).
- Mục tiêu: app Android native (Kotlin, Compose, Hilt, Room, BLE) kết nối backend mock phục vụ 4 API tour execution.

---

## 2. Các giai đoạn chính

### Giai đoạn 1 — Triển khai Android MVP

**Yêu cầu:** Implement toàn bộ app theo plan/module, commit theo từng module trên nhánh `main`.

**Kết quả:**
- Hoàn thiện các module: Authentication, Tour, BLE, UI.
- Sử dụng Hilt, Room, DataStore, Retrofit, OkHttp, Jetpack Compose.
- Commit và push lên GitHub theo từng module.

---

### Giai đoạn 2 — Tài liệu & cấu hình API

#### 2.1. Tạo `baocao.md`

**Yêu cầu:** Tóm tắt chức năng đã có và việc cần làm tiếp (BASE_URL, map GPK, database URL, v.v.).

**Thay đổi:**
- Tạo file `baocao.md` trong `EXETREKMATE/`.

---

#### 2.2. Thêm tiền tố `/exe` cho mọi API external

**Yêu cầu:** Mọi API kết nối ra ngoài app đều có prefix `/exe`.

**Ví dụ:** `POST /tours` → `POST /exe/tours`

**Thay đổi (Android):**
- Cập nhật `TourApiService.kt` và các DTO/request path tương ứng.
- Cập nhật `baocao.md` ghi nhận convention mới.

---

### Giai đoạn 3 — Backend mock riêng (`exe-backend`)

#### 3.1. Tạo backend độc lập

**Yêu cầu:** Không dùng backend hệ thống có sẵn; tạo backend mock trong `Backend/exe-backend/` kết nối DB `exetrekmate`, chỉ trả về dữ liệu cần thiết cho app.

**Thay đổi (Backend):**
- Tạo Spring Boot 3.3.5, Java 21.
- Cấu trúc: `controller`, `service`, `repository`, `model`, `dto`, `config`.
- Implement 4 endpoint:
  - `POST /exe/tours` — tạo tour
  - `POST /exe/tours/join` — join tour
  - `POST /exe/tours/end` — kết thúc tour
  - `GET /exe/tours/{tourId}/members` — lấy danh sách thành viên
- Ban đầu dùng PostgreSQL `exetrekmate`.

**Lưu ý:** Thư mục `exe-backend` ban đầu nằm trong `EXETREKMATE/` để thuận tiện, sau đó user di chuyển ra `Backend/exe-backend/` và quản lý git riêng.

---

### Giai đoạn 4 — Sửa lỗi build & runtime Android

| Lỗi | Nguyên nhân | Chỉnh sửa |
|-----|-------------|-----------|
| `mipmap/ic_launcher` not found | Thiếu icon launcher | Tạo `ic_launcher.xml`, `ic_launcher_round.xml`, `ic_launcher_background.xml`, `ic_launcher_foreground.xml` |
| `MemberRow` is private | Composable private không access được từ screen khác | Đổi `MemberRow` từ `private` → `internal` trong `LeaderDashboardScreen.kt` |
| Hilt MissingBinding `SystemClockProvider` | Thiếu `@Inject constructor()` | Thêm `@Inject constructor()` vào `SystemClockProvider.kt` |
| Cleartext HTTP blocked (`10.0.2.2`) | Android chặn HTTP cleartext | Tạo `network_security_config.xml`, thêm vào `AndroidManifest.xml` |
| BASE_URL vẫn trỏ `10.0.2.2` sau khi đổi config | Build cache Android Studio | Hướng dẫn Clean + Rebuild Project |

---

### Giai đoạn 5 — Deploy backend lên Render

#### 5.1. Chiến lược deploy

**Thảo luận:**
- Backend trên laptop không dùng được `10.0.2.2` cho điện thoại thật.
- Backend deploy không kết nối được DB local.
- Quyết định: dùng **H2** thay PostgreSQL, deploy lên **Render**.

#### 5.2. Chuyển PostgreSQL → H2

**Thay đổi (Backend):**
- `pom.xml`: thay dependency PostgreSQL bằng H2.
- `application.yml`:
  - Ban đầu: H2 in-memory.
  - Sau: H2 file-based `jdbc:h2:file:/tmp/exetrekmate;AUTO_SERVER=TRUE`, `ddl-auto: update` (tránh mất data khi Render free tier restart).
  - `server.port: ${PORT:8080}` cho Render.

#### 5.3. Docker & deploy

**Thay đổi (Backend):**
- Tạo `Dockerfile` (multi-stage Maven build).
- Tạo `.dockerignore`.

#### 5.4. Cập nhật BASE_URL Android

**Thay đổi (Android):**
- `app/build.gradle.kts`: `BASE_URL` → `https://exetrekmatebe-1.onrender.com/` (debug + defaultConfig).

**Lỗi tiếp theo:** Join tour HTTP 404 sau idle — do H2 in-memory mất data. Đã fix bằng H2 file-based.

---

### Giai đoạn 6 — Real-time updates: Polling → SSE

#### 6.1. Vấn đề ban đầu

- Người join tour thấy đủ danh sách thành viên.
- Người tạo tour chỉ thấy bản thân.
- End tour: người join không bị đẩy về home, bị kẹt trong tour đã kết thúc.

**Yêu cầu user:** Không dùng sync/polling. Dùng cơ chế server push (trigger khi có thay đổi tour).

User đã **revert** cơ chế sync/polling trước đó.

#### 6.2. Triển khai Server-Sent Events (SSE)

**Thay đổi (Backend):**
- `TourEventBroadcaster.java`: quản lý `SseEmitter` theo `tourId`, `broadcastMemberUpdate`, `broadcastTourEnded`, heartbeat 25s.
- `ExeTourController.java`: endpoint `GET /exe/tours/{tourId}/events` (SSE).
- `ExeTourServiceImpl.java`: gọi broadcast sau join/end (sau này chuyển sang controller).
- `ExeBackendApplication.java`: thêm `@EnableScheduling`.
- Header SSE: `X-Accel-Buffering: no`, `Cache-Control`, `Connection: keep-alive`.

**Thay đổi (Android):**
- `TourSseEvent.kt`: sealed class `MemberUpdate`, `TourEnded`, `Disconnected`.
- `TourSseClient.kt`: OkHttp SSE client, auto-reconnect.
- `TourRepositoryImpl.kt`: `startSse()` / `stopSse()`, xử lý events, cập nhật Room.
- `ApplicationScope.kt`, `NetworkModule.kt`: DI cho SSE và application scope.
- `TourViewModel.kt`: bỏ polling, dùng SSE qua repository.

#### 6.3. Lỗi SSE & fix

| Lỗi | Fix |
|-----|-----|
| `isActive` không resolve trong Flow builder | Dùng `currentCoroutineContext().isActive` trong `TourSseClient.kt` |
| `TourRepositoryTest` thiếu `sseClient`, `appScope` | Thêm mock vào test |
| `TourEnded` không clear Room (coroutine bị cancel) | `withContext(NonCancellable) { clearLocalTour() }` trong `TourRepositoryImpl.kt` |
| Stale tour data trong Room | `TourDao`: abstract class + `@Transaction` clear trước insert; `AppDatabase` version 2 |

---

### Giai đoạn 7 — SSE hoạt động một nửa

#### 7.1. Triệu chứng

- **`tour_ended` hoạt động:** Người join được đẩy về home, xóa group id.
- **`member_update` không hoạt động:** Máy người tạo tour không cập nhật danh sách khi có người join.

#### 7.2. Phân tích & fix lần 1

**Giả thuyết:** Render nginx buffer SSE response; `tour_ended` “work” vì `emitter.complete()` buộc flush buffer.

**Thay đổi (Backend):**
- Di chuyển `broadcastMemberUpdate` / `broadcastTourEnded` từ `ExeTourServiceImpl` (trong `@Transactional`) sang **controller** — broadcast sau khi transaction commit.
- SSE endpoint: **register emitter trước**, `getMembers()` sau (tránh miss broadcast).
- `TourEventBroadcaster`: catch mọi exception (không chỉ `IOException`), log INFO với số emitter.

**Thay đổi (Android):**
- `TourSseClient`: rethrow `CancellationException`, thêm logging.
- `TourRepositoryImpl`: logging khi nhận `MemberUpdate`.
- `MemberDao`: chuyển từ `interface` → `abstract class` để `@Transaction replaceAll` hoạt động đáng tin cậy.

#### 7.3. Kết quả sau redeploy — vẫn lỗi

**Log backend xác nhận backend gửi đúng:**
```
SSE registered: tourId=... total=1          ← leader đã subscribe
Tour joined: tourId=... userId=...
SSE broadcast 'member_update' → tourId=... (1 emitter(s))   ← đã gửi tới leader
SSE registered: tourId=... total=2          ← member subscribe sau
```

**Kết luận:** Backend gửi event thành công. Vấn đề nằm ở **nginx/Cloudflare trên Render buffer SSE** — event không tới Android real-time (chỉ flush khi connection đóng, tức lúc `tour_ended`).

---

### Giai đoạn 8 — Chuyển SSE → WebSocket

#### 8.1. Quyết định

WebSocket không bị nginx buffer như SSE; Render hỗ trợ WebSocket natively. Đây là fix dứt điểm cho `member_update` real-time.

#### 8.2. Thay đổi Backend

| File | Tóm tắt |
|------|---------|
| `pom.xml` | Thêm `spring-boot-starter-websocket` |
| `ws/WebSocketConfig.java` | **Mới.** Cấu hình endpoint `/exe/tours/{tourId}/ws` |
| `ws/TourWebSocketHandler.java` | **Mới.** Quản lý WebSocket session theo tourId; `broadcastMemberUpdate`, `broadcastTourEnded`, heartbeat 25s |
| `ExeTourController.java` | Inject `TourWebSocketHandler`; sau join/end broadcast qua cả SSE (backward compat) và WebSocket |
| `TourEventBroadcaster.java` | Giữ SSE, nâng logging |

**Format message WebSocket:**
```json
{"type":"member_update","members":[{"user_id":"...","is_leader":true}]}
{"type":"tour_ended"}
{"type":"heartbeat"}
```

**URL:** `wss://exetrekmatebe-1.onrender.com/exe/tours/{tourId}/ws`

#### 8.3. Thay đổi Android

| File | Tóm tắt |
|------|---------|
| `core/network/ws/TourWebSocketClient.kt` | **Mới.** OkHttp WebSocket client; `eventFlow(tourId)` trả về cùng type `TourSseEvent` |
| `TourRepositoryImpl.kt` | Thay `TourSseClient` → `TourWebSocketClient` (interface Flow giữ nguyên) |
| `NetworkModule.kt` | Provide `TourWebSocketClient` |
| `TourRepositoryTest.kt` | Mock `TourWebSocketClient` thay `TourSseClient` |

**Lưu ý:** `TourSseClient.kt`, `TourSseEvent.kt` vẫn tồn tại; event type dùng chung, chỉ đổi transport layer.

---

## 3. Tổng hợp file đã chỉnh sửa / tạo mới

### Android (`EXETREKMATE/`)

| File | Loại thay đổi |
|------|---------------|
| `app/build.gradle.kts` | BASE_URL → Render |
| `baocao.md` | Tạo/cập nhật tài liệu |
| `res/drawable/ic_launcher_*.xml` | Tạo icon |
| `res/mipmap-anydpi-v26/ic_launcher*.xml` | Adaptive icon |
| `res/xml/network_security_config.xml` | Cho phép cleartext `10.0.2.2` |
| `AndroidManifest.xml` | networkSecurityConfig |
| `LeaderDashboardScreen.kt` | `MemberRow` → internal |
| `SystemClockProvider.kt` | `@Inject constructor()` |
| `TourDao.kt` | Abstract class, transaction clear+insert |
| `MemberDao.kt` | Abstract class, reliable `@Transaction` |
| `AppDatabase.kt` | Version 2 |
| `TourSseEvent.kt` | Sealed class events |
| `TourSseClient.kt` | SSE client (giữ lại, không còn dùng chính) |
| `TourWebSocketClient.kt` | **WebSocket client (transport chính)** |
| `TourRepositoryImpl.kt` | SSE → WebSocket, NonCancellable cleanup |
| `TourViewModel.kt` | Bỏ polling |
| `NetworkModule.kt` | Gson, baseUrl, ApplicationScope, WebSocket |
| `ApplicationScope.kt` | Qualifier cho app-lifetime scope |
| `TourRepositoryTest.kt` | Mock WebSocket client |

### Backend (`Backend/exe-backend/`)

| File | Loại thay đổi |
|------|---------------|
| Toàn bộ module | Tạo backend mock Spring Boot |
| `pom.xml` | H2, WebSocket |
| `application.yml` | H2 file-based, PORT Render |
| `Dockerfile`, `.dockerignore` | Deploy Render |
| `TourEventBroadcaster.java` | SSE registry + broadcast + heartbeat |
| `ExeTourController.java` | REST + SSE + broadcast sau join/end |
| `ExeTourServiceImpl.java` | Logic tour, bỏ broadcast (chuyển controller) |
| `ExeBackendApplication.java` | `@EnableScheduling` |
| `ws/WebSocketConfig.java` | **Mới** — WebSocket config |
| `ws/TourWebSocketHandler.java` | **Mới** — WebSocket handler |

---

## 4. Trạng thái hiện tại & việc cần làm

### Đã hoạt động
- Tạo tour, join tour qua join code.
- Backend deploy Render: `https://exetrekmatebe-1.onrender.com/`
- `tour_ended` push về client (qua SSE; WebSocket cũng implement).
- Join tour trả về đủ member list cho người join.

### Cần verify sau deploy WebSocket
1. **Backend:** commit/push 5 file WebSocket → Render redeploy.
2. **Android:** pull `main`, rebuild APK.
3. **Test flow:**
   - Máy A tạo tour → vào Leader Dashboard.
   - Máy B join bằng code.
   - Máy A phải thấy 2 thành viên ngay lập tức.
4. **Log kiểm tra:**
   - Render: `WS connected`, `WS broadcast type='member_update'`
   - LogCat: `TourWsClient`, `TourRepo` — `Parsed member_update: 2 member(s)`

### Việc chưa làm / ngoài phạm vi cuộc trò chuyện
- Map GPK offline.
- BLE tracking nâng cao.
- Out tour (member tự rời) — chưa có API/event.
- Production database thay H2 file trên `/tmp` Render.

---

## 5. Timeline commit Android (GitHub `EXETREKMATE`)

Các commit liên quan giai đoạn SSE/WebSocket (theo thứ tự gần đúng):

1. `fix(storage): convert MemberDao to abstract class for reliable @Transaction support`
2. `fix(sse): rethrow CancellationException, add debug logging for SSE events`
3. `feat(ws): replace SSE with WebSocket client to bypass nginx/Cloudflare buffering on Render`

Backend: user tự quản lý git riêng tại `Backend/exe-backend/`.

---

## 6. Bài học kỹ thuật

1. **SSE trên Render/Cloudflare:** Response buffering khiến event không real-time; `X-Accel-Buffering: no` không đủ tin cậy. WebSocket là lựa chọn phù hợp hơn cho push real-time.
2. **Broadcast trong transaction:** Không gọi SSE/WS broadcast bên trong `@Transactional` — phải broadcast sau khi commit (ở controller).
3. **Coroutine cancellation:** Khi handler SSE/WS tự cancel job khi `TourEnded`, cleanup Room cần `NonCancellable`.
4. **H2 trên Render free tier:** In-memory mất data khi restart; file-based `/tmp` + `ddl-auto: update` ổn định hơn cho demo.
5. **Android build cache:** Đổi `BASE_URL` trong `build.gradle.kts` cần Clean + Rebuild để áp dụng trên device.

---

*Tài liệu được tạo từ cuộc trò chuyện phát triển TrekMate EXE. Cập nhật lần cuối: tháng 7/2026.*
