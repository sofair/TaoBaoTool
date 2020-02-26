package com.qihoo.tbtool.core.taobao.event

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.mm.red.expansion.showHintDialog
import com.qihoo.tbtool.core.taobao.Core
import com.qihoo.tbtool.core.taobao.TbDetailActivityHook
import com.qihoo.tbtool.expansion.l
import com.qihoo.tbtool.expansion.mainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Math.random

/**
 * 点击立即购买按钮
 */
object ClickBuyEvent : BaseEvent() {

    fun execute(activity: Activity) = mainScope.launch {
        try {
            // 循环判断界面是否加载完毕
            checkLoadCompleteById(
                activity,
                activity.window,
                "ll_bottom_bar_content"
            )

            // 判断是否已经抢完了
            val hintText = judgeSoldThe(activity)

            if (hintText.isBlank()) {
                // 未出售光,点击购买按钮
                clickBuyBtn(activity)
            } else {

                val is_crazy = activity.intent.getBooleanExtra(TbDetailActivityHook.IS_GRAB_CRAZY, false)
                if (is_crazy) {
                    val crazyInterval =
                        activity.intent.getIntArrayExtra(TbDetailActivityHook.CRAZY_INTERVAL)
                    crazyInterval?.let {
                        val lb = it[0]
                        val ub = if (it[1]>it[0]) it[1] else it[0]
                        val ci = lb + (ub-lb) * random()

                        if (ci > 10000) {
                            Toast.makeText(
                                activity,
                                "检测到物品:$hintText" + ", 等待" + ci / 1000.0 + "秒进行下次尝试",
                                Toast.LENGTH_LONG
                            ).show()

                            // 注入抢购按钮
                             TbDetailActivityHook.injectView(activity)
                        }

                        if (ci > 0)
                            delay(ci.toLong())

                        val file = File(
                            activity.filesDir,
                            "crazy.txt"
                        )
                        if (file.exists()) {
                            throw Exception()
                        }else{
                            Toast.makeText(
                                activity,
                                "疯狂模式终止",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                } else {
                    activity.showHintDialog("提示", "检测到物品:$hintText") {}
                }
            }


        } catch (e: Exception) {
            // 出错重新抢购
            Core.startGo(activity.applicationContext, activity.intent.clone() as Intent)
            activity.finish()
        }
    }


    /**
     * 判断物品是否已经寿光
     */
    private fun judgeSoldThe(activity: Activity): String {
        try {
            val resId = activity.resources.getIdentifier(
                "tv_hint",
                "id",
                activity.packageName
            )

            val tvHint: TextView = activity.findViewById(resId) ?: return ""
            l("tvHint:$tvHint  ${tvHint.text}")
            return tvHint.text.toString()
        } catch (e: Exception) {
        }
        return ""
    }


    private fun clickBuyBtn(activity: Activity) {
        l("开始执行购买")
        // 进入到这里可以确认界面加载完毕了
        val buy = getBuyButton(activity)
        if (buy != null) {
            l("买: " + buy.text + "  " + buy.isEnabled)
            // 获取到购买按钮,判断是否已经可以开始抢购了
            if (buy.isEnabled) {
                buy.performClick()
            } else {
                // 还没有开始抢购,重新检测
                Core.startGo(activity.applicationContext, activity.intent.clone() as Intent)
                activity.finish()
            }
        } else {
            // 获取按钮失败,重新开始
            Core.startGo(activity.applicationContext, activity.intent.clone() as Intent)
            activity.finish()
        }
    }


    /**
     * 根据布局层级
     * 获取立即购买的 Button 按钮
     */
    private fun getBuyButton(activity: Activity): TextView? {
        val resId = activity.resources.getIdentifier(
            "ll_bottom_bar_content",
            "id",
            activity.packageName
        )

        val bottomBarContent: ViewGroup? = activity.findViewById(resId)

        val group2: ViewGroup? = bottomBarContent?.getChildAt(4) as ViewGroup?

        val buyButton = group2?.getChildAt(0) as TextView?

        return buyButton
    }

}