package com.ekrem.mangaoverlay.overlay
import android.app.*; import android.content.*; import android.graphics.*; import android.hardware.display.*; import android.media.*; import android.media.projection.*; import android.os.*; import android.view.*; import android.widget.*
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
    private val projCallback = object : MediaProjection.Callback() { override fun onStop() { releaseCapture() } }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onBind(intent: Intent?) = null

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 60; lp.y = 200

        overlayView = LayoutInflater.from(this).inflate(
            resources.getIdentifier("overlay_panel", "layout", packageName),
            null
        )
        txtOutput = overlayView!!.findViewById(
            resources.getIdentifier("txtOutput", "id", packageName)
        )
        val helper = DraggableResizableTouchHelper(windowManager, overlayView!!, lp)
        overlayView!!.setOnTouchListener(helper)
        overlayView!!.findViewById<ImageButton>(
            resources.getIdentifier("btnClose", "id", packageName)
        ).setOnClickListener { stopSelf() }

        windowManager.addView(overlayView, lp)
    }

    private fun startProjection(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data) ?: return

        if (projThread == null) { projThread = HandlerThread("projection-cb").also { it.start() }; projHandler = Handler(projThread!!.looper) }
        projection!!.registerCallback(projCallback, projHandler)

        val dm = resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val density = dm.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        vDisplay = projection!!.createVirtualDisplay(
            "manga-capture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        scope.launch {
            val ocr = com.ekrem.mangaoverlay.ocr.OcrTranslator(this@OverlayService)
            var lastHash = 0L
            var lastText = ""
            while (isActive) {
                val frame = takeFrameCroppedAndMasked()  // status bar kırp + paneli maskele
                if (frame != null) {
                    val h = quickHash(frame)
                    if (h != lastHash) {
                        lastHash = h
                        runCatching {
                            val raw = ocr.recognizeAndTranslate(frame)
                            val tr = cleanText(raw)
                            if (tr.isNotBlank() && !isMostlySame(tr, lastText)) {
                                lastText = tr
                                withContext(Dispatchers.Main) { txtOutput?.text = tr }
                            }
                        }
                    }
                    frame.recycle()
                }
                delay(2000)  // 2 sn'de bir
            }
        }
    }

    private fun releaseCapture() {
        runCatching { vDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.unregisterCallback(projCallback) }
        runCatching { projection?.stop() }
        vDisplay = null; imageReader = null; projection = null
        projThread?.quitSafely(); projThread = null; projHandler = null
    }

    // --- KARE AL: üst barı kırp + panelin bulunduğu alanı siyahla maskele ---
    private fun takeFrameCroppedAndMasked(): Bitmap? {
        val img = imageReader?.acquireLatestImage() ?: return null
        val plane = img.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val fullW = img.width
        val fullH = img.height

        val tmp = Bitmap.createBitmap(rowStride / pixelStride, fullH, Bitmap.Config.ARGB_8888)
        tmp.copyPixelsFromBuffer(buffer)
        img.close()

        val topCrop = getStatusBarHeight()
        val h = (fullH - topCrop).coerceAtLeast(1)
        var cropped = Bitmap.createBitmap(tmp, 0, topCrop, fullW, h)
        tmp.recycle()

        // Panel alanını siyahla boya (OCR görmesin)
        overlayView?.let { v ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val left = loc[0].coerceAtLeast(0)
            val top = (loc[1] - topCrop).coerceAtLeast(0)      // kırpmayı telafi et
            val right = (left + v.width).coerceAtMost(cropped.width)
            val bottom = (top + v.height).coerceAtMost(cropped.height)
            if (right > left && bottom > top) {
                val mutable = cropped.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)
                val paint = Paint().apply { color = Color.BLACK; alpha = 255 }
                canvas.drawRect(Rect(left, top, right, bottom), paint)
                cropped.recycle()
                cropped = mutable
            }
        }
        return cropped
    }

    private fun getStatusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else (24 * resources.displayMetrics.density).toInt()
    }

    private fun quickHash(bmp: Bitmap): Long {
        var sum = 0L
        val stepX = (bmp.width / 32).coerceAtLeast(1)
        val stepY = (bmp.height / 32).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) { var x = 0; while (x < bmp.width) { sum += bmp.getPixel(x, y).toLong(); x += stepX }; y += stepY }
        return sum
    }

    // --- Gürültü temizleme: URL/domain ve panel metinlerini at ---
    private fun cleanText(input: String): String {
        val lines = input.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val urlRegex = Regex("""\b([a-z0-9\-]+\.)+(com|net|org|io|co|me|app|site|ru|cn|uk)\b[\S]*""", RegexOption.IGNORE_CASE)
        val junk = setOf("canlı çeviri", "paneli sağ-alt köşeden tutup büyütebilirsiniz")
        val filtered = lines.filter { line ->
            val low = line.lowercase()
            !urlRegex.containsMatchIn(line) &&
            junk.none { low.contains(it) }
        }
        // Aynı satırı tekrar tekrar yazma
        return filtered.distinct().joinToString("\n")
    }

    private fun isMostlySame(a: String, b: String): Boolean {
        if (a == b) return true
        val minLen = minOf(a.length, b.length)
        if (minLen == 0) return false
        val same = a.commonPrefixWith(b).length
        return (same.toDouble() / minLen) > 0.8
    }
}
