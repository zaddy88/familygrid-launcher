package dev.fluxnova.familygrid;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;

/**
 * LOGIC TIER: "Quiet mode". When switched on, hides notifications from apps the family
 * didn't allow: anything not on a home-screen tile, not essential (calls, messages,
 * alarms, system) and not on the extra allow-list. Only the sending app's package and
 * the notification category are checked; titles and text are never read or stored.
 */
public class QuietService extends NotificationListenerService {
    private Set<String> allowed;
    private long allowedStamp = -1;

    /** true when the user has granted notification access to this service. */
    static boolean hasAccess(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (list == null) return false;
        ComponentName me = new ComponentName(c, QuietService.class);
        for (String s : list.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    @Override public void onListenerConnected() {
        sweep(); // tidy what is already in the shade
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (shouldHide(sbn)) cancel(sbn);
    }

    /** Re-checks everything currently shown, e.g. right after Quiet mode is switched on. */
    private void sweep() {
        if (!HubConfig.quietMode(this)) return;
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all == null) return;
            for (StatusBarNotification sbn : all) if (shouldHide(sbn)) cancel(sbn);
        } catch (RuntimeException ignored) {}
    }

    private boolean shouldHide(StatusBarNotification sbn) {
        if (!HubConfig.quietMode(this)) return false;
        if (sbn.isOngoing() || !sbn.isClearable()) return false; // music, navigation, calls in progress
        Notification n = sbn.getNotification();
        if (n != null && (Notification.CATEGORY_CALL.equals(n.category)
                || Notification.CATEGORY_ALARM.equals(n.category)
                || Notification.CATEGORY_MISSED_CALL.equals(n.category))) {
            return false;
        }
        return !allowedApps().contains(sbn.getPackageName());
    }

    private Set<String> allowedApps() {
        long stamp = HubConfig.settingsStamp(this);
        if (allowed == null || stamp != allowedStamp) {
            allowed = HubActions.quietAllowList(this);
            allowedStamp = stamp;
        }
        return allowed;
    }

    private void cancel(StatusBarNotification sbn) {
        try { cancelNotification(sbn.getKey()); } catch (RuntimeException ignored) {}
    }
}
