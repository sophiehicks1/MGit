package me.sheimi.sgit.repo.tasks;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replacement for the deprecated {@link android.os.AsyncTask}, keeping the same callback names so
 * existing tasks only need to change their superclass.
 *
 * Like AsyncTask's default executor, tasks run one at a time on a background thread (git
 * operations on the same repo must not overlap); callbacks other than doInBackground run on the
 * main thread.
 */
public abstract class BackgroundTask<Params, Progress, Result> {

    private static final ExecutorService SERIAL_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final AtomicBoolean mCancelled = new AtomicBoolean(false);
    private final AtomicBoolean mStarted = new AtomicBoolean(false);

    protected abstract Result doInBackground(Params... params);

    protected void onPreExecute() {
    }

    protected void onPostExecute(Result result) {
    }

    protected void onProgressUpdate(Progress... values) {
    }

    protected void onCancelled() {
    }

    /**
     * Must be called on the main thread, at most once per instance.
     */
    @SafeVarargs
    public final void execute(final Params... params) {
        executeOn(SERIAL_EXECUTOR, params);
    }

    @SafeVarargs
    public final void executeOn(ExecutorService executor, final Params... params) {
        if (!mStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("task can only be executed once");
        }
        onPreExecute();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                final Result result;
                try {
                    result = isCancelled() ? null : doInBackground(params);
                } catch (final RuntimeException e) {
                    // surface the failure like AsyncTask did, rather than losing it in the Future
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            throw e;
                        }
                    });
                    return;
                }
                MAIN_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isCancelled()) {
                            onCancelled();
                        } else {
                            onPostExecute(result);
                        }
                    }
                });
            }
        });
    }

    @SafeVarargs
    protected final void publishProgress(final Progress... values) {
        if (isCancelled()) {
            return;
        }
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (!isCancelled()) {
                    onProgressUpdate(values);
                }
            }
        });
    }

    /**
     * Cancellation is cooperative: doInBackground should poll {@link #isCancelled()}. The worker
     * thread is never interrupted, as interrupting JGit mid-operation can corrupt a repo.
     */
    public final boolean cancel() {
        return !mCancelled.getAndSet(true);
    }

    public final boolean isCancelled() {
        return mCancelled.get();
    }
}
