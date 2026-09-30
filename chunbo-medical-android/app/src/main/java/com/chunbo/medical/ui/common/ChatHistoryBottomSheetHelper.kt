package com.chunbo.medical.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.databinding.DialogChatHistoryBottomSheetBinding
import com.chunbo.medical.databinding.ItemSessionHistoryCardBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionHistoryCardData(
    val sessionId: String,
    val title: String,
    val timeDisplay: String,
    val badge: String = "AI提炼",
    val rawData: Map<String, Any> = emptyMap()
)

object ChatHistoryBottomSheetHelper {

    fun show(
        context: Context,
        scope: LifecycleCoroutineScope,
        sheetTitle: String,
        newSessionTitle: String,
        newSessionSub: String,
        loadSessions: suspend () -> List<SessionHistoryCardData>,
        onNewSession: () -> Unit,
        onSessionSelect: (SessionHistoryCardData) -> Unit,
        onSessionDelete: (suspend (SessionHistoryCardData) -> Unit)? = null
    ) {
        val binding = DialogChatHistoryBottomSheetBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        binding.tvHistorySheetTitle.text = sheetTitle
        binding.tvNewSessionMain.text = newSessionTitle
        binding.tvNewSessionSub.text = newSessionSub

        binding.btnCloseHistorySheet.setOnClickListener {
            dialog.dismiss()
        }

        binding.cardActionNewSession.setOnClickListener {
            dialog.dismiss()
            onNewSession()
        }

        lateinit var adapter: HistorySessionAdapter
        adapter = HistorySessionAdapter(
            onItemClick = { item ->
                dialog.dismiss()
                onSessionSelect(item)
            },
            onItemLongClick = { item ->
                if (onSessionDelete != null) {
                    AlertDialog.Builder(context)
                        .setTitle("删除会话")
                        .setMessage("确定删除「${item.title}」吗？删除后不可恢复。")
                        .setPositiveButton("删除") { _, _ ->
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { onSessionDelete(item) }
                                    val list = withContext(Dispatchers.IO) { loadSessions() }
                                    adapter.submitList(list)
                                    binding.tvHistorySheetCount.text = "共 ${list.size} 条"
                                    if (list.isEmpty()) {
                                        binding.layoutHistoryEmpty.visibility = View.VISIBLE
                                        binding.rvHistoryCards.visibility = View.GONE
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        )
        binding.rvHistoryCards.layoutManager = LinearLayoutManager(context)
        binding.rvHistoryCards.adapter = adapter

        // 默认显示加载中动画，不弹任何恼人 Toast
        binding.layoutHistoryLoading.visibility = View.VISIBLE
        binding.layoutHistoryEmpty.visibility = View.GONE
        binding.rvHistoryCards.visibility = View.GONE
        binding.tvHistorySheetCount.text = "加载中..."

        dialog.show()

        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    loadSessions()
                }
                binding.layoutHistoryLoading.visibility = View.GONE
                if (list.isEmpty()) {
                    binding.layoutHistoryEmpty.visibility = View.VISIBLE
                    binding.rvHistoryCards.visibility = View.GONE
                    binding.tvHistorySheetCount.text = "共 0 条"
                } else {
                    binding.layoutHistoryEmpty.visibility = View.GONE
                    binding.rvHistoryCards.visibility = View.VISIBLE
                    binding.tvHistorySheetCount.text = "共 ${list.size} 条"
                    adapter.submitList(list)
                }
            } catch (e: Exception) {
                binding.layoutHistoryLoading.visibility = View.GONE
                binding.layoutHistoryEmpty.visibility = View.VISIBLE
                binding.tvHistorySheetCount.text = "加载失败"
                Toast.makeText(context, "获取会话记录失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 格式化人类易读时间，去除冗长复杂的 ISO 字符串 */
    fun formatFriendlyTime(rawTime: String?): String {
        if (rawTime.isNullOrBlank()) return "近期"
        val clean = rawTime.replace("T", " ").trim()
        val todayPrefix = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return when {
            clean.startsWith(todayPrefix) -> "今天 " + clean.drop(11).take(5)
            clean.length >= 16 -> clean.substring(5, 16)
            else -> clean
        }
    }

    private class HistorySessionAdapter(
        private val onItemClick: (SessionHistoryCardData) -> Unit,
        private val onItemLongClick: (SessionHistoryCardData) -> Unit
    ) : RecyclerView.Adapter<HistorySessionAdapter.VH>() {

        private val items = mutableListOf<SessionHistoryCardData>()

        fun submitList(newItems: List<SessionHistoryCardData>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSessionHistoryCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.tvHistoryItemTitle.text = item.title
            holder.binding.tvHistoryItemTime.text = item.timeDisplay
            holder.binding.tvHistoryItemBadge.text = item.badge
            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        override fun getItemCount(): Int = items.size

        class VH(val binding: ItemSessionHistoryCardBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
