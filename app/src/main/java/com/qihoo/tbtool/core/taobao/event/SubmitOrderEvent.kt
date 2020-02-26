package com.qihoo.tbtool.core.taobao.event

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.qihoo.tbtool.expansion.l
import com.qihoo.tbtool.expansion.mainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object SubmitOrderEvent : BaseEvent() {
    fun execute(activity: Activity) = mainScope.launch {
        // 循环检测页面是否加载完毕
        withContext(Dispatchers.Default) {
            while (true) {
                val resId = activity.resources.getIdentifier(
                    "purchase_bottom_layout",
                    "id",
                    activity.packageName
                )
                val group = activity.findViewById<View>(resId) as ViewGroup
                if (group != null && group.childCount != 0) {
                    break
                }
            }
        }

        // 获取提交按钮
        val submitBtn = getSubmitBtn(activity) ?: return@launch

        l("找到提交按钮:$submitBtn  ${submitBtn.text}")
        submitBtn.performClick()

        notifyUser(activity)

    }

    private fun notifyUser(activity: Activity){
        // 提交了，快来付款吧
        val channelId: String = "791bfe58-583e-11ea-b587-633d566f5b8b"

        //Define sound URI
        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(activity)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("抢购成功")
            .setContentText("抢购成功快来付款吧")
            .setSound(soundUri)
            .setAutoCancel(true)

        val notificationManager =
            activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_HIGH
            )
            val att = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            channel.setSound(soundUri, att)
            notificationManager?.createNotificationChannel(channel)
            notificationBuilder.setChannelId(channelId)
        }

        notificationManager?.notify(0 /* ID of notification */, notificationBuilder.build())
    }


    private fun getSubmitBtn(activity: Activity): TextView? {
        val resId =
            activity.resources.getIdentifier("purchase_bottom_layout", "id", activity.packageName)
        val group = activity.findViewById<View>(resId) as ViewGroup

        return group?.let {
            return findSubmitBtn(it)
        }
    }

    /**
     * 递归查找 提交按钮
     */
    private fun findSubmitBtn(group: ViewGroup): TextView? {
        for (v in 0..group.childCount) {
            val child = group.getChildAt(v)
            if (child is ViewGroup) {
                val submitBtn = findSubmitBtn(child)
                if (submitBtn != null) {
                    return submitBtn
                }
            } else {
                if (child is TextView) {
                    if ("提交订单" == child.text) {
                        return child
                    }
                }
            }
        }
        return null
    }


}