package org.imogene.android.sync;

import org.imogene.android.notification.SynchronizationNotification;
import org.imogene.android.preference.PreferenceHelper;
import org.imogene.android.template.R;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

public class SynchronizationService extends Service {

	private static final String TAG = SynchronizationService.class.getName();

	private static final String ACTION_CHECK = "org.imogene.android.action.CHECK";
	private static final String ACTION_RESCHEDULE = "org.imogene.android.action.RESCHEDULE";
	private static final String ACTION_CANCEL = "org.imogene.android.action.CANCEL";

	/** Time between watchdog checks; in milliseconds */
	private static final long WATCHDOG_DELAY = 10 * 60 * 1000; // 10 minutes

	public static void actionReschedule(Context context) {
		Intent i = new Intent(context, SynchronizationService.class);
		i.setAction(ACTION_RESCHEDULE);
		context.startService(i);
	}

	public static void actionCancel(Context context) {
		Intent i = new Intent(context, SynchronizationService.class);
		i.setAction(ACTION_CANCEL);
		context.startService(i);
	}

	public static void actionCheck(Context context) {
		Intent i = new Intent(context, SynchronizationService.class);
		i.setAction(ACTION_CHECK);
		context.startService(i);
	}

	private SynchronizationController mController;
	private SynchronizationNotification mNotifier;
	private ControllerCallback mControllerCallback = new ControllerCallback();
	private Context mContext;

	@Override
	public int onStartCommand(Intent intent, int flags, final int startId) {

		mController = SynchronizationController.getInstance(this);
		mController.addResultCallback(mControllerCallback);
		mNotifier = SynchronizationNotification.getInstance(this);
		mContext = this;

		String action = intent.getAction();

		final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		if (ACTION_CHECK.equals(action)) {
			new Thread() {
				@Override
				public void run() {
					setWatchdog(alarmManager);

					mController.synchronize();

					if (PreferenceHelper.getSynchronizationStatus(mContext)) {
						reschedule(alarmManager);
					} else {
						// Cancel watchdog
						cancel(alarmManager);
					}

					stopSelf(startId);
				};
			}.start();
		} else if (ACTION_RESCHEDULE.equals(action)) {
			reschedule(alarmManager);
			stopSelf(startId);
		} else if (ACTION_CANCEL.equals(action)) {
			cancel(alarmManager);
			stopSelf(startId);
		}

		// Returning START_NOT_STICKY means that if a mail check is killed (e.g. due to memory
		// pressure, there will be no explicit restart. This is OK; Note that we set a watchdog
		// alarm before each mailbox check. If the mailbox check never completes, the watchdog
		// will fire and get things running again.
		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mController.removeResultCallback(mControllerCallback);
	}

	private void cancel(AlarmManager alarmMgr) {
		Log.i(TAG, "*** SynchronizationService: cancel");
		PendingIntent pi = createAlarmIntent();
		alarmMgr.cancel(pi);
	}

	private void reschedule(AlarmManager alarmMgr) {
		Log.i(TAG, "*** SynchronizationService: reschedule");
		PendingIntent pi = createAlarmIntent();
		long period = PreferenceHelper.getSynchronizationPeriod(this);
		long timeNow = SystemClock.elapsedRealtime();
		long nextCheckTime = timeNow + period * 1000;
		alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextCheckTime, pi);
	}

	/**
	 * Create a watchdog alarm and set it. This is used in case a mail check fails (e.g. we are killed by the system due
	 * to memory pressure.) Normally, a mail check will complete and the watchdog will be replaced by the call to
	 * reschedule().
	 * 
	 * @param accountId the account we were trying to check
	 * @param alarmMgr system alarm manager
	 */
	private void setWatchdog(AlarmManager alarmMgr) {
		PendingIntent pi = createAlarmIntent();
		long timeNow = SystemClock.elapsedRealtime();
		long nextCheckTime = timeNow + WATCHDOG_DELAY;
		alarmMgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextCheckTime, pi);
	}

	private PendingIntent createAlarmIntent() {
		Intent intent = new Intent(this, SynchronizationService.class);
		intent.setAction(ACTION_CHECK);
		return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	class ControllerCallback extends SynchronizationController.Callback {
		@Override
		public void onStart() {
			Log.i(TAG, "Starting");
			mNotifier.notifyState(getString(R.string.ig_sync_start));
		}

		@Override
		public void onInit() {
			Log.i(TAG, "Initializing");
			mNotifier.notifyState(getString(R.string.ig_sync_init));
		}

		@Override
		public void onInitResume() {
			Log.i(TAG, "Initializing a resumed session");
			mNotifier.notifyState(getString(R.string.ig_sync_init_resume));
		}

		@Override
		public void onSend() {
			Log.i(TAG, "Sending");
			mNotifier.notifyState(getString(R.string.ig_sync_send));
		}

		@Override
		public void onSendResume() {
			Log.i(TAG, "Sending from a resumed session");
			mNotifier.notifyState(getString(R.string.ig_sync_send_resume));
		}

		@Override
		public void onSent(int number) {
		}

		@Override
		public void onReceive() {
			Log.i(TAG, "Receiving");
			mNotifier.notifyState(getString(R.string.ig_sync_receive));
		}

		@Override
		public void onReceiveResume() {
			Log.i(TAG, "Receiving from a resumed session");
			mNotifier.notifyState(getString(R.string.ig_sync_receive_resume));
		}

		@Override
		public void onReceived(int number) {
		}

		@Override
		public void onClose() {
			Log.i(TAG, "Closing");
			mNotifier.notifyState(getString(R.string.ig_sync_close));
		}

		@Override
		public void onCloseResume() {
			Log.i(TAG, "Closing a resumed session");
			mNotifier.notifyState(getString(R.string.ig_sync_close_resume));
		}

		@Override
		public void onFinish() {
			Log.i(TAG, "Synchronization finished");
			mNotifier.cancel();
		}

		@Override
		public void onFailure(int code) {
		}
	}

}
