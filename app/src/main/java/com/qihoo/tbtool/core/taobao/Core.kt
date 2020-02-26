package com.qihoo.tbtool.core.taobao

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.instacart.library.truetime.TrueTimeRx
import com.qihoo.tbtool.core.taobao.event.ClickBuyEvent
import com.qihoo.tbtool.core.taobao.event.OrderChooseEvent
import com.qihoo.tbtool.core.taobao.event.SubmitOrderEvent
import com.qihoo.tbtool.core.taobao.view.ChooseTime
import com.qihoo.tbtool.expansion.l
import com.qihoo.tbtool.expansion.mainScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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

    /**
     * 开始定时抢购
     */
    fun statTimeGo(chooseTime: ChooseTime, activity: Activity) {
        val context = activity.applicationContext
        val intent = activity.intent.clone() as Intent
        val itemId = intent.getStringExtra("item_id") ?: ""

        var job = GlobalScope.launch {
            while (true) {
                val currentTimeMillis =
                    if (chooseTime.useTrueTime) TrueTimeRx.now().time else System.currentTimeMillis()
                val time = chooseTime.time() - currentTimeMillis

                l("执行倒计时: $time")

                if (chooseTime.isGrabCrazyMode){
                    val file = File(
                        activity.filesDir,
                        "crazy.txt"
                    )
                    if (!file.exists()){
                        if(file.createNewFile()) {
                            val fo: OutputStream = FileOutputStream(file)
                            with(fo) {
                                write("1".toByteArray())
                                close()
                            }
                        }
                    }
                }else{
                    val file = File(
                        activity.filesDir,
                        "crazy.txt"
                    )
                    if (file.exists())
                        file.delete()
                }

                if (time <= 0) {
                    intent.putExtra(TbDetailActivityHook.IS_GRAB_CRAZY, chooseTime.isGrabCrazyMode)
                    intent.putExtra(TbDetailActivityHook.CRAZY_INTERVAL, intArrayOf(
                        chooseTime.crazyInterval.t1, chooseTime.crazyInterval.t2)
                    )
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

}