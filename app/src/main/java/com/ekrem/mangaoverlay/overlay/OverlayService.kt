package com.ekrem.mangaoverlay.overlay
import android.app.*; import android.content.*; import android.graphics.*; import android.hardware.display.*; import android.media.*; import android.media.projection.*; import android.os.*; import android.view.*; import android.widget.*
import com.ekrem.mangaoverlay.R
import kotlinx.coroutines.*

class OverlayService : Service() {
  companion object { const val EXTRA_RESULT_CODE = "result_code"; const val EXTRA_RESULT_DATA = "result_data" }

  private lateinit var windowManager: WindowManager
  private var overlayView: View? = null
  private var txtOutput: TextView? = null

  private var projection: MediaProjection? = null
  private var imageReader: ImageReader? = null
  private var vDisplay: VirtualDisplay? = null

  private var projThread: HandlerThread? = null
  private var projHandler: Handler? = null
  private val projCallback = object : MediaProjection.Callback() {
    override fun onStop() {
      // Sistem capture'ı durdurursa kaynakları bırak
      releaseCapture()
    }
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    startAsForeground()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    showOverlay()
    startProjection(intent)
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
    releaseCapture()
    overlayView?.let { runCatching { windowManager.removeView(it) } }
  }

  private fun startAsForeground() {
    val channelId = "overlay_service"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ch = NotificationChannel(channelId, "Çeviri Servisi", NotificationManager.IMPORTANCE_LOW)
      getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    val notif: Notification =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        Notification.Builder(this, channelId).setContentTitle("Çeviri servisi çalışıyor").setSmallIcon(android.R.drawable.ic_menu_info_details).build()
      else
        Notification.Builder(this).setContentTitle("Çeviri servisi çalışıyor").setSmallIcon(android.R.drawable.ic_menu_info_details).build()
    startForeground(1, notif)
  }

  private fun showOverlay() {
    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    )
    lp.gravity = Gravity.TOP or Gravity.START; lp.x = 60; lp.y = 140

    overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
    txtOutput = overlayView!!.findViewById(R.id.txtOutput)
    val touchHelper = DraggableResizableTouchHelper(windowManager, overlayView!!, lp)
    overlayView!!.setOnTouchListener(touchHelper)
    overlayView!!.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { stopSelf() }
    windowManager.addView(overlayView, lp)
  }

  private fun startProjection(intent: Intent?) {
    val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
    val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return

    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    projection = mpm.getMediaProjection(code, data) ?: return

    // ---- Android 14+ gereği: önce callback kaydet, sonra capture başlat ----
    if (projThread == null) {
      projThread = HandlerThread("projection-cb").also { it.start() }
      projHandler = Handler(projThread!!.looper)
    }
    projection!!.registerCallback(projCallback, projHandler)

    val metrics = resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val density = metrics.densityDpi

    imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    vDisplay = projection!!.createVirtualDisplay(
      "manga-capture", width, height, density,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      imageReader!!.surface, null, null
    )

    scope.launch {
      val ocr = com.ekrem.mangaoverlay.ocr.OcrTranslator(this@OverlayService)
      var lastHash = 0L
      while (isActive) {
        val bmp = takeFrame()
        if (bmp != null) {
          val h = quickHash(bmp)
          if (h != lastHash) {
            lastHash = h
            runCatching {
              val tr = ocr.recognizeAndTranslate(bmp)
              withContext(Dispatchers.Main) { txtOutput?.text = tr.ifBlank { "(metin bulunamadı)" } }
            }.onFailure {
              withContext(Dispatchers.Main) { txtOutput?.text = "Hata: ${it.message}" }
            }
          }
          bmp.recycle()
        }
        delay(700)
      }
    }
  }

  private fun releaseCapture() {
    try { vDisplay?.release() } catch (_: Throwable) { }
    try { imageReader?.close() } catch (_: Throwable) { }
    try { projection?.unregisterCallback(projCallback) } catch (_: Throwable) { }
    try { projection?.stop() } catch (_: Throwable) { }
    vDisplay = null; imageReader = null; projection = null
    projThread?.quitSafely(); projThread = null; projHandler = null
  }

  private fun takeFrame(): Bitmap? {
    val img = imageReader?.acquireLatestImage() ?: return null
    val plane = img.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val width = img.width
    val height = img.height
    val bmp = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buffer)
    img.close()
    return Bitmap.createBitmap(bmp, 0, 0, width, height)
  }

  private fun quickHash(bmp: Bitmap): Long {
    var sum = 0L
    val stepX = (bmp.width / 32).coerceAtLeast(1)
    val stepY = (bmp.height / 32).coerceAtLeast(1)
    var y = 0
    while (y < bmp.height) {
      var x = 0
      while (x < bmp.width) { sum += bmp.getPixel(x, y).toLong(); x += stepX }
      y += stepY
    }
    return sum
  }
}
