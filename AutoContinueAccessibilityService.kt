package com.alsa.autocontinue

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoContinueAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var observedGenerating = false
    private var sendScheduled = false
    private var lastSendAt = 0L

    private val scheduledSend = Runnable {
        sendScheduled = false
        attemptSend()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        setStatus("سرویس فعال؛ منتظر ChatGPT")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != MainActivity.CHATGPT_PACKAGE) return

        val prefs = Prefs.prefs(this)
        if (!prefs.getBoolean(Prefs.KEY_ARMED, false)) {
            cancelScheduled()
            observedGenerating = false
            return
        }

        val root = rootInActiveWindow ?: return

        if (containsBlockingText(root)) {
            disarm("به علت مشاهده خطا/محدودیت/درخواست تأیید متوقف شد")
            return
        }

        val generating = hasGeneratingControl(root)
        if (generating) {
            observedGenerating = true
            cancelScheduled()
            setStatus("پاسخ در حال تولید است")
            return
        }

        // ایمنی مهم: تنها وقتی ارسال می‌کنیم که در همین چرخه واقعاً کنترل توقف تولید را دیده باشیم.
        if (observedGenerating && !sendScheduled) {
            val delaySeconds = prefs.getInt(Prefs.KEY_DELAY_SECONDS, 20)
            sendScheduled = true
            setStatus("پاسخ پایان یافت؛ ارسال پس از $delaySeconds ثانیه")
            handler.postDelayed(scheduledSend, delaySeconds * 1000L)
        }
    }

    override fun onInterrupt() {
        cancelScheduled()
        setStatus("سرویس متوقف یا قطع شد")
    }

    private fun attemptSend() {
        val prefs = Prefs.prefs(this)
        if (!prefs.getBoolean(Prefs.KEY_ARMED, false)) return

        val sent = prefs.getInt(Prefs.KEY_SENT_COUNT, 0)
        val max = prefs.getInt(Prefs.KEY_MAX_PARTS, 10)
        if (sent >= max) {
            disarm("حداکثر $max ارسال انجام شد")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSendAt < 10_000L) return

        val root = rootInActiveWindow ?: run {
            disarm("پنجره ChatGPT در دسترس نیست")
            return
        }

        if (hasGeneratingControl(root) || containsBlockingText(root)) {
            disarm("شرایط امن ارسال برقرار نبود")
            return
        }

        val prompt = prefs.getString(Prefs.KEY_PROMPT, Prefs.DEFAULT_PROMPT).orEmpty()
        val editor = findBestEditable(root)
        if (editor == null || !setNodeText(editor, prompt)) {
            disarm("کادر نوشتن پیام پیدا نشد")
            return
        }

        handler.postDelayed({
            val refreshedRoot = rootInActiveWindow
            val sendButton = refreshedRoot?.let { findSendButton(it) }
            if (sendButton != null && sendButton.isEnabled && clickNodeOrParent(sendButton)) {
                lastSendAt = System.currentTimeMillis()
                observedGenerating = false
                val newCount = sent + 1
                prefs.edit()
                    .putInt(Prefs.KEY_SENT_COUNT, newCount)
                    .putString(Prefs.KEY_STATUS, "پیام ادامه شماره $newCount ارسال شد")
                    .apply()
                if (newCount >= max) {
                    handler.postDelayed({ disarm("حداکثر $max ارسال تکمیل شد") }, 1500L)
                }
            } else {
                disarm("دکمه ارسال پیدا نشد")
            }
        }, 800L)
    }

    private fun findBestEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val all = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (node.isEditable || node.className?.toString()?.contains("EditText", true) == true) {
                all += node
            }
        }
        return all.lastOrNull { it.isEnabled && it.isVisibleToUser }
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var exact: AccessibilityNodeInfo? = null
        var fallback: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val label = nodeLabel(node).lowercase()
            if (node.isVisibleToUser && node.isEnabled && (node.isClickable || node.parent?.isClickable == true)) {
                if (label == "send" || label == "ارسال" || label.contains("send message") || label.contains("ارسال پیام")) {
                    exact = node
                } else if (node.className?.toString()?.contains("ImageButton", true) == true) {
                    fallback = node
                }
            }
        }
        return exact ?: fallback
    }

    private fun hasGeneratingControl(root: AccessibilityNodeInfo): Boolean {
        var found = false
        traverse(root) { node ->
            val label = nodeLabel(node).lowercase()
            if (label.contains("stop generating") ||
                label.contains("stop response") ||
                label == "stop" ||
                label.contains("توقف تولید") ||
                label.contains("توقف پاسخ")) {
                found = true
            }
        }
        return found
    }

    private fun containsBlockingText(root: AccessibilityNodeInfo): Boolean {
        val blockers = listOf(
            "you've reached", "usage limit", "rate limit", "try again later", "something went wrong",
            "network error", "confirm", "are you sure", "upgrade to", "failed to send",
            "به سقف", "محدودیت استفاده", "بعداً دوباره", "خطای شبکه", "مشکلی پیش آمد",
            "تأیید", "آیا مطمئن", "ارتقا", "ارسال نشد"
        )
        var found = false
        traverse(root) { node ->
            val label = nodeLabel(node).lowercase()
            if (blockers.any { label.contains(it) }) found = true
        }
        return found
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String = buildString {
        node.text?.let { append(it).append(' ') }
        node.contentDescription?.let { append(it).append(' ') }
        node.viewIdResourceName?.let { append(it) }
    }.trim()

    private inline fun traverse(root: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 2500) {
            val node = stack.removeLast()
            visited++
            action(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(5) {
            if (current?.isClickable == true && current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                return true
            }
            current = current?.parent
        }
        return false
    }

    private fun cancelScheduled() {
        handler.removeCallbacks(scheduledSend)
        sendScheduled = false
    }

    private fun disarm(reason: String) {
        cancelScheduled()
        observedGenerating = false
        Prefs.prefs(this).edit()
            .putBoolean(Prefs.KEY_ARMED, false)
            .putString(Prefs.KEY_STATUS, reason)
            .apply()
    }

    private fun setStatus(status: String) {
        Prefs.prefs(this).edit().putString(Prefs.KEY_STATUS, status).apply()
    }
}
