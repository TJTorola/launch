package dev.torola.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(
    private val allApps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongPress: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    private var filteredApps: List<AppInfo> = allApps

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appLabel: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(appInfo: AppInfo) {
            appLabel.text = appInfo.label
            itemView.setOnClickListener {
                onAppClick(appInfo)
            }
            itemView.setOnLongClickListener {
                onAppLongPress(appInfo)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredApps[position])
    }

    override fun getItemCount(): Int = filteredApps.size

    fun filter(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
    
    fun getFirstApp(): AppInfo? {
        return filteredApps.firstOrNull()
    }
}
