package com.ubuntuone.android.files.util;

import greendroid.widget.QuickAction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.text.ClipboardManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import com.ubuntuone.android.files.R;

public final class UIUtil
{
	private UIUtil() {
	}
	
	public static void showToast(Context context, String text, boolean isLong) {
		Toast.makeText(context, text,
				isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
	}
	
	public static void showToast(Context context, int resId, boolean isLong) {
		Toast.makeText(context, resId,
				isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
	}
	
	public static void showToast(Context context, String text) {
		showToast(context, text, false);
	}
	
	public static void showToast(Context context, int resId) {
		showToast(context, resId, false);
	}

	public static String formatTime(long milliseconds) {
		int s = (int) milliseconds / 1000;
		int seconds = (int) (s % 60);
		int minutes = (int) ((s / 60) % 60);
		int hours   = (int) ((s / 60*60) % 24);
		return String.format("%02d:%02d:%02d", hours, minutes, seconds); 
	}

	private static ClipboardManager clipboardManager;
	
	public static void save2Clipboard(Context context, String text) {
		if (clipboardManager == null)
			clipboardManager = (ClipboardManager) context
					.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(text);
	}
	
	public static class BlackQuickAction extends QuickAction {
		
		private static final ColorFilter BLACK_CF =
				new LightingColorFilter(Color.BLACK, Color.BLACK);

		public BlackQuickAction(Context ctx, int drawableId, int titleId) {
			super(ctx, buildDrawable(ctx, drawableId), titleId);
		}
		
		private static Drawable buildDrawable(Context ctx, int drawableId) {
			Drawable d = ctx.getResources().getDrawable(drawableId);
			d.setColorFilter(BLACK_CF);
			return d;
		}
		
	}
	
	public static Intent createConcreteClassShareIntent(Activity activity,
			String packageName, String className, Intent intent) {
		final Intent concreteIntent = new Intent(Intent.ACTION_SEND);
		concreteIntent.setClassName(packageName, className);
		concreteIntent.setType(intent.getType());
		concreteIntent.putExtras(intent.getExtras());
		return concreteIntent;
	}
	
	public static ArrayList<Intent> resolveSpecificUrlSharingApps(
			Activity activity, PackageManager pm, Intent intent) {
		final ArrayList<Intent> specifics = new ArrayList<Intent>();
		
		final String[] topApps = new String[] {
				"Facebook", "Twitter", "Google+", "Gmail", "TweetDeck"
		};
		
		final List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
		for (int i = 0; i < topApps.length; i++) {
			final String topAppName = topApps[i];
			
			Iterator<ResolveInfo> it = activities.iterator();
			while (it.hasNext()) {
				final ResolveInfo info = it.next();
				final String foundAppName = info.loadLabel(pm).toString();
				if (foundAppName.toLowerCase().equals(topAppName.toLowerCase())) {
					String packageName =
							info.activityInfo.applicationInfo.packageName;
					String className =
							info.activityInfo.name;
					specifics.add(UIUtil.createConcreteClassShareIntent(activity,
							packageName, className, intent));
				}
			}
		}
		
		return specifics;
	}
	
	public static String[] resolveAppLabels(Activity activity,
			PackageManager pm, List<ResolveInfo> resolvedInfos) {
		final String[] appLabels = new String[1 + resolvedInfos.size()];
		appLabels[0] = "Copy to clipboard";
		final Iterator<ResolveInfo> it = resolvedInfos.iterator();
		for (int i = 0; it.hasNext(); i++) {
			final ResolveInfo info = it.next();
			appLabels[1 + i] = info.loadLabel(pm).toString();
		}
		return appLabels;
	}

	public static AlertDialog createIntentChooser(final Activity activity,
			String[] appLabels, final List<ResolveInfo> resolved,
			final Intent intent, final boolean isReferral) {
		final PackageManager pm = activity.getPackageManager();
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
				R.layout.list_chooser_row, R.id.text, appLabels) {
					@Override
					public View getView(int position, View convertView,
							ViewGroup parent) {
						View view = super.getView(position, convertView, parent);
						ImageView image = ((ImageView) view.findViewById(R.id.image));
						if (position == 0) {
							image.setImageResource(R.drawable.ic_menu_copy);
						} else {
							Drawable left = resolved.get(position-1).loadIcon(pm);
							image.setImageDrawable(left);
						}
						return view;
					}
		};
		
		final OnClickListener listener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String url = intent.getStringExtra(Intent.EXTRA_TEXT);
				int position = which;
				if (position == 0) {
					UIUtil.save2Clipboard(activity, url);
					UIUtil.showToast(activity, R.string.toast_file_link_copied);
				} else {
					ResolveInfo info = resolved.get(position - 1);
					
					if (isReferral) {
						final String appName = info.loadLabel(pm).toString();
						Log.i("ubuntuone", "Share using " + appName);
						// Facebook and Google Plus won't accept content other than a URL.
						if (appName.toLowerCase().contains("twitter")) {
							customizeForTwitter(activity, intent, url);
						} else if (appName.toLowerCase().contains("mail")) {
							customizeForGmail(activity, intent, url);
						}
					}
					
					String packageName =
							info.activityInfo.applicationInfo.packageName;
					String className =
							info.activityInfo.name;
					Intent concreteIntent = createConcreteClassShareIntent(activity,
							packageName, className, intent);
					concreteIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
					
					// Log to debug problems on 2.2.1
					{
						String text = intent.getStringExtra(Intent.EXTRA_TEXT);
						Log.i("ubuntuone", String.format("Sharing: '%s'", text));
					}
					activity.startActivity(concreteIntent);
				}
			}
		};

		final AlertDialog dialog = new AlertDialog.Builder(activity)
				.setTitle(R.string.dialog_share_link_title)
				.setAdapter(adapter, listener)
				.create();
		return dialog;
	}
	
	private static void customizeForTwitter(
			Context context, Intent intent, String url) {
		customizeShareIntent(context, intent, "text/plain", url,
				R.string.invite_friend_subject,
				R.string.invite_friend_twitter_body_fmt);
	}
	
	private static void customizeForGmail(
			Context context, Intent intent, String url) {
		customizeShareIntent(context, intent, "message/rfc822", url,
				R.string.invite_friend_subject,
				R.string.invite_friend_body_fmt);
	}
	
	private static void customizeShareIntent(Context context, Intent intent,
			String type, String url, int subjectResId, int bodyFmtResId) {
		String subject = context.getString(subjectResId);
		String body = context.getString(bodyFmtResId, url);
		
		intent.setType(type);
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, body);
	}
	
	public static void sleepSilently(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			// Ignore.
		}
	}
	
	public static void shareLink(Activity activity, String url) {
		shareLink(activity, url, false);
	}
	
	public static void shareLink(Activity activity, String url, boolean isReferral) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(
				R.string.shared_from_u1));
		intent.putExtra(Intent.EXTRA_TEXT, url);
		
		final PackageManager pm = activity.getPackageManager();
		
		// Resolve specific app intents.
		final ArrayList<Intent> specifics =
				UIUtil.resolveSpecificUrlSharingApps(activity, pm, intent);
		
		// Resolve list of all URL receivers, prioritizing intents from above.
		final List<ResolveInfo> resolved = pm.queryIntentActivityOptions(
				activity.getComponentName(), specifics.toArray(new Intent[] {}), intent, 0);
		
		// Resolve application labels from the resolved Activity list.
		final String[] appLabels = UIUtil.resolveAppLabels(activity, pm, resolved);

		if (resolved.size() > 0) {
			final AlertDialog dialog = UIUtil.createIntentChooser(
					activity, appLabels, resolved, intent, isReferral);
			
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					dialog.show();
				}
			});
		} else {
			UIUtil.showToast(activity, R.string.toast_no_suitable_activity);
		}
	}
}
