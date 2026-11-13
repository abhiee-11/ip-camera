package com.freecam.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.freecam.camera.R
import com.freecam.camera.databinding.ActivityLogsBinding

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var appLog: com.freecam.camera.data.AppLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_logs)

        appLog = (application as com.freecam.camera.App).appLog ?: com.freecam.camera.data.AppLog(this).also {
            (application as com.freecam.camera.App).appLog = it
        }

        binding.tvLogs.text = appLog.getLogs().joinToString("\n")

        binding.btnCopyLogs.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip = ClipData.newPlainText("FreeCam Logs", appLog.copyAll())
            Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnClearLogs.setOnClickListener {
            appLog.clear()
            binding.tvLogs.text = ""
            Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
        }
    }
}