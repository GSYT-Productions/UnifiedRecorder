package gsyt.android.unifiedrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.MediaProjection
import android.media.MediaProjectionManager
import android.media.MediaRecorder
import android.os.Environment
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class UnifiedRecorderTileService : TileService() {

    private val REQUEST_CODE = 1000
    private var projectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Recording"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Start Recording"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isRecording) {
            stopRecording()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE)
                } else {
                    startRecording()
                }
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager?.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            mediaProjection = projectionManager?.getMediaProjection(resultCode, data!!)
            prepareRecorder()
            virtualDisplay = createVirtualDisplay()
            mediaRecorder?.start()
            isRecording = true
            updateTile()
        }
    }

    private fun prepareRecorder() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            val outputPath = File(getExternalFilesDir(null), "ScreenRecordings")
            if (!outputPath.exists()) {
                outputPath.mkdirs()
            }
            setOutputFile(File(outputPath, "recording_${System.currentTimeMillis()}.mp4").absolutePath)
            setVideoSize(1080, 1920)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setVideoEncodingBitRate(512 * 1000)
            setVideoFrameRate(30)
            try {
                prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return mediaProjection!!.createVirtualDisplay(
            "UnifiedRecorder",
            1080, 1920, metrics.densityDpi,
            WindowManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            reset()
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
        isRecording = false
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile
        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Recording"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Start Recording"
        }
        tile.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (isRecording) {
            stopRecording()
        }
    }
}
