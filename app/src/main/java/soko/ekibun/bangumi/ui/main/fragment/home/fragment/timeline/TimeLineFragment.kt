package soko.ekibun.bangumi.ui.main.fragment.home.fragment.timeline

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.widget.PopupMenu
import android.view.View
import kotlinx.android.synthetic.main.fragment_timeline.*
import okhttp3.FormBody
import soko.ekibun.bangumi.R
import soko.ekibun.bangumi.api.ApiHelper
import soko.ekibun.bangumi.api.bangumi.Bangumi
import soko.ekibun.bangumi.ui.main.MainActivity
import soko.ekibun.bangumi.ui.main.fragment.home.fragment.HomeTabFragment
import soko.ekibun.bangumi.ui.topic.ReplyDialog

class TimeLineFragment: HomeTabFragment(R.layout.fragment_timeline){
    override val titleRes: Int = R.string.timeline
    override val iconRes: Int = R.drawable.ic_timelapse

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TimeLinePagerAdapter(view.context, this, item_pager)
        item_pager?.adapter = adapter
        item_tabs?.setupWithViewPager(item_pager)
        val popup = PopupMenu(view.context, item_type)
        popup.menuInflater.inflate(R.menu.list_timeline, popup.menu)
        item_type?.text = popup.menu.findItem(adapter.selectedType)?.title
        item_type?.setOnClickListener {
            popup.menu.findItem(R.id.timeline_type_self)?.isVisible = ((activity as? MainActivity)?.user?.username?:"").isNotEmpty()
            popup.setOnMenuItemClickListener{
                item_type?.text = it.title
                adapter.selectedType = it.itemId
                adapter.reset()
                true
            }
            popup.show()
        }
        onSelect()
    }

    override fun onSelect() {
        val adapter = (item_pager?.adapter as? TimeLinePagerAdapter)?:return
        val formhash = (activity as? MainActivity)?.user?.sign?:""
        if(formhash.isEmpty()){
            item_new?.hide()
        }else {
            item_new?.show()
            var draft = ""
            item_new?.setOnClickListener {
                val dialog = ReplyDialog()
                dialog.hint = context?.getString(R.string.timeline_dialog_add)?:""
                dialog.draft = draft
                dialog.callback = {string, send ->
                    if(send){
                        ApiHelper.buildHttpCall("${Bangumi.SERVER}/update/user/say?ajax=1", mapOf("User-Agent" to ((activity as? MainActivity)?.ua?:"")), body = FormBody.Builder()
                                .add("say_input", string)
                                .add("formhash", formhash)
                                .add("submit", "submit").build()){
                            it.body()?.string()?.contains("\"status\":\"ok\"") == true
                        }.enqueue(ApiHelper.buildCallback(context, {
                            if(it){
                                draft = ""
                                if(item_pager?.currentItem?:2 !in 0..1) item_pager?.currentItem = 1
                                adapter.pageIndex[item_pager?.currentItem?:0] = 0
                                adapter.loadTopicList()
                            }else Snackbar.make(item_pager, R.string.hint_submit_error, Snackbar.LENGTH_SHORT).show()
                        }){ })
                    }else draft = string
                }
                dialog.show(activity?.supportFragmentManager?:return@setOnClickListener, "say")
            }
            adapter.pageIndex[item_pager?.currentItem?:0] = adapter.pageIndex[item_pager?.currentItem?:0]?:0
            if(adapter.pageIndex[item_pager?.currentItem?:0] == 0) adapter.loadTopicList()
        }
    }
}