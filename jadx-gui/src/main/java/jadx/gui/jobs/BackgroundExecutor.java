package jadx.gui.jobs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.utils.exceptions.JadxRuntimeException;
import jadx.gui.ui.MainWindow;
import jadx.gui.ui.panel.ProgressPanel;
import jadx.gui.utils.NLS;
import jadx.gui.utils.UiUtils;

import static jadx.gui.utils.UiUtils.calcProgress;

/**
 * Class for run tasks in background with progress bar indication.
 * Use instance created in {@link MainWindow}.
 */
public class BackgroundExecutor {
	private static final Logger LOG = LoggerFactory.getLogger(BackgroundExecutor.class);

	private final MainWindow mainWindow;
	private final ProgressPanel progressPane;

	private ThreadPoolExecutor taskQueueExecutor;
	private final Map<Long, IBackgroundTask> taskRunning = new ConcurrentHashMap<>();
	private final AtomicLong idSupplier = new AtomicLong(0);

	public BackgroundExecutor(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.progressPane = mainWindow.getProgressPane();
		reset();
	}

	public synchronized Future<TaskStatus> execute(IBackgroundTask task) {
		long id = idSupplier.incrementAndGet();
		TaskWorker taskWorker = new TaskWorker(id, task);
		taskRunning.put(id, task);
		taskQueueExecutor.execute(() -> {
			taskWorker.init();
			taskWorker.run();
		});
		return taskWorker;
	}

	public TaskStatus executeAndWait(IBackgroundTask task) {
		try {
			return execute(task).get();
		} catch (Exception e) {
			throw new JadxRuntimeException("Task execution error", e);
		}
	}

	public synchronized void cancelAll() {
		try {
			taskRunning.values().forEach(Cancelable::cancel);
			taskQueueExecutor.shutdown();
			boolean complete = taskQueueExecutor.awaitTermination(5, TimeUnit.SECONDS);
			LOG.debug("Background task executor terminated with status: {}", complete ? "complete" : "interrupted");
		} catch (Exception e) {
			LOG.error("Error terminating task executor", e);
		} finally {
			reset();
		}
	}

	public void execute(String title, List<Runnable> backgroundJobs, Consumer<TaskStatus> onFinishUiRunnable) {
		execute(new SimpleTask(title, backgroundJobs, onFinishUiRunnable));
	}

	public void execute(String title, Runnable backgroundRunnable, Consumer<TaskStatus> onFinishUiRunnable) {
		execute(new SimpleTask(title, Collections.singletonList(backgroundRunnable), onFinishUiRunnable));
	}

	public Future<TaskStatus> execute(String title, Runnable backgroundRunnable) {
		return execute(new SimpleTask(title, Collections.singletonList(backgroundRunnable)));
	}

	private synchronized void reset() {
		taskQueueExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
		taskRunning.clear();
		idSupplier.set(0);
	}

	private void taskComplete(long id) {
		taskRunning.remove(id);
	}

	private final class TaskWorker extends SwingWorker<TaskStatus, Void> implements ITaskInfo {
		private final long id;
		private final IBackgroundTask task;
		private ThreadPoolExecutor executor;
		private TaskStatus status = TaskStatus.WAIT;
		private long jobsCount;
		private long jobsComplete;
		private long time;

		public TaskWorker(long id, IBackgroundTask task) {
			this.id = id;
			this.task = task;
		}

		public void init() {
			addPropertyChangeListener(progressPane);
			SwingUtilities.invokeLater(() -> {
				progressPane.reset();
				if (task.getTaskProgress() != null) {
					progressPane.setIndeterminate(false);
				}
			});
		}

		@Override
		protected TaskStatus doInBackground() throws Exception {
			progressPane.changeLabel(this, task.getTitle() + "… ");
			progressPane.changeCancelBtnVisible(this, task.canBeCanceled());
			try {
				runJobs();
			} finally {
				task.onDone(this);
				taskComplete(id);
			}
			return status;
		}

		private void runJobs() throws InterruptedException {
			List<? extends Runnable> jobs = task.scheduleJobs();
			jobsCount = jobs.size();
			LOG.debug("Starting background task '{}', jobs count: {}, time limit: {} ms, memory check: {}",
					task.getTitle(), jobsCount, task.timeLimit(), task.checkMemoryUsage());
			if (jobsCount != 1) {
				progressPane.changeVisibility(this, true);
			}
			status = TaskStatus.STARTED;
			int threadsCount = mainWindow.getSettings().getThreadsCount();
			executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
			for (Runnable job : jobs) {
				executor.execute(job);
			}
			executor.shutdown();
			long startTime = System.currentTimeMillis();
			status = waitTermination(executor, buildCancelCheck(startTime));
			time = System.currentTimeMillis() - startTime;
			jobsComplete = executor.getCompletedTaskCount();
		}

		@SuppressWarnings("BusyWait")
		private TaskStatus waitTermination(ThreadPoolExecutor executor, Supplier<TaskStatus> cancelCheck) throws InterruptedException {
			try {
				int k = 0;
				while (true) {
					if (executor.isTerminated()) {
						return TaskStatus.COMPLETE;
					}
					TaskStatus cancelStatus = cancelCheck.get();
					if (cancelStatus != null) {
						performCancel(executor);
						return cancelStatus;
					}
					updateProgress(executor);
					k++;
					Thread.sleep(k < 20 ? 100 : 1000); // faster update for short tasks
					if (jobsCount == 1 && k == 3) {
						// small delay before show progress to reduce blinking on short tasks
						progressPane.changeVisibility(this, true);
					}
				}
			} catch (InterruptedException e) {
				LOG.debug("Task wait interrupted");
				performCancel(executor);
				return TaskStatus.CANCEL_BY_USER;
			} catch (Exception e) {
				LOG.error("Task wait aborted by exception", e);
				performCancel(executor);
				return TaskStatus.ERROR;
			}
		}

		private void updateProgress(ThreadPoolExecutor executor) {
			Consumer<ITaskProgress> onProgressListener = task.getOnProgressListener();
			ITaskProgress taskProgress = task.getTaskProgress();
			if (taskProgress == null) {
				taskProgress = new TaskProgress(executor.getCompletedTaskCount(), jobsCount);
			}
			setProgress(calcProgress(taskProgress));
			if (onProgressListener != null) {
				onProgressListener.accept(taskProgress);
			}
		}

		private void performCancel(ThreadPoolExecutor executor) throws InterruptedException {
			progressPane.changeLabel(this, task.getTitle() + " (" + NLS.str("progress.canceling") + ")… ");
			progressPane.changeIndeterminate(this, true);
			// force termination
			task.cancel();
			executor.shutdownNow();
			boolean complete = executor.awaitTermination(5, TimeUnit.SECONDS);
			LOG.debug("Task cancel complete: {}", complete);
		}

		private Supplier<TaskStatus> buildCancelCheck(long startTime) {
			long waitUntilTime = task.timeLimit() == 0 ? 0 : startTime + task.timeLimit();
			boolean checkMemoryUsage = task.checkMemoryUsage();
			return () -> {
				if (task.isCanceled()) {
					return TaskStatus.CANCEL_BY_USER;
				}
				if (waitUntilTime != 0 && waitUntilTime < System.currentTimeMillis()) {
					LOG.error("Task '{}' execution timeout, force cancel", task.getTitle());
					return TaskStatus.CANCEL_BY_TIMEOUT;
				}
				if (isCancelled() || Thread.currentThread().isInterrupted()) {
					LOG.warn("Task '{}' canceled", task.getTitle());
					return TaskStatus.CANCEL_BY_USER;
				}
				if (checkMemoryUsage && !UiUtils.isFreeMemoryAvailable()) {
					LOG.info("Memory usage: {}", UiUtils.memoryInfo());
					if (executor.getCorePoolSize() == 1) {
						LOG.error("Task '{}' memory limit reached, force cancel", task.getTitle());
						return TaskStatus.CANCEL_BY_MEMORY;
					}
					LOG.warn("Low memory, reduce processing threads count to 1");
					// reduce thread count and continue
					executor.setCorePoolSize(1);
					System.gc();
					UiUtils.sleep(1000); // wait GC
					if (!UiUtils.isFreeMemoryAvailable()) {
						LOG.error("Task '{}' memory limit reached (after GC), force cancel", task.getTitle());
						return TaskStatus.CANCEL_BY_MEMORY;
					}
				}
				return null;
			};
		}

		@Override
		protected void done() {
			progressPane.setVisible(false);
			task.onFinish(this);
		}

		@Override
		public TaskStatus getStatus() {
			return status;
		}

		@Override
		public long getJobsCount() {
			return jobsCount;
		}

		@Override
		public long getJobsComplete() {
			return jobsComplete;
		}

		@Override
		public long getJobsSkipped() {
			return jobsCount - jobsComplete;
		}

		@Override
		public long getTime() {
			return time;
		}
	}
}
