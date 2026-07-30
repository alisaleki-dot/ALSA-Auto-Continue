package com.alsa.autocontinue

import android.content.Context

object Prefs {
    private const val FILE = "alsa_auto_continue"
    const val KEY_PROMPT = "prompt"
    const val KEY_MAX_PARTS = "max_parts"
    const val KEY_DELAY_SECONDS = "delay_seconds"
    const val KEY_ARMED = "armed"
    const val KEY_SENT_COUNT = "sent_count"
    const val KEY_STATUS = "status"

    const val DEFAULT_PROMPT = "ادامه مستقیم از آخرین خط، بدون تکرار و خلاصه‌سازی. ابتدا سازگاری این بخش را با تمام قراردادها و تصمیمات قبلی بررسی کن، سپس Part بعدی را با بیشترین دقت فنی، قابلیت پیاده‌سازی واقعی در MQL5، تست‌پذیری و مدیریت ریسک تکمیل کن. اگر ابهام، تناقض یا خطر معماری وجود دارد، ادامه خودکار را متوقف و آن را گزارش کن."

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
