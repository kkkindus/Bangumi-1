package soko.ekibun.bangumi.ui.main.fragment.home.fragment.timeline

import android.support.v7.app.AlertDialog
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.chad.library.adapter.base.BaseSectionQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import kotlinx.android.synthetic.main.item_timeline.view.*
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.ApiHelper
import soko.ekibun.bangumi.api.bangumi.Bangumi
import soko.ekibun.bangumi.api.bangumi.bean.TimeLine
import soko.ekibun.bangumi.ui.topic.PostAdapter
import soko.ekibun.bangumi.ui.web.WebActivity
import soko.ekibun.bangumi.util.HttpUtil
import java.net.URI

class TimeLineAdapter(val ua: String, data: MutableList<TimeLine>? = null) :
        BaseSectionQuickAdapter<TimeLine, BaseViewHolder>
        (R.layout.item_timeline, R.layout.header_episode, data){
    override fun convertHead(helper: BaseViewHolder, item: TimeLine) {
        helper.setText(R.id.item_header, item.header)
    }

    override fun convert(helper: BaseViewHolder, item: TimeLine) {
        //say
        helper.itemView.item_layout.setOnClickListener {
            WebActivity.launchUrl(helper.itemView.context, HttpUtil.getUrl(item.t.sayUrl?:return@setOnClickListener, URI.create(Bangumi.SERVER)), "")
        }
        helper.itemView.item_layout.isClickable = !item.t.sayUrl.isNullOrEmpty()
        //action
        @Suppress("DEPRECATION")
        helper.itemView.item_action.text = PostAdapter.setTextLinkOpenByWebView(Html.fromHtml(item.t.action)){
            WebActivity.launchUrl(helper.itemView.context, HttpUtil.getUrl(it, URI.create(Bangumi.SERVER)), "")
        }
        helper.itemView.item_action.movementMethod = if(helper.itemView.item_layout.isClickable) null else LinkMovementMethod.getInstance()
        //del
        helper.itemView.item_del.visibility = if(item.t.delUrl.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        helper.itemView.item_del.setOnClickListener {
            AlertDialog.Builder(helper.itemView.context).setTitle("删除这条时间线？")
                    .setNegativeButton("取消") { _, _ -> }.setPositiveButton("确定") { _, _ ->
                        ApiHelper.buildHttpCall("${item.t.delUrl}&ajax=1", mapOf("User-Agent" to ua)) {
                            it.body()?.string()?.contains("\"status\":\"ok\"") == true
                        }.enqueue(ApiHelper.buildCallback<Boolean>(helper.itemView.context, {
                            if(!it) return@buildCallback
                            val index = data.indexOfFirst { it === item }
                            val removeHeader = data.getOrNull(index-1)?.isHeader == true && data.getOrNull(index+1)?.isHeader == true
                            remove(index)
                            if(removeHeader) remove(index-1)
                        }) {})
                    }.show()
        }
        //collectStar
        helper.itemView.item_rate.visibility = if(item.t.collectStar == 0) View.GONE else View.VISIBLE
        helper.itemView.item_rate.rating = item.t.collectStar * 0.5f
        //content
        helper.itemView.item_content.visibility = if(item.t.content.isNullOrEmpty()) View.GONE else View.VISIBLE
        helper.itemView.item_content.text = item.t.content?:""
        helper.itemView.item_content.setOnClickListener {
            WebActivity.launchUrl(helper.itemView.context, item.t.contentUrl, "")
        }
        //time
        helper.itemView.item_time.text = item.t.time

        val userImage = item.t.userImg
        helper.itemView.item_avatar.visibility = if(userImage.isEmpty() || data.getOrNull(data.indexOfFirst { it === item } - 1)?.t?.userUrl == item.t.userUrl ) View.INVISIBLE else View.VISIBLE
        helper.itemView.item_avatar.setOnClickListener {
            WebActivity.launchUrl(helper.itemView.context, item.t.userUrl, "")
        }
        if(!userImage.isEmpty())
            Glide.with(helper.itemView.item_avatar)
                    .load(userImage)
                    .apply(RequestOptions.circleCropTransform().error(R.drawable.ic_404))
                    .into(helper.itemView.item_avatar)
    }

}