package com.qihoo.tbtool.core.taobao

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.instacart.library.truetime.TrueTimeRx
import com.mm.red.expansion.showHintDialog
import com.qihoo.tbtool.core.taobao.event.ClickBuyEvent
import com.qihoo.tbtool.core.taobao.event.OrderChooseEvent
import com.qihoo.tbtool.core.taobao.event.SubmitOrderEvent
import com.qihoo.tbtool.core.taobao.view.ChooseTime
import com.qihoo.tbtool.expansion.l
import com.qihoo.tbtool.expansion.mainScope
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.min


object Core {

    /**
     * 开始秒杀
     */
    fun startGo(context: Context, intent: Intent) {
        val intent = intent.clone() as Intent
        intent.putExtra(TbDetailActivityHook.IS_KILL, true)
        intent.putExtra(TbDetailActivityHook.IS_KILL_GO, false)
        intent.putExtra(TbDetailActivityHook.IS_INJECT, false)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }


    /**
     * 检测是否可以抢购了
     */
    fun checkBuy(activity: Activity) {
        // 执行点击购买逻辑
        ClickBuyEvent.execute(activity)
    }


    /**
     * 选择订单详情
     */
    fun orderChoose(dialog: Dialog) {
        OrderChooseEvent.execute(dialog)
    }


    /**
     * 提交订单
     */
    fun submitOrder(activity: Activity) {
        SubmitOrderEvent.execute(activity)
    }

    fun addScheledJob(chooseTime: ChooseTime, activity: Activity) {
        val context = activity.applicationContext
        val intent = activity.intent.clone() as Intent
        val itemId = intent.getStringExtra("item_id") ?: ""

        var job = GlobalScope.launch {
            while (true) {
                val currentTimeMillis =
                    if (chooseTime.useTrueTime) TrueTimeRx.now().time else System.currentTimeMillis()
                val time = chooseTime.time() - currentTimeMillis

                l("执行倒计时: $time")

                if (time <= 0) {
                    startGo(context, intent)
                    JobManagers.removeJob(itemId)
                    break
                }

                mainScope.launch {
                    Toast.makeText(activity, "剩余:" + time / 1000.0 + ( if (chooseTime.useTrueTime) "s" else "秒"), Toast.LENGTH_SHORT)
                        .show()
                }
                delay(min(1000L, time))
            }
        }
        JobManagers.addJob(itemId, job)
    }

    /**
     * 开始定时抢购
     */
    fun statTimeGo(chooseTime: ChooseTime, activity: Activity) {
        val context = activity.applicationContext
        val intent = activity.intent.clone() as Intent
        val itemId = intent.getStringExtra("item_id") ?: ""

        if (chooseTime.useTrueTime) {
            // 启动TrueTime
            TrueTimeRx.build()
                .initializeRx("ntp.aliyun.com")
                .subscribeOn(Schedulers.io())
                .subscribe(
                    object : DisposableSingleObserver<Date>() {
                        override fun onSuccess(date: Date) { // work with the resulting todos...
                            mainScope.launch {
                                Toast.makeText(
                                    activity,
                                    "TrueTime was initialized and we have a time from ntp.aliyun.com: $date",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addScheledJob(chooseTime, activity)
                            dispose()
                        }
                        override fun onError(throwable: Throwable) { // handle the error case...
                            throwable.printStackTrace()
                            mainScope.launch {
                                Toast.makeText(
                                    activity,
                                    "TrueTime was initialized from ntp.aliyun.com failed, use system time",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            chooseTime.useTrueTime = false
                            addScheledJob(chooseTime, activity)
                            dispose()
                        }
                    }
                )
        } else {
            addScheledJob(chooseTime, activity)
        }
    }

}