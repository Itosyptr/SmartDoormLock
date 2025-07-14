package telkom.ta.smartdoor.admin // Anda bisa letakkan di package 'admin'

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import telkom.ta.smartdoor.R
import telkom.ta.smartdoor.database.VerificationLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<VerificationLog, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = getItem(position)
        holder.bind(log)
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.iv_status_icon)
        private val messageText: TextView = itemView.findViewById(R.id.tv_log_message)
        private val userText: TextView = itemView.findViewById(R.id.tv_log_user)
        private val timeText: TextView = itemView.findViewById(R.id.tv_log_time)

        fun bind(log: VerificationLog) {
            messageText.text = log.message
            userText.text = "User: ${log.username}"
            timeText.text = formatTimestamp(log.timestamp)

            if (log.status.equals("Berhasil", ignoreCase = true)) {
                statusIcon.setImageResource(R.drawable.ic_check)
                statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.green_success))
            } else {
                statusIcon.setImageResource(R.drawable.ic_cancel)
                statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.red_error))
            }
        }

        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class LogDiffCallback : DiffUtil.ItemCallback<VerificationLog>() {
        override fun areItemsTheSame(oldItem: VerificationLog, newItem: VerificationLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VerificationLog, newItem: VerificationLog): Boolean {
            return oldItem == newItem
        }
    }
}
