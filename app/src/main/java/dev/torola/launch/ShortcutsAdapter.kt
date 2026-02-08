package dev.torola.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShortcutsAdapter(
    private val shortcuts: List<ShortcutItem>,
    private val onDeleteClick: (ShortcutItem) -> Unit
) : RecyclerView.Adapter<ShortcutsAdapter.ShortcutViewHolder>() {

    inner class ShortcutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val shortcutNameLabel: TextView = itemView.findViewById(R.id.shortcutNameLabel)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)

        fun bind(shortcut: ShortcutItem) {
            shortcutNameLabel.text = shortcut.name
            deleteButton.setOnClickListener {
                onDeleteClick(shortcut)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut, parent, false)
        return ShortcutViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(shortcuts[position])
    }

    override fun getItemCount(): Int = shortcuts.size
}
