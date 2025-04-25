package com.example.hackathon_step_counter
// used for restting the counter at midnight everyday
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ResetWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        // Broadcast a signal to reset steps
        val intent = android.content.Intent("RESET_STEP_COUNTER")
        applicationContext.sendBroadcast(intent)

        return Result.success()
    }
}
