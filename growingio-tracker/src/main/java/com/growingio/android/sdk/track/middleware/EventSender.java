/*
 * Copyright (C) 2020 Beijing Yishu Technology Co., Ltd.
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

package com.growingio.android.sdk.track.middleware;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.growingio.android.sdk.track.providers.MemoryStatusProvider;
import com.growingio.android.sdk.track.providers.NetworkStatusProvider;
import com.growingio.android.sdk.track.providers.SendPolicyProvider;
import com.growingio.android.sdk.track.utils.GIOProviders;
import com.growingio.android.sdk.track.utils.LogUtil;
import com.growingio.android.sdk.track.utils.ThreadUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 事件发送者
 * - 多个进程公用同一个EventSender
 * - EventSender为单线程模型
 */
class EventSender {
    private static final String TAG = "GIO.EventSender";

    private int mCacheEventNum = 0;
    private final DBSQLite mDbSQLite;

    private GIOSenderService mSenderService;
    private final SharedPreferences mSharedPreferences;

    EventSender(Context context, IEventSender sender) {
        mDbSQLite = new DBSQLite(context, sender);
        NetworkStatusProvider.NetworkStatus.get(context);
        MemoryStatusProvider.MemoryPolicy.get(context);
        GIOProviders.update(EventSender.class, this);
        mSharedPreferences = context.getSharedPreferences("growing_sender", Context.MODE_PRIVATE);
    }

    public void setSenderService(GIOSenderService senderService) {
        this.mSenderService = senderService;
    }

    /**
     * EventSaver通知EventSender已经记录了一个消息
     *
     * @param instant true -- 表示是一个即时消息
     */
    void onEventWrite(boolean instant) {
        mDbSQLite.updateStatics(1, 0, 0, 0, 0);
        if (instant) {
            sendEvents(true);
        } else {
            mCacheEventNum++;
            if (mCacheEventNum >= SendPolicyProvider.SendPolicy.get().bulkSize()) {
                LogUtil.d(TAG, "cacheEventNum >= 300, toggle one send action");
                sendEvents(false);
                mCacheEventNum = 0;
            }
        }
    }

    void delAllMsg() {
        LogUtil.d(TAG, "action: 清库");
        mDbSQLite.mDbHelper.delAllMsg();
    }

    void consumeBytes(int bytes) {
        if (NetworkStatusProvider.NetworkStatus.get().isMobileData()) {
            mDbSQLite.updateStatics(0, 0, bytes, 0, 0);
            todayBytes(bytes);
        }
    }

    /**
     * @param delta 变化量
     * @return 今日移动网络数据发送量
     */
    long todayBytes(int delta) {
        String dateKey = "today";
        String valueKey = "today_bytes";
        String todayStr = mSharedPreferences.getString(dateKey, "");

        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat dayFormat = new SimpleDateFormat("YYYYMMdd");
        String realDayTime = dayFormat.format(new Date());

        long oldValue;
        if (!realDayTime.equals(todayStr)) {
            // 新的一天， 重新计算
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString(dateKey, realDayTime);
            editor.putLong(valueKey, 0);
            editor.apply();
            oldValue = 0;
        } else {
            // 与记录数据是同一天
            oldValue = mSharedPreferences.getLong(valueKey, 0);
        }
        if (delta != 0) {
            long value = oldValue + delta;
            mSharedPreferences.edit().putLong(valueKey, value).apply();
            return value;
        }
        return oldValue;
    }

    /**
     * 在初始化后调用
     * - 用于清理过期数据
     * - 删除已发送数据文件
     * - copy清理无效的mapper文件, copy一次mapper文件
     * - 发送一次网络请求
     */
    void afterConstructor() {
        cleanInvalid();
        sendEvents(false);
    }

    void cleanInvalid() {
        mDbSQLite.cleanOldLog();
    }


    private void scheduleForNet(final boolean needWifi) {
        // 如果没有网络, 重新调度一个等待
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GIOSenderService senderService = mSenderService;
                if (senderService != null) {
                    senderService.scheduleForNet(needWifi);
                }
            }
        });
    }

    private void cancelScheduleForNet() {
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GIOSenderService senderService = mSenderService;
                if (senderService != null) {
                    senderService.cancelScheduleForNet();
                }
            }
        });
    }

    /**
     * 发送事件
     *
     * @param onlyInstant true -- 仅发送实时消息
     */
    void sendEvents(boolean onlyInstant) {
        NetworkStatusProvider networkStatusProvider = GIOProviders.provider(NetworkStatusProvider.class);

        networkStatusProvider.checkNetStatus();
        if (!networkStatusProvider.isConnected()) {
            scheduleForNet(false);
            return;
        }
        MemoryStatusProvider memoryStatusProvider = GIOProviders.provider(MemoryStatusProvider.class);
        memoryStatusProvider.check();

        Boolean scheduleForNet = null;
        int[] uploadEvents;

        if (onlyInstant) {
            uploadEvents = new int[]{GEvent.SEND_POLICY_INSTANT};
        } else if (networkStatusProvider.isWifi()) {
            uploadEvents = new int[]{
                    GEvent.SEND_POLICY_INSTANT, GEvent.SEND_POLICY_MOBILE_DATA, GEvent.SEND_POLICY_WIFI
            };
        } else {
            uploadEvents = new int[]{GEvent.SEND_POLICY_INSTANT, GEvent.SEND_POLICY_MOBILE_DATA};
        }
        for (int policy : uploadEvents) {
            int result;
            do {
                if (policy != GEvent.SEND_POLICY_INSTANT
                        && networkStatusProvider.isMobileData()
                        && SendPolicyProvider.SendPolicy.get().cellularDataLimit() < todayBytes(0)) {
                    LogUtil.d(TAG, "今日流量已耗尽");
                    break;
                }
                result = mDbSQLite.uploadEvent(policy, memoryStatusProvider.numOfMaxEventsPerRequest());
                if (result == DBSQLite.UPLOAD_FAILED
                        && (scheduleForNet == null || scheduleForNet)) {
                    scheduleForNet = policy == GEvent.SEND_POLICY_WIFI;
                }
            } while (result == DBSQLite.UPLOAD_SUCCESS);
        }
        if (!onlyInstant) {
            mDbSQLite.uploadStaticEvent();
        }
        if (scheduleForNet != null) {
            LogUtil.d(TAG, "upload event failed, and schedule for retry later");
            scheduleForNet(scheduleForNet);
        } else {
            cancelScheduleForNet();
        }
        mCacheEventNum = 0;
    }
}
