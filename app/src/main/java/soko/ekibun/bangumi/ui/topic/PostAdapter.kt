package soko.ekibun.bangumi.ui.topic

import android.annotation.SuppressLint
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import kotlinx.android.synthetic.main.item_reply.view.*
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.bangumi.Bangumi
import soko.ekibun.bangumi.api.bangumi.bean.TopicPost
import soko.ekibun.bangumi.ui.view.FixMultiViewPager
import soko.ekibun.bangumi.util.HtmlHttpImageGetter
import soko.ekibun.bangumi.util.HtmlTagHandler
import java.net.URI
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import org.jsoup.Jsoup
import soko.ekibun.bangumi.ui.web.WebActivity


class PostAdapter(data: MutableList<TopicPost>? = null) :
        BaseQuickAdapter<TopicPost, BaseViewHolder>(R.layout.item_reply, data) {

    private val drawableMap  = HashMap<String, LinkedHashMap<String, HtmlHttpImageGetter.UrlDrawable>>()

    @SuppressLint("SetTextI18n")
    override fun convert(helper: BaseViewHolder, item: TopicPost) {
        helper.addOnClickListener(R.id.item_del)
        helper.addOnClickListener(R.id.item_reply)
        helper.addOnClickListener(R.id.item_edit)
        helper.addOnClickListener(R.id.item_avatar)
        helper.itemView.item_user.text = "${item.nickname}@${item.username}"
        val subFloor = if(item.sub_floor>0) "-${item.sub_floor}" else ""
        helper.itemView.item_time.text = "#${item.floor}$subFloor - ${item.dateline}"
        helper.itemView.offset.visibility = if(item.isSub) View.VISIBLE else View.GONE
        helper.itemView.item_reply.visibility = if(item.relate.toIntOrNull()?:0 > 0) View.VISIBLE else View.GONE
        helper.itemView.item_del.visibility = if(item.editable) View.VISIBLE else View.GONE
        helper.itemView.item_edit.visibility = helper.itemView.item_del.visibility
        val drawables = drawableMap.getOrPut(item.pst_id){ LinkedHashMap() }
        helper.itemView.item_message.setOnFocusChangeListener { _, focus ->
            if(!focus){
                helper.itemView.item_message.tag = null
                helper.itemView.item_message.text = helper.itemView.item_message.text
            } }
        @Suppress("DEPRECATION")
        helper.itemView.item_message.text = setTextLinkOpenByWebView(
                Html.fromHtml(parseHtml(item.pst_content),HtmlHttpImageGetter(helper.itemView.item_message, URI.create(Bangumi.SERVER), drawables), HtmlTagHandler(helper.itemView.item_message){
            val imageList = drawables.filter { it.value.drawable != null && (it.key.startsWith("http") || !it.key.contains("smile")) }.toList()
            val index = imageList.indexOfFirst { d-> d.first == it.source }
            if(index < 0) return@HtmlTagHandler
            val popWindow = PopupWindow(helper.itemView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true)
            val viewPager = FixMultiViewPager(helper.itemView.context)
            popWindow.contentView = viewPager
            viewPager.adapter = PhotoPagerAdapter(imageList.map{it.second.drawable!!.constantState!!.newDrawable()}){
                popWindow.dismiss()
            }
            viewPager.currentItem = index
            popWindow.isClippingEnabled = false
            popWindow.showAtLocation(helper.itemView, Gravity.CENTER, 0, 0)
        })){
            WebActivity.launchUrl(helper.itemView.context, it, "")
        }

        helper.itemView.item_message.movementMethod = LinkMovementMethod.getInstance()
        Glide.with(helper.itemView)
                .load("http:" + item.avatar)
                .apply(RequestOptions.circleCropTransform())
                .into(helper.itemView.item_avatar)
    }

    companion object {
        fun parseHtml(html: String): String{
            val doc= Jsoup.parse(html, Bangumi.SERVER)
            doc.body().children().forEach {
                var appendBefore = ""
                var appendEnd = ""
                val style = it.attr("style")
                if(style.contains("font-weight:bold")) {
                    appendBefore = "$appendBefore<b>"
                    appendEnd = "</b>$appendEnd"
                } //it.html("<b>${parseHtml(it.html())}</b>")
                if(style.contains("font-style:italic")) {
                    appendBefore = "$appendBefore<i>"
                    appendEnd = "</i>$appendEnd"
                } //it.html("<i>${parseHtml(it.html())}</i>")
                if(style.contains("text-decoration: underline")) {
                    appendBefore = "$appendBefore<u>"
                    appendEnd = "</u>$appendEnd"
                } //it.html("<u>${parseHtml(it.html())}</u>")
                if(style.contains("font-size:")) { Regex("""font-size:([0-9]*)px""").find(style)?.groupValues?.get(1)?.let{size->
                    appendBefore = "$appendBefore<size size='${size}px'>"
                    appendEnd = "</size>$appendEnd"
                } }//it.html("<size size='${size}px'>${parseHtml(it.html())}</size>")
                if(style.contains("background-color:")) {
                    appendBefore = "$appendBefore<mask>"
                    appendEnd = "</mask>$appendEnd"
                } //it.html("<mask>${parseHtml(it.html())}</mask>")
                it.html("$appendBefore${parseHtml(it.html())}$appendEnd")
            }
            doc.select("div.quote").forEach {
                it.html("“${it.html()}”")
            }
            return doc.body().html()
        }

        fun setTextLinkOpenByWebView(htmlString: Spanned, onCLick:(String)->Unit): Spanned {
            if (htmlString is SpannableStringBuilder) {
// 取得与a标签相关的Span
                val objs = htmlString.getSpans(0, htmlString.length, URLSpan::class.java)
                if (null != objs && objs.isNotEmpty()) {
                    for (obj in objs) {
                        val start = htmlString.getSpanStart(obj)
                        val end = htmlString.getSpanEnd(obj)
                        if (obj is URLSpan) {
                            //先移除这个Span，再新添加一个自己实现的Span。
                            val url = obj.url
                            htmlString.removeSpan(obj)
                            htmlString.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    onCLick(url)
                                }
                            }, start, end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }
            return htmlString
        }
    }
}