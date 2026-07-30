package com.alsa.autocontinue

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alsa.autocontinue.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()

        binding.saveButton.setOnClickListener { saveSettings() }
        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.openChatGptButton.setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "اپ ChatGPT پیدا نشد.", Toast.LENGTH_LONG).show()
        }
        binding.resetButton.setOnClickListener {
            Prefs.prefs(this).edit()
                .putBoolean(Prefs.KEY_ARMED, false)
                .putInt(Prefs.KEY_SENT_COUNT, 0)
                .putString(Prefs.KEY_STATUS, "متوقف شد؛ شمارنده صفر شد")
                .apply()
            binding.armedSwitch.isChecked = false
            refreshStatus()
            Toast.makeText(this, "ارسال خودکار متوقف شد.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }

    private fun loadSettings() {
        val p = Prefs.prefs(this)
        binding.promptInput.setText(p.getString(Prefs.KEY_PROMPT, Prefs.DEFAULT_PROMPT))
        binding.maxPartsInput.setText(p.getInt(Prefs.KEY_MAX_PARTS, 10).toString())
        binding.delayInput.setText(p.getInt(Prefs.KEY_DELAY_SECONDS, 20).toString())
        binding.armedSwitch.isChecked = p.getBoolean(Prefs.KEY_ARMED, false)
        refreshStatus()
    }

    private fun saveSettings() {
        val prompt = binding.promptInput.text?.toString()?.trim().orEmpty()
        val maxParts = binding.maxPartsInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 50) ?: 10
        val delay = binding.delayInput.text?.toString()?.toIntOrNull()?.coerceIn(10, 300) ?: 20

        if (prompt.isBlank()) {
            Toast.makeText(this, "متن ادامه نمی‌تواند خالی باشد.", Toast.LENGTH_LONG).show()
            return
        }

        Prefs.prefs(this).edit()
            .putString(Prefs.KEY_PROMPT, prompt)
            .putInt(Prefs.KEY_MAX_PARTS, maxParts)
            .putInt(Prefs.KEY_DELAY_SECONDS, delay)
            .putBoolean(Prefs.KEY_ARMED, binding.armedSwitch.isChecked)
            .putInt(Prefs.KEY_SENT_COUNT, 0)
            .putString(Prefs.KEY_STATUS, if (binding.armedSwitch.isChecked) "مسلح؛ منتظر مشاهده تولید پاسخ" else "غیرفعال")
            .apply()

        refreshStatus()
        Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatus() {
        val p = Prefs.prefs(this)
        val armed = p.getBoolean(Prefs.KEY_ARMED, false)
        val sent = p.getInt(Prefs.KEY_SENT_COUNT, 0)
        val max = p.getInt(Prefs.KEY_MAX_PARTS, 10)
        val status = p.getString(Prefs.KEY_STATUS, "غیرفعال")
        binding.statusText.text = "وضعیت: $status\nارسال‌ها: $sent از $max\nARM: ${if (armed) "روشن" else "خاموش"}"
    }

    companion object {
        const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }
}
