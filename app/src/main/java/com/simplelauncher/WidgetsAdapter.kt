package com.simplelauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying widgets in picker and management screens
 */
class WidgetsAdapter(
    private val widgets: List<WidgetItem>,
    private val buttonText: String = "Add",
    private val onWidgetClick: (WidgetItem) -> Unit
) : RecyclerView.Adapter<WidgetsAdapter.WidgetViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_widget, parent, false)
        return WidgetViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        holder.bind(widgets[position], buttonText, onWidgetClick)
    }
    
    override fun getItemCount(): Int = widgets.size
    
    class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val widgetIcon: ImageView = itemView.findViewById(R.id.widgetIcon)
        private val widgetName: TextView = itemView.findViewById(R.id.widgetName)
        private val widgetSize: TextView = itemView.findViewById(R.id.widgetSize)
        private val widgetActionButton: Button = itemView.findViewById(R.id.widgetActionButton)
        
        fun bind(widget: WidgetItem, buttonText: String, onClick: (WidgetItem) -> Unit) {
            widgetIcon.setImageDrawable(widget.icon)
            widgetName.text = widget.label
            widgetSize.text = "${widget.providerInfo.minWidth / 70} x ${widget.providerInfo.minHeight / 70} cells"
            widgetActionButton.text = buttonText
            
            widgetActionButton.setOnClickListener {
                onClick(widget)
            }
        }
    }
}
