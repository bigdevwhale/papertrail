package app.papertrail.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.papertrail.MainActivity
import app.papertrail.R
import app.papertrail.container
import app.papertrail.domain.Case
import app.papertrail.domain.Report
import app.papertrail.domain.date
import app.papertrail.localized
import app.papertrail.ui.Words
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** The two things the office ever says out loud: a new case is open, and the evening report. */
object Notify {
    private const val REPORT = "report"
    private const val CASES = "cases"

    fun channels(ctx: Context) {
        val c = ctx.localized()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(REPORT, c.getString(R.string.channel_report), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CASES, c.getString(R.string.channel_cases), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_trail)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx)).setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) {}
    }

    suspend fun cases(ctx: Context, opened: List<Case>) {
        if (opened.isEmpty()) return
        val c = ctx.container
        val s = c.repo.state.value ?: return
        val st = c.settings.now()
        val w = Words.of(ctx.localized(), st)
        for (case in opened) post(ctx, CASES, 1000 + case.number, w.caseTitle(case), w.caseLead(case, s))
    }

    /** Asks for the next evening report, at nine. */
    fun plan(ctx: Context) {
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(21, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val delay = java.time.Duration.between(now, at).toMillis()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            REPORT, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReportWork>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    class ReportWork(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
        override suspend fun doWork(): Result {
            val ctx = applicationContext
            val c = ctx.container
            val st = c.settings.now()
            if (st.onboarded && st.report && st.advance > 0) {
                val s = c.repo.refresh()
                val zone = ZoneId.systemDefault()
                val w = Words.of(ctx.localized(), st)
                val f = Report.facts(s, st.plan, System.currentTimeMillis().date(zone), zone)
                Report.pick(c.lines, f)?.let { post(ctx, REPORT, 1, w.t("Вечерний рапорт", "Evening report"), w.line(it, f, s)) }
            }
            plan(ctx)
            return Result.success()
        }
    }
}
