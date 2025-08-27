package com.ekrem.mangaoverlay
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ekrem.mangaoverlay.databinding.ActivityMainBinding
import com.ekrem.mangaoverlay.overlay.OverlayService
class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
  private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val intent = Intent(this, OverlayService::class.java).apply {
        putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
        putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
      binding.txtStatus.text = "Durum: Açık"
    }
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    binding.btnStart.setOnClickListener { ensureOverlayPermission { requestScreenCapture() } }
    binding.btnStop.setOnClickListener { stopService(Intent(this, OverlayService::class.java)); binding.txtStatus.text = "Durum: Kapalı" }
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
