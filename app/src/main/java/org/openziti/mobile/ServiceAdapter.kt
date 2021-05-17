/*
 * Copyright (c) 2021 NetFoundry. All rights reserved.
 */

package org.openziti.mobile

import android.opengl.Visibility
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openziti.api.Service

class ServiceAdapter(private val services:Collection<Service>):RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    /**
     * setup the elements in the view for binding to data
     */
    inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
        val label = itemView.findViewById<TextView>(R.id.Label)
        val url = itemView.findViewById<TextView>(R.id.Value)
        val warning = itemView.findViewById<ImageView>(R.id.WarningImage)
        val details = itemView.findViewById<ImageView>(R.id.DetailsImage)
    }

    /**
     * Create the Layout for the adapter
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceAdapter.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val serviceView = inflater.inflate(R.layout.line, parent, false)
        return ViewHolder(serviceView)
    }

    /**
     * Bind the data to the view elements
     */
    override fun onBindViewHolder(viewHolder: ServiceAdapter.ViewHolder, position: Int) {
        val service: Service = services.elementAt(position)
        viewHolder.label.setText(service.name)
        var url = ""
        service.interceptConfig?.let {
            url = "$it"
        }
        viewHolder.url.setText(url)
        // If posture check fails or a warning exists on the service
        viewHolder.warning.visibility = View.GONE;
    }

    // Returns the total count of items in the list
    override fun getItemCount(): Int {
        return services.size
    }
}