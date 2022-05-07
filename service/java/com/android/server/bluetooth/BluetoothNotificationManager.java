/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.bluetooth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification manager for Bluetooth. All notification will be sent to the current user.
 */
public class BluetoothNotificationManager {
    private static final String TAG = "BluetoothNotificationManager";
    private static final String NOTIFICATION_TAG = "com.android.bluetooth";
    public static final String APM_NOTIFICATION_CHANNEL = "apm_notification_channel";
    private static final String APM_NOTIFICATION_GROUP = "apm_notification_group";

    //TODO: b/239983569 remove hardcoded notification ID
    private static final int NOTE_BT_APM_NOTIFICATION = -1000007;

    private final Context mContext;
    private NotificationManager mNotificationManager;

    private boolean mInitialized = false;

    /**
     * Constructor
     *
     * @param ctx The context to use to obtain access to the Notification Service
     */
    BluetoothNotificationManager(Context ctx) {
        mContext = ctx;
    }

    private NotificationManager getNotificationManagerForCurrentUser() {
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.CURRENT).getSystemService(NotificationManager.class);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get NotificationManager for current user: " + e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        return null;
    }

    /**
     * Update to the notification manager fot current user and create notification channels.
     */
    public void createNotificationChannels() {
        if (mNotificationManager != null) {
            // Cancel all active notification from Bluetooth Stack.
            cleanAllBtNotification();
        }
        mNotificationManager = getNotificationManagerForCurrentUser();
        if (mNotificationManager == null) {
            return;
        }
        List<NotificationChannel> channelsList = new ArrayList<>();

        final NotificationChannel apmChannel = new NotificationChannel(
                APM_NOTIFICATION_CHANNEL,
                APM_NOTIFICATION_GROUP,
                NotificationManager.IMPORTANCE_HIGH);
        channelsList.add(apmChannel);

        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            mNotificationManager.createNotificationChannels(channelsList);
        } catch (Exception e) {
            Log.e(TAG, "Error Message: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    private void cleanAllBtNotification() {
        for (StatusBarNotification notification : getActiveNotifications()) {
            if (NOTIFICATION_TAG.equals(notification.getTag())) {
                cancel(notification.getId());
            }
        }
    }

    /**
     * Send notification to the current user.
     */
    public void notify(int id, Notification notification) {
        if (!mInitialized) {
            createNotificationChannels();
            mInitialized = true;
        }
        if (mNotificationManager == null) {
            return;
        }
        mNotificationManager.notify(NOTIFICATION_TAG, id, notification);
    }

    /**
     * Build and send the APM notification.
     */
    public void sendApmNotification(String title, String message) {
        if (!mInitialized) {
            createNotificationChannels();
            mInitialized = true;
        }
        Notification notification =  new Notification.Builder(mContext, APM_NOTIFICATION_CHANNEL)
                        .setAutoCancel(true)
                        .setLocalOnly(true)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new Notification.BigTextStyle().bigText(message))
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .build();
        notify(NOTE_BT_APM_NOTIFICATION, notification);
    }

    /**
     * Cancel the notification fot current user.
     */
    public void cancel(int id) {
        if (mNotificationManager == null) {
            return;
        }
        mNotificationManager.cancel(NOTIFICATION_TAG, id);
    }

    /**
     * Get active notifications for current user.
     */
    public StatusBarNotification[] getActiveNotifications() {
        if (mNotificationManager == null) {
            return new StatusBarNotification[0];
        }
        return mNotificationManager.getActiveNotifications();
    }
}
