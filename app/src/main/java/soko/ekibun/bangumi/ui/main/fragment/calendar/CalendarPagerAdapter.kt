package soko.ekibun.bangumi.ui.main.fragment.calendar

import android.annotation.SuppressLint
import android.preference.PreferenceManager
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import retrofit2.Call
import soko.ekibun.bangumi.api.ApiHelper
import soko.ekibun.bangumi.api.bangumi.Bangumi
import soko.ekibun.bangumi.api.bangumi.bean.Images
import soko.ekibun.bangumi.api.bangumi.bean.Subject
import soko.ekibun.bangumi.api.bangumi.bean.SubjectCollection
import soko.ekibun.bangumi.api.bangumi.bean.SubjectType
import soko.ekibun.bangumi.api.github.GithubRaw
import soko.ekibun.bangumi.api.github.bean.BangumiCalendarItem
import soko.ekibun.bangumi.api.tinygrail.bean.OnAir
import soko.ekibun.bangumi.ui.main.MainActivity
import soko.ekibun.bangumi.ui.subject.SubjectActivity
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class CalendarPagerAdapter(val fragment: CalendarFragment, private val pager: ViewPager) : PagerAdapter(){
    val sp by lazy { PreferenceManager.getDefaultSharedPreferences(pager.context) }

    private fun getItem(position: Int): Pair<CalendarAdapter, SwipeRefreshLayout>{
        return items.getOrPut(position){
            val swipeRefreshLayout = SwipeRefreshLayout(pager.context)
            val recyclerView = RecyclerView(pager.context)
            val adapter = CalendarAdapter()
            adapter.setOnItemChildClickListener { _, v, pos ->
                SubjectActivity.startActivity(v.context, adapter.data[pos].t.subject)
            }
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(pager.context)
            recyclerView.isNestedScrollingEnabled = false
            swipeRefreshLayout.addView(recyclerView)
            swipeRefreshLayout.tag = recyclerView
            swipeRefreshLayout.setOnRefreshListener { loadCalendarList() }
            Pair(adapter, swipeRefreshLayout)
        }
    }

    private val items = HashMap<Int, Pair<CalendarAdapter, SwipeRefreshLayout>>()
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val item = getItem(CalendarAdapter.getCalendarInt(getPostDate(position)))
        if(raw == null && position == pager.currentItem)
            loadCalendarList()
        (item.second.parent as? ViewGroup)?.removeView(item.second)
        container.addView(item.second)
        return item.second
    }

    private var firstLoad = true
    private fun setOnAirList(it: List<BangumiCalendarItem>){
        val useCN = sp.getBoolean("calendar_use_cn", false)
        val use30h = sp.getBoolean("calendar_use_30h", false)

        val now = CalendarAdapter.getNowInt(use30h)
        val onAir = HashMap<Int, HashMap<String, ArrayList<OnAir>>>()
        val calWeek = CalendarAdapter.getIntCalendar(now)
        calWeek.add(java.util.Calendar.DAY_OF_MONTH, -7)
        val minDate = CalendarAdapter.getCalendarInt(calWeek)
        calWeek.add(java.util.Calendar.DAY_OF_MONTH, +14)
        val maxDate = CalendarAdapter.getCalendarInt(calWeek)
        it.forEach {subject->
            val bangumi = Subject(subject.id?:return@forEach, "${Bangumi.SERVER}/subject/${subject.id}", SubjectType.ANIME, subject.name, subject.name_cn, images = Images(
                    subject.image?.replace("/g/", "/l/"),
                    subject.image?.replace("/g/", "/c/"),
                    subject.image?.replace("/g/", "/m/"),
                    subject.image?.replace("/g/", "/s/"),
                    subject.image))
            subject.eps?.forEach {
                val item=OnAir(it, bangumi, null)
                val timeInt = (if(!useCN || subject.timeCN.isNullOrEmpty()) subject.timeJP else subject.timeCN)?.toIntOrNull()?:0
                val zoneOffset = TimeZone.getDefault().rawOffset
                val hourDif = zoneOffset/1000/3600 - 8
                val minuteDif = zoneOffset/1000/60 % 60
                val minute = timeInt%100+minuteDif
                val hour = timeInt/100+hourDif + when{
                    minute >= 60 -> 1
                    minute < 0 -> -1
                    else -> 0
                }
                val dayCarry = when{
                    hour >= if(use30h) 30 else 24-> 1
                    hour < if(use30h) 6 else 0 -> -1
                    else -> 0
                }
                val mStrBuilder = StringBuilder()
                val mFormatter = Formatter(mStrBuilder, Locale.getDefault())
                mStrBuilder.setLength(0)
                val time = mFormatter.format("%02d:%02d", if(use30h) (hour - 6 + 24) % 24 + 6 else (hour+24)%24, minute%60).toString()
                val cal = java.util.Calendar.getInstance()
                cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.airdate)
                val week = (((if(!useCN || subject.timeCN.isNullOrEmpty()) subject.weekDayJP else subject.weekDayCN)?:0) - CalendarAdapter.getWeek(cal) + 7) % 7
                val dayDif = week + dayCarry
                cal.add(java.util.Calendar.DAY_OF_MONTH, dayDif)
                val date = CalendarAdapter.getCalendarInt(cal)
                if(date in minDate..maxDate)
                    onAir.getOrPut(date){HashMap()}.getOrPut(time){ArrayList()}.add(item)
            }
        }
        onAir.toList().forEach {date->
            var index = -1
            val item = getItem(date.first)
            item.first.setNewData(null)
            date.second.toList().sortedBy { it.first }.forEach { time->
                var isHeader = true
                time.second.forEach {
                    it.subject.collect = chaseList?.find {c-> c.subject_id == it.subject.id } != null
                    if(it.subject.images != null){
                        if(index == -1 && !CalendarAdapter.pastTime(date.first, time.first, use30h))
                            index = item.first.data.size
                        item.first.addData(CalendarAdapter.CalendarSection(isHeader, it, date.first, time.first))
                        isHeader = false
                    } } }
            if(firstLoad)
                ((item.second.tag as? RecyclerView)?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index-1, 0)
        }
        if(firstLoad)
            firstLoad = false
    }

    private var raw: List<BangumiCalendarItem>? = null
    private var chaseList: List<SubjectCollection>? = null
    private var calendarCall : Call<List<BangumiCalendarItem>>? = null
    private var chaseCall: Call<List<SubjectCollection>>? = null
    @SuppressLint("UseSparseArrays")
    private fun loadCalendarList(){
        items.forEach { it.value.second.isRefreshing = true }

        calendarCall?.cancel()
        calendarCall = GithubRaw.createInstance().bangumiCalendar()
        calendarCall?.enqueue(ApiHelper.buildCallback(pager.context, {
            raw = it
            setOnAirList(raw?:return@buildCallback)
        }, {items.forEach {
            it.value.second.isRefreshing = false
        }}))

        chaseCall?.cancel()
        val user = (fragment.activity as? MainActivity)?.user?:return
        val userName = user.username?:user.id.toString()
        chaseCall = Bangumi.createInstance().collection(userName)
        chaseCall?.enqueue(ApiHelper.buildCallback(pager.context, {
            chaseList = it
            setOnAirList(raw?:return@buildCallback)
        }, {}))
    }

    override fun getItemPosition(`object`: Any): Int {
        return PagerAdapter.POSITION_NONE
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    private fun parseDate(date: Int): String{
        val cal = CalendarAdapter.getIntCalendar(date)
        return "${date/100%100}-${date%100}\n${CalendarAdapter.weekSmall[CalendarAdapter.getWeek(cal)]}(${CalendarAdapter.weekJp[CalendarAdapter.getWeek(cal)]})"
    }

    private fun getPostDate(pos: Int): java.util.Calendar{
        val cal = CalendarAdapter.getIntCalendar(CalendarAdapter.getNowInt(
                sp.getBoolean("calendar_use_30h", false)))
        cal.add(java.util.Calendar.DAY_OF_MONTH, pos-7)
        return cal
    }

    override fun getPageTitle(pos: Int): CharSequence{
        return parseDate(CalendarAdapter.getCalendarInt(getPostDate(pos)))//parseDate(old?.toList()?.sortedBy { it.first }?.get(pos)?.first?:0)
    }

    override fun getCount(): Int {
        return 15
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }
}