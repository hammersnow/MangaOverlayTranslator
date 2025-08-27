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
import com.ekrem.mangaoverlay.databinding.ActivityMainBinding
import com.ekrem.mangaoverlay.overlay.OverlayService

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

  private val overlayPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

  private val screenCaptureLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val intent = Intent(this, OverlayService::class.java).apply {
          putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
          putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        binding.txtStatus.text = "Durum: Açık"
      } else {
        Toast.makeText(this, "Ekran yakalama iptal edildi", Toast.LENGTH_SHORT).show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Butonlar
    binding.btnStart.setOnClickListener { ensureOverlayPermission { requestScreenCapture() } }
    binding.btnStop.setOnClickListener {
      stopService(Intent(this, OverlayService::class.java))
      binding.txtStatus.text = "Durum: Kapalı"
    }

    // --- Ayarlar ---
    // Interval 1..5 sn -> stored ms
    val currentMs = prefs.getInt("interval_ms", 2000)
    val idx = (currentMs / 1000).coerceIn(1, 5) - 1
    binding.seekInterval.progress = idx
    binding.lblInterval.text = "Çeviri aralığı: ${idx + 1} sn"
    binding.seekInterval.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
      override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
        binding.lblInterval.text = "Çeviri aralığı: ${p + 1} sn"
        prefs.edit().putInt("interval_ms", (p + 1) * 1000).apply()
      }
      override fun onStartTrackingTouch(sb: SeekBar?) {}
      override fun onStopTrackingTouch(sb: SeekBar?) {}
    })

    // Opacity %40..100
    val curOpacity = prefs.getInt("panel_opacity", 80) // %
    binding.seekOpacity.progress = (curOpacity - 40)
    binding.lblOpacity.text = "Panel opaklığı: %$curOpacity"
    binding.seekOpacity.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
      override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
        val value = p + 40
        binding.lblOpacity.text = "Panel opaklığı: %$value"
        prefs.edit().putInt("panel_opacity", value).apply()
      }
      override fun onStartTrackingTouch(sb: SeekBar?) {}
      override fun onStopTrackingTouch(sb: SeekBar?) {}
    })

    // Panel rengi & Yazı rengi (preset)
    val panelOptions = arrayOf("Koyu Gri", "Siyah", "Lacivert")
    val panelValues = intArrayOf(0xFF1E1E1E.toInt(), 0xFF000000.toInt(), 0xFF0D1B2A.toInt())
    val textOptions = arrayOf("Beyaz", "Sarı", "Camgöbeği")
    val textValues = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00E5FF.toInt())

    fun <T> Spinner.bind(items: Array<String>, sel: Int, onSel: (Int)->Unit) {
      adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
      setSelection(sel, false)
      onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { onSel(position) }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
      }
    }

    val curPanel = prefs.getInt("panel_color", panelValues[0])
    val curPanelIdx = panelValues.indexOf(curPanel).let { if (it >= 0) it else 0 }
    binding.spPanelColor.bind(panelOptions, curPanelIdx) { i -> prefs.edit().putInt("panel_color", panelValues[i]).apply() }

    val curText = prefs.getInt("text_color", textValues[0])
    val curTextIdx = textValues.indexOf(curText).let { if (it >= 0) it else 0 }
    binding.spTextColor.bind(textOptions, curTextIdx) { i -> prefs.edit().putInt("text_color", textValues[i]).apply() }
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
