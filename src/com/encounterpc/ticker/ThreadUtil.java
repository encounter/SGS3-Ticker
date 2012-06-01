package com.encounterpc.ticker;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.RejectedExecutionException;

public class ThreadUtil {
    public static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public static boolean checkIfMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static void enforceMainThread() {
        if (!checkIfMainThread())
            throw new RejectedExecutionException("This must be called on the main thread!");
    }

    public static void runDelayedOnUiThread(Runnable paramRunnable, int paramInt) {
        mainThreadHandler.postDelayed(paramRunnable, paramInt);
    }

    public static void runOnUiThread(Runnable paramRunnable) {
        if (!checkIfMainThread())
            mainThreadHandler.post(paramRunnable);
        else paramRunnable.run();
    }
}
