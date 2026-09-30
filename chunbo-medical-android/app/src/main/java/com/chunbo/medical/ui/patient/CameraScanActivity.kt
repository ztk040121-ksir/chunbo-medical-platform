package com.chunbo.medical.ui.patient

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.data.model.Registration
import com.chunbo.medical.databinding.ActivityCameraScanBinding
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class CameraScanActivity : AppCompatActivity(), SurfaceHolder.Callback, Camera.PreviewCallback {

    private lateinit var binding: ActivityCameraScanBinding
    private var camera: Camera? = null
    private var isFlashOn = false
    private var isDecoding = false
    private var hasHandledSuccess = false
    private var laserAnimator: ValueAnimator? = null
    private val multiFormatReader = MultiFormatReader()
    private var pendingRegistration: Registration? = null

    companion object {
        private const val REQUEST_CAMERA_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        loadPendingRegistration()
        startLaserAnimation()
        checkCameraPermission()
    }

    private fun initViews() {
        binding.btnBackScan.setOnClickListener {
            finish()
        }

        binding.btnToggleFlash.setOnClickListener {
            toggleFlashlight()
        }



        binding.cameraSurfaceView.holder.addCallback(this)
    }

    private fun loadPendingRegistration() {
        val user = UserManager.getUser()
        val phoneFilter = if (user.isLoggedIn && !user.phone.isNullOrBlank()) user.phone else null

        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    ApiClient.service.getRegistrationList(userPhone = phoneFilter)
                }
                pendingRegistration = list.firstOrNull { it.status == "待签到" }
                if (pendingRegistration != null) {
                    binding.tvScanPatientInfo.text = "待签到就诊人: ${pendingRegistration?.patientName} (${pendingRegistration?.department} · ${pendingRegistration?.doctorName})"
                } else {
                    binding.tvScanPatientInfo.text = "提示: 电脑端诊室签到码扫描（当前就诊人随时入队）"
                }
            } catch (e: Exception) {
                binding.tvScanPatientInfo.text = "就诊卡连接就绪，对准电脑屏幕二维码即可"
            }
        }
    }

    private fun startLaserAnimation() {
        laserAnimator = ValueAnimator.ofFloat(0f, 250f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.scanLaserLine.translationY = v * resources.displayMetrics.density
            }
            start()
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_CODE
            )
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "需要相机权限以扫描电脑端签到二维码", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun openCamera() {
        try {
            if (camera == null) {
                camera = Camera.open(0) // 后置摄像头
                camera?.setDisplayOrientation(90) // 竖屏方向适配
                val params = camera?.parameters
                val focusModes = params?.supportedFocusModes
                if (focusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                    params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }
                camera?.parameters = params
            }
            if (binding.cameraSurfaceView.holder.surface.isValid) {
                camera?.setPreviewDisplay(binding.cameraSurfaceView.holder)
                camera?.setPreviewCallback(this)
                camera?.startPreview()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "启动相机硬件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            camera?.setPreviewDisplay(holder)
            camera?.setPreviewCallback(this)
            camera?.startPreview()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (holder.surface == null) return
        try {
            camera?.stopPreview()
            camera?.setPreviewDisplay(holder)
            camera?.setPreviewCallback(this)
            camera?.startPreview()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseCamera()
    }

    override fun onPreviewFrame(data: ByteArray?, cam: Camera?) {
        if (data == null || cam == null || isDecoding || hasHandledSuccess) return
        isDecoding = true

        val params = cam.parameters ?: run {
            isDecoding = false
            return
        }
        val size = params.previewSize ?: run {
            isDecoding = false
            return
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                var rawText: String? = null
                // 1. 尝试直接解码
                try {
                    val source = PlanarYUVLuminanceSource(
                        data, size.width, size.height,
                        0, 0, size.width, size.height, false
                    )
                    val bitmap = BinaryBitmap(HybridBinarizer(source))
                    rawText = multiFormatReader.decodeWithState(bitmap)?.text
                } catch (e: Exception) {
                    // direct decode failed
                }

                // 2. 若直接解码未识别，旋转 90 度为竖屏矩阵后解码
                if (rawText.isNullOrBlank()) {
                    try {
                        val rotatedData = ByteArray(data.size)
                        for (y in 0 until size.height) {
                            for (x in 0 until size.width) {
                                rotatedData[x * size.height + size.height - y - 1] = data[x + y * size.width]
                            }
                        }
                        val rotatedSource = PlanarYUVLuminanceSource(
                            rotatedData, size.height, size.width,
                            0, 0, size.height, size.width, false
                        )
                        val rotatedBitmap = BinaryBitmap(HybridBinarizer(rotatedSource))
                        rawText = multiFormatReader.decodeWithState(rotatedBitmap)?.text
                    } catch (ignored: Exception) {}
                }

                if (!rawText.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        if (!hasHandledSuccess) {
                            handleQrResult(rawText)
                        }
                    }
                }
            } catch (e: Exception) {
                // 当前帧未扫描到完整二维码，继续等待下一帧
            } finally {
                multiFormatReader.reset()
                isDecoding = false
            }
        }
    }

    private fun handleQrResult(qrContent: String) {
        hasHandledSuccess = true
        triggerVibration()
        binding.tvScanHint.text = "✅ 成功扫描电脑端二维码！正在办理入队签到..."
        binding.scanLaserLine.setBackgroundColor(ContextCompat.getColor(this, com.chunbo.medical.R.color.primary))

        performSignOperation(qrContent)
    }

    private fun performSignOperation(sourceToken: String) {
        hasHandledSuccess = true
        lifecycleScope.launch {
            try {
                // 智能识别专属患者二维码或诊室现场码
                var specificId: Long? = null
                var isDeskSign = false
                when {
                    sourceToken.contains("CHUNBO_CLINIC_DESK_SIGNIN") -> isDeskSign = true
                    sourceToken.contains("CHUNBO_SIGN:") || sourceToken.contains("REG_ID:") -> {
                        val parts = sourceToken.split(":")
                        if (parts.size >= 2) {
                            specificId = parts[1].toLongOrNull()
                        }
                    }
                }

                val targetReg = if (specificId != null) null else (pendingRegistration ?: run {
                    val user = UserManager.getUser()
                    val phoneFilter = if (user.isLoggedIn && !user.phone.isNullOrBlank()) user.phone else null
                    val list = withContext(Dispatchers.IO) {
                        ApiClient.service.getRegistrationList(userPhone = phoneFilter)
                    }
                    list.firstOrNull { it.status == "待签到" }
                })

                val signTargetId = specificId ?: targetReg?.id

                if (signTargetId != null) {
                    val signed = withContext(Dispatchers.IO) {
                        ApiClient.service.signRegistration(signTargetId)
                    }
                    Toast.makeText(
                        this@CameraScanActivity,
                        "🎉 现场扫码签到成功！\n就诊人【${signed.patientName}】已进入【${signed.doctorName ?: "主诊医生"}】待诊队列，请留意叫号！",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@CameraScanActivity, "未检测到待签到记录，请确认已预约挂号或在电脑端诊室报到", Toast.LENGTH_LONG).show()
                }

                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CameraScanActivity, "签到异常: ${e.message}", Toast.LENGTH_SHORT).show()
                hasHandledSuccess = false
            }
        }
    }

    private fun toggleFlashlight() {
        try {
            val params = camera?.parameters ?: return
            if (isFlashOn) {
                params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                camera?.parameters = params
                isFlashOn = false
                binding.btnToggleFlash.setImageResource(com.chunbo.medical.R.drawable.ic_flashlight)
                binding.btnToggleFlash.setColorFilter(android.graphics.Color.WHITE)
            } else {
                params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                camera?.parameters = params
                isFlashOn = true
                binding.btnToggleFlash.setImageResource(com.chunbo.medical.R.drawable.ic_flashlight_active)
                binding.btnToggleFlash.clearColorFilter()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "该设备不支持闪光灯手电筒", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vib?.vibrate(100)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun releaseCamera() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        laserAnimator?.cancel()
        releaseCamera()
    }
}
