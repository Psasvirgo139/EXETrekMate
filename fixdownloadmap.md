# Fix Report: GPS Loading & Offline Map Download

Branch: `seperateGPS`
Commits: `ba38143` → `c5c749f` → `0bb6878`

---

## Vấn đề 1 — GPS loading indefinitely ("Đang lấy vị trí GPS" không bao giờ kết thúc)

### Root Cause

**`lastLocation` fallback không có timeout.**

Code cũ dùng `suspendCancellableCoroutine` để đợi `fusedClient.lastLocation` nhưng không bao bọc bởi `withTimeoutOrNull`. Nếu GMS Task không gọi callback (xảy ra khi GPS lần đầu khởi động, Play Services đang bận, hoặc thiết bị chưa có vị trí cache), coroutine **treo vĩnh viễn** mà không có cơ chế nào để thoát ra.

```
Stage 1 (fresh GPS): withTimeoutOrNull(15s)  ← có timeout
Stage 2 (lastLocation): suspendCancellableCoroutine  ← KHÔNG CÓ TIMEOUT ← lỗi
```

Kết quả: state kẹt ở `GettingLocation`, không bao giờ chuyển sang `LocationFailed` hay `Error`.

### Hướng giải quyết đúng

**Mọi GMS Task phải có timeout riêng.**

`fusedClient.lastLocation` và `fusedClient.getCurrentLocation()` đều là async operations — cả hai đều có thể không callback trong một số điều kiện nhất định. Cần dùng `withTimeoutOrNull` cho từng stage.

### Cách giải quyết đã áp dụng

Rewrite `LocationProvider.getCurrentLocation()` thành 2 stage, mỗi stage có timeout độc lập:

```kotlin
// Stage 1: lastLocation — cache read, fast (~ms), timeout 5s
val cached: Location? = withTimeoutOrNull(5_000L) {
    suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
}
if (cached != null) return Result.success(...)

// Stage 2: requestLocationUpdates (1 update) — active GPS, timeout 25s
// Dùng requestLocationUpdates thay getCurrentLocation vì đáng tin cậy hơn
// cho cold-start GPS (chủ động bật GPS hardware).
val fresh: Location? = withTimeoutOrNull(25_000L) {
    suspendCancellableCoroutine { cont ->
        val request = LocationRequest.Builder(PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedClient.removeLocationUpdates(this)
                if (cont.isActive) cont.resume(result.lastLocation)
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
    }
}
return if (fresh != null) Result.success(...) else Result.failure(...)
```

**Thay đổi bổ sung:**
- Tách `GpsManager` thành singleton riêng, độc lập với `OfflineMapManager`
- `GpsManager` expose `GpsState` flow: `Idle → Acquiring → Success(lat, lon) / Failed`
- UI dùng `GpsStatusCard` riêng biệt để hiển thị trạng thái GPS

---

## Vấn đề 2 — Offline map download stuck at 0% (thẻ UI kẹt ở "Đang khởi động tải bản đồ")

### Root Cause

**`callbackFlow` chạy trên `Dispatchers.Default` — Mapbox SDK v11 dispatch callback về calling thread's Looper.**

`Dispatchers.Default` dùng thread pool không có `Looper`. Khi `offlineManager.loadStylePack()` và `tileStore.loadTileRegion()` cần deliver callback, chúng tìm Looper của calling thread — không có → callback **không bao giờ được gọi** → flow không emit → `collect { }` không chạy → state kẹt mãi ở `Downloading(0f, ...)`.

```kotlin
// Code cũ — BUG
private fun loadStylePackFlow(styleUri: String): Flow<Float> = callbackFlow {
    // callbackFlow chạy trên Dispatchers.Default (không có Looper)
    cancelable = offlineManager.loadStylePack(...) { expected ->
        // Callback cần Looper để deliver → KHÔNG BAO GIỜ FIRE
        trySend(1f); close()
    }
    awaitClose { cancelable?.cancel() }
}
```

**Vấn đề phụ — card không hiển thị:**
State chỉ được set sang `Downloading` bên trong `collect { progress -> }`. Nếu callback không fire, state không bao giờ rời `Idle` → `AnimatedVisibility(visible = state !is Idle)` ẩn card.

### Hướng giải quyết đúng

**Gọi tất cả Mapbox API từ Main thread bằng `withContext(Dispatchers.Main)`.**

Mapbox SDK cần Main thread's Looper để dispatch callbacks. Dù `suspendCancellableCoroutine` hay `callbackFlow`, nếu Mapbox API được gọi từ thread không có Looper, callbacks sẽ không fire.

### Cách giải quyết đã áp dụng

**1. Thay `callbackFlow` bằng `suspendCancellableCoroutine` + `withContext(Dispatchers.Main)`:**

```kotlin
private suspend fun loadStylePackSuspend(
    styleUri: String,
    onProgress: (Float) -> Unit
) = withContext(Dispatchers.Main) {      // ← KEY FIX: Main thread
    suspendCancellableCoroutine<Unit> { cont ->
        val cancelable = offlineManager.loadStylePack(
            styleUri, opts,
            { progress -> onProgress(safeFraction(...)) }  // progress callback
        ) { expected ->
            // Completion callback — now fires correctly on Main thread
            if (expected.isValue) cont.resume(Unit)
            else cont.resumeWithException(Exception(...))
        }
        cont.invokeOnCancellation { cancelable.cancel() }
    }
}
```

**2. Set `Downloading` state ngay đầu `beginDownload()` trước khi gọi Mapbox:**

```kotlin
private suspend fun beginDownload() {
    // Guard...
    
    // *** Signal ngay để card hiển thị, trước khi Mapbox API calls ***
    _state.value = MapDownloadState.Downloading(0f, "Đang khởi động tải bản đồ…")
    
    try { downloadAll(...) }
    catch (e: CancellationException) {
        _state.value = MapDownloadState.Idle  // Reset state khi bị cancel
        throw e
    }
    catch (e: Exception) { _state.value = MapDownloadState.Error(...) }
}
```

**3. Timeout bảo vệ mỗi stage:**

```kotlin
// Style pack: 60 giây
val s1ok = withTimeoutOrNull(60_000L) { loadStylePackSuspend(...) }
if (s1ok == null) Log.w(TAG, "Stage 1 timeout — continuing")

// Tile region: 5 phút
val s3ok = withTimeoutOrNull(300_000L) { downloadTileRegionSuspend(...) }
if (s3ok == null) throw Exception("Tile region timeout")
```

**Thay đổi kiến trúc bổ sung:**
- Tách `OfflineMapManager` ra khỏi GPS — dùng tọa độ fix cứng cho testing
- Dùng `collect` + `Job` thay `collectLatest` để tránh cancel giữa download
- Grace period 5 giây trước khi cleanup khi tour kết thúc (tránh WebSocket flapping)
- `CancellationException` được rethrow đúng cách thay vì bị catch bởi `catch (e: Exception)`

---

## Tóm tắt Pattern

| Vấn đề | Nguyên nhân | Fix |
|--------|------------|-----|
| GPS hang forever | `lastLocation` không có timeout | `withTimeoutOrNull` cho mọi GMS Task |
| GPS không request được | `getCurrentLocation()` kém tin cậy cold-start | Dùng `requestLocationUpdates(maxUpdates=1)` |
| Map download 0% stuck | `callbackFlow` trên `Dispatchers.Default` (không Looper) | `withContext(Dispatchers.Main)` + `suspendCancellableCoroutine` |
| Card không hiển thị | State chỉ set trong callback (có thể không fire) | Set `Downloading(0f)` ngay đầu `beginDownload()` |
| Download restart loop | `collectLatest` cancel giữa chừng | `collect` + explicit `Job` + grace period 5s |

---

## Files thay đổi chính

| File | Thay đổi |
|------|---------|
| `LocationProvider.kt` | 2-stage timeout: lastLocation (5s) + requestLocationUpdates (25s) |
| `GpsManager.kt` | Singleton mới — GPS-only, expose `GpsState` |
| `GpsState.kt` | Model mới: `Idle / Acquiring / Success / Failed` |
| `MapDownloadState.kt` | Model mới: `Idle / Downloading / Ready / Error` |
| `OfflineMapManager.kt` | Bỏ GPS, `withContext(Main)`, `suspendCancellableCoroutine`, timeouts |
| `MapViewModel.kt` | Inject cả `GpsManager` + `OfflineMapManager` |
| `MapScreen.kt` | 2 card riêng: `GpsStatusCard` + `MapDownloadCard` |
| `MainActivity.kt` | Thêm `ACCESS_FINE_LOCATION` vào permission request flow |
