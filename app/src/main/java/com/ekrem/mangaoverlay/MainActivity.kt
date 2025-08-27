package com.ekrem.mangaoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.ekrem.mangaoverlay.overlay.OverlayService

class MainActivity : AppCompatActivity() {

  private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

  private lateinit var btnStart: Button
  private lateinit var btnStop: Button
  private lateinit var txtStatus: TextView
  private lateinit var lblInterval: TextView
  private lateinit var seekInterval: SeekBar
  private lateinit var lblOpacity: TextView
  private lateinit var seekOpacity: SeekBar
  private lateinit var spPanelColor: Spinner
  private lateinit var spTextColor: Spinner

  private val overlayPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }

  private val screenCaptureLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val intent = Intent(this, OverlayService::class.java).apply {
          putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
          putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        txtStatus.text = "Durum: Açık"
      } else {
        Toast.makeText(this, "Ekran yakalama iptal edildi", Toast.LENGTH_SHORT).show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    setContentView(R.layout.activity_main)

    // view'ları bağla
    btnStart = findViewById(R.id.btnStart)
    btnStop = findViewById(R.id.btnStop)
    txtStatus = findViewById(R.id.txtStatus)
    lblInterval = findViewById(R.id.lblInterval)
    seekInterval = findViewById(R.id.seekInterval)
    lblOpacity = findViewById(R.id.lblOpacity)
    seekOpacity = findViewById(R.id.seekOpacity)
    spPanelColor = findViewById(R.id.spPanelColor)
    spTextColor = findViewById(R.id.spTextColor)

    // Başlat/Durdur
    btnStart.setOnClickListener { ensureOverlayPermission { requestScreenCapture() } }
    btnStop.setOnClickListener {
      stopService(Intent(this, OverlayService::class.java))
      txtStatus.text = "Durum: Kapalı"
    }

    // ---- Ayarlar ----
    // Çeviri aralığı 1..5 sn (ms olarak saklanır)
    val currentMs = prefs.getInt("interval_ms", 2000)
    val curIdx = (currentMs / 1000).coerceIn(1, 5) - 1
    seekInterval.max = 4
    seekInterval.progress = curIdx
    lblInterval.text = "Çeviri aralığı: ${curIdx + 1} sn"
    seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
        lblInterval.text = "Çeviri aralığı: ${p + 1} sn"
        prefs.edit().putInt("interval_ms", (p + 1) * 1000).apply()
      }
      override fun onStartTrackingTouch(sb: SeekBar?) {}
      override fun onStopTrackingTouch(sb: SeekBar?) {}
    })

    // Opaklık %40..%100
    val curOpacity = prefs.getInt("panel_opacity", 80).coerceIn(40, 100)
    seekOpacity.max = 60
    seekOpacity.progress = curOpacity - 40
    lblOpacity.text = "Panel opaklığı: %$curOpacity"
    seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
        val v = p + 40
        lblOpacity.text = "Panel opaklığı: %$v"
        prefs.edit().putInt("panel_opacity", v).apply()
      }
      override fun onStartTrackingTouch(sb: SeekBar?) {}
      override fun onStopTrackingTouch(sb: SeekBar?) {}
    })

    // Renk seçenekleri
    val panelOptions = arrayOf("Koyu Gri", "Siyah", "Lacivert")
    val panelValues = intArrayOf(0xFF1E1E1E.toInt(), 0xFF000000.toInt(), 0xFF0D1B2A.toInt())
    val textOptions = arrayOf("Beyaz", "Sarı", "Camgöbeği")
    val textValues = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt())

    spPanelColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, panelOptions)
    spTextColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, textOptions)

    val curPanel = prefs.getInt("panel_color", panelValues[0])
    val curPanelIdx = panelValues.indexOf(curPanel).let { if (it >= 0) it else 0 }
    spPanelColor.setSelection(curPanelIdx, false)
    spPanelColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
        prefs.edit().putInt("panel_color", panelValues[position]).apply()
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    val curText = prefs.getInt("text_color", textValues[0])
    val curTextIdx = textValues.indexOf(curText).let { if (it >= 0) it else 0 }
    spTextColor.setSelection(curTextIdx, false)
    spTextColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
        prefs.edit().putInt("text_color", textValues[position]).apply()
      }
      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
  }

  private fun ensureOverlayPermission(onGranted: () -> Unit) {
    if (Settings.canDrawOverlays(this)) onGranted() else {
      val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
      overlayPermissionLauncher.launch(intent)
    }
  }

  private fun requestScreenCapture() {
    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    val capIntent = mpm.createScreenCaptureIntent()
    screenCaptureLauncher.launch(capIntent)
  }
}
