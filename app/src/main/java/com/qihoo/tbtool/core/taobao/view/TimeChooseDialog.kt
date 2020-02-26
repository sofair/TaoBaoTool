package com.qihoo.tbtool.core.taobao.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Gravity
import android.app.Dialog
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import com.instacart.library.truetime.TrueTimeRx
import com.mm.red.expansion.fillZero
import com.qihoo.tbtool.R
import com.qihoo.tbtool.core.taobao.Core
import com.qihoo.tbtool.core.taobao.view.wheelview.adapter.ArrayWheelAdapter
import com.qihoo.tbtool.core.taobao.view.wheelview.common.WheelConstants.WHEEL_TEXT_SIZE
import com.qihoo.tbtool.core.taobao.view.wheelview.widget.WheelView
import com.qihoo.tbtool.expansion.createMyScope
import com.qihoo.tbtool.expansion.l
import com.qihoo.tbtool.expansion.mainScope
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import java.util.*


data class TwoVal<T>  (
var t1: T,
var t2:T
){}

/**
 * 时间选择器保存对象
 */
data class ChooseTime(
    var timeType: String,
    var hour: Int,
    var minutes: Int,
    var second: Int,
    var millisecond: Int,
    var useTrueTime: Boolean,
    var isGrabCrazyMode: Boolean,
    var crazyInterval: TwoVal<Int>
) {
    /**
     * 转换时间
     */
    fun time(): Long {
        val c = Calendar.getInstance()
        if (timeType == MORNING) {
            c.set(Calendar.HOUR_OF_DAY, hour)
        } else {
            if (hour == 12) {
                // 12点就是 第二天 0 点
                c.add(Calendar.DATE, 1);
                c.set(Calendar.HOUR_OF_DAY, 0)
            } else {
                c.set(Calendar.HOUR_OF_DAY, hour + 12)
            }
        }
        c.set(Calendar.MINUTE, minutes)
        c.set(Calendar.SECOND, second)
        c.set(Calendar.MILLISECOND, millisecond)

        // 距离当前时间不超过12小时
        if( (System.currentTimeMillis() - c.timeInMillis) > 12 * 3600000){
            c.add(Calendar.DATE, -1)
        }
        return c.timeInMillis
    }

    override fun toString(): String {
        return ("ChooseTime(timeType='$timeType',"
                + " hour=$hour, minutes=$minutes, second=$second,"
                + " millisecond=$millisecond, useTrueTime=$useTrueTime)")
    }

}

val MORNING = "上午"
val AFTERNOON = "下午"

class TimeChooseDialog(
    context: Context
    ,
    val default: ChooseTime = ChooseTime(MORNING, 6, 30, 0, 0,
        useTrueTime = false,
        isGrabCrazyMode = false,
        crazyInterval = TwoVal(0,0)
    ),
    val timeConfirmListener: (ChooseTime) -> Unit
) : Dialog(context),
    CoroutineScope by createMyScope() {
    private companion object {


        val DAY_TYPE = listOf(MORNING, AFTERNOON)

        val HOURS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")

        val MINUTES = mutableListOf<String>().apply {
            repeat(60) {
                add(it.fillZero())
            }
        }
        val MILLISECONDS = mutableListOf<String>().apply {
            repeat(1000) {
                add(it.fillZero(3))
            }
        }
    }


    init {
        setContentView(buildView())
        window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(false)


        window!!.getDecorView().setPadding(0, 0, 0, 0)
        val lp = window!!.attributes
        lp.width = WindowManager.LayoutParams.MATCH_PARENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.BOTTOM
        window!!.attributes = lp

        initView()
    }

    /**
     * 早上还是下午
     */
    lateinit var wvDayType: WheelView<String>

    /**
     * 时辰
     */
    lateinit var wvHour: WheelView<String>
    /**
     * 分钟
     */
    lateinit var wvMinutes: WheelView<String>

    /**
     * 秒
     */
    lateinit var wvSecond: WheelView<String>

    /**
     * 豪秒
     */
    lateinit var wvMilliSecond: WheelView<String>

    /**
     *
     */
    // lateinit var wvUseTrueTime: ToggleButton

    /**
     * Crazy mode
     */
    lateinit var tbIsGrabCrazyMode: ToggleButton
    lateinit var etCrazyIntervalLow: EditText
    lateinit var etCrazyInterval: EditText
    lateinit var tvCrazyDash: TextView

    private fun buildView(): View {
        return context.UI {
            verticalLayout {
                linearLayout {
                    backgroundColor = Color.WHITE
                    orientation = LinearLayout.HORIZONTAL

                    textView("取消") {
                        textColor = Color.parseColor("#FFFF3B30")
                        textSize = 16f
                        padding = dip(10)
                        setOnClickListener {
                            dismiss()
                        }
                    }.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )



                    textView("设置定时秒杀时间") {
                        gravity = Gravity.CENTER
                        textColor = Color.parseColor("#FF333333")
                        textSize = 16f
                        setOnLongClickListener {
                            default.useTrueTime = !default.useTrueTime
                            Toast.makeText(
                                it.context,
                                "Trying to use" + (if (default.useTrueTime) "TrueTime" else "SystemTime") + " mode",
                                Toast.LENGTH_LONG
                            ).show()
                            if (default.useTrueTime) {
                                // reset to false
                                default.useTrueTime = false
                                // 启动TrueTime
                                TrueTimeRx.build()
                                    .initializeRx("ntp.aliyun.com")
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(
                                        object : DisposableSingleObserver<Date>() {
                                            override fun onSuccess(date: Date) { // work with the resulting todos...
                                                // always use system time
                                                default.useTrueTime = false
                                                val dt0 =
                                                    TrueTimeRx.now().time - System.currentTimeMillis()
                                                val dt1 =
                                                    System.currentTimeMillis() - TrueTimeRx.now().time
                                                val dt = (dt0 - dt1) / 2
                                                mainScope.launch {
                                                    Toast.makeText(
                                                        it.context,
                                                        "TrueTime was initialized and we have a time from ntp.aliyun.com: $date, difference from local systemtime is ($dt0, ${-dt1}, ${dt})ms",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                dispose()
                                            }

                                            override fun onError(throwable: Throwable) { // handle the error case...
                                                default.useTrueTime = false
                                                throwable.printStackTrace()
                                                mainScope.launch {
                                                    Toast.makeText(
                                                        it.context,
                                                        "TrueTime was initialized from ntp.aliyun.com failed, use system time",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                dispose()
                                            }
                                        }
                                    )
                            }

                            false
                        }

                    }.lparams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ) {
                        weight = 1.0f
                    }



                    textView("确定") {
                        gravity = Gravity.CENTER
                        textColor = Color.parseColor("#FF35C759")
                        textSize = 16f
                        padding = dip(10)

                        setOnClickListener {
                            confirm()
                        }
                    }.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )


                }.lparams(LinearLayout.LayoutParams.MATCH_PARENT, dip(44))

                linearLayout {
                    backgroundColor = Color.WHITE
                    orientation = LinearLayout.HORIZONTAL

                    textView("疯狂模式？") {
                        gravity = Gravity.CENTER
                        textColor = Color.parseColor("#FF35C759")
                        textSize = 16f
                    }.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )

                    tbIsGrabCrazyMode = toggleButton()
                    tbIsGrabCrazyMode.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )

                    tbIsGrabCrazyMode.setOnCheckedChangeListener { buttonView, isChecked ->
                        etCrazyInterval?.isEnabled = isChecked
                        tvCrazyDash?.isEnabled = isChecked
                        etCrazyIntervalLow?.isEnabled = isChecked
                    }

                    etCrazyIntervalLow = editText("0")
                    etCrazyIntervalLow.lparams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ) {
                        weight = 1.0f
                    }
                    etCrazyIntervalLow.inputType = InputType.TYPE_CLASS_NUMBER
                    etCrazyIntervalLow.isEnabled = false
                    etCrazyIntervalLow.hint = "小于等于0无间隔尝试!!!可能会导致手机卡死!!!"

                    tvCrazyDash = textView("-") {
                        gravity = Gravity.CENTER
                        textColor = Color.parseColor("#FF35C759")
                        textSize = 16f
                    }
                    tvCrazyDash.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )

                    etCrazyInterval = editText("0")
                    etCrazyInterval.lparams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ) {
                        weight = 1.0f
                    }
                    etCrazyInterval.inputType = InputType.TYPE_CLASS_NUMBER
                    etCrazyInterval.isEnabled = false
                    etCrazyInterval.hint = "小于等于0无间隔尝试!!!可能会导致手机卡死!!!"

                    textView("ms") {
                        gravity = Gravity.CENTER
                        textColor = Color.parseColor("#FF35C759")
                        textSize = 16f
                    }.lparams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )


                }.lparams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)

                linearLayout {
                    orientation = LinearLayout.HORIZONTAL

                    // 上午或下午
                    wvDayType = getWheelView(2.0f)
                    defaultWv(wvDayType, false, DAY_TYPE)
                    wvDayType.selection = DAY_TYPE.indexOf(default.timeType)
                    addView(wvDayType)

                    // 时
                    wvHour = getWheelView(1.0f)
                    defaultWv(wvHour, true, HOURS)
                    wvHour.selection = HOURS.indexOf(default.hour.toString())
                    addView(wvHour)

                    // 分
                    wvMinutes = getWheelView(1.0f)
                    defaultWv(wvMinutes, true, MINUTES)
                    wvMinutes.selection = MINUTES.indexOf(default.minutes.fillZero())
                    addView(wvMinutes)

                    // 秒
                    wvSecond = getWheelView(1.0f)
                    defaultWv(wvSecond, true, MINUTES)
                    wvSecond.selection = MINUTES.indexOf(default.second.fillZero())
                    addView(wvSecond)

                    // 豪秒
                    wvMilliSecond = getWheelView(1.0f)
                    defaultWv(wvMilliSecond, true, MILLISECONDS)
                    wvMilliSecond.selection = MILLISECONDS.indexOf(default.millisecond.fillZero(3))
                    addView(wvMilliSecond)


                }.lparams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            }


        }.view

    }

    private fun <T> getWheelView(weightN: Float): WheelView<T> {
        return WheelView<T>(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = weightN
                }
        }

    }


    /**
     * 初始化 wv
     */
    fun initView() {


    }

    fun <T> defaultWv(wv: WheelView<T>, loop: Boolean, data: List<T>) {
        wv.setWheelAdapter(ArrayWheelAdapter<T>(context))
        wv.skin = WheelView.Skin.Holo
        //
        wv.style = WheelView.WheelViewStyle().apply {
            backgroundColor = Color.parseColor("#F2F2F2")
            selectedTextColor = Color.parseColor("#FF333333")
            selectedTextSize = WHEEL_TEXT_SIZE + 4
            holoBorderColor = Color.parseColor("#DADFE0")

        }

        wv.setExtraText("", Color.parseColor("#0288ce"), 40, 70, true)
        wv.setWheelSize(5)
        wv.setLoop(loop)

        wv.setWheelData(data)
        wv.selection = data.size / 2
        wv.visibility = View.VISIBLE


    }


    private fun confirm() {
        default.timeType = wvDayType.selectionItem
        default.hour = wvHour.selectionItem.toInt()
        default.minutes = wvMinutes.selectionItem.toInt()
        default.second = wvSecond.selectionItem.toInt()
        default.millisecond = wvMilliSecond.selectionItem.toInt()
        // default.useTrueTime = wvUseTrueTime.isChecked
        default.isGrabCrazyMode = tbIsGrabCrazyMode.isChecked
        default.crazyInterval = TwoVal(
            etCrazyIntervalLow.text.toString().toInt(),
            etCrazyInterval.text.toString().toInt()
        )
        timeConfirmListener(default)
        dismiss()
    }


    override fun dismiss() {
        super.dismiss()
        this.coroutineContext.cancel()
    }


}

