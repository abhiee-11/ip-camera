package com.freecam.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.freecam.camera.R
import com.freecam.camera.data.AppLog
import com.freecam.camera.databinding.ActivityLogsBinding

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_logs)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("FreeCam Logs", binding.tvLogs.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnClear.setOnClickListener {
            val appLog = (application as com.freecam.camera.App).appLog
                ?: com.freecam.camera.data.AppLog(this)
            appLog.clear()
            binding.tvLogs.text = ""
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
        }

        loadLogs()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadLogs() {
        val appLog = (application as com.freecam.camera.App).appLog
            ?: com.freecam.camera.data.AppLog(this)
        val logFile = appLog.getLogFile()
        if (logFile.exists()) {
            binding.tvLogs.text = logFile.readText()
        } else {
            binding.tvLogs.text = "No logs available"
        }
    }
}