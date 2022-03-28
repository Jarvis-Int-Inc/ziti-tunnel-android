/*
 * Copyright (c) 2020 NetFoundry. All rights reserved.
 */

package org.openziti.mobile
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.line.view.*

import android.util.Log
import org.openziti.api.DNSName
import org.openziti.api.InterceptAddress
import org.openziti.api.Service

/**
 * Service List Line Items
 */
class LineView : RelativeLayout {

    var _label: String? = ""
    var _value: String? = ""
    var _service: Service? = null

    var service: Service?
        get() = this._service
        set(value) {
            this._service = value
            var addressNames: String = ""
            // Hate this. Shows my ignorance. I need to quickly (as in now)
            // get the ports for the service. I cannot do it using
            // interceptConfig, as I need to know more Kotlin. So I'm doing
            // something stupid and trimming a domain name.

            /*
            if (_service?.interceptConfig?.addresses?.size!! > 0) {
                for (item in _service?.interceptConfig?.addresses!!) {
                    var textToAdd = item.toString()
                    textToAdd = textToAdd.substringAfter("=")
                    textToAdd = textToAdd.substringAfter("/")
                    textToAdd = textToAdd.substringBefore(")")
                    textToAdd = textToAdd.substringBefore(",")
                    addressNames += textToAdd

                }

            }
            */

            var count = 0
            if (_service?.interceptConfig?.portRanges != null) {
                if (_service?.interceptConfig?.portRanges?.size!! > 0) {
                    var size = _service?.interceptConfig?.portRanges?.size!!
                    for (item in _service?.interceptConfig?.portRanges!!) {
                        var textToAdd = item.toString()
                        addressNames += textToAdd
                        count += 1
                        if (count < size) addressNames += ", "
                    }
                }
            }

            this.value = addressNames

            Log.d("LineView", "${addressNames}")
        }

    var label: String
        get() = this._label.toString()
        set(value) {
            this._label = value
            Label.text = this._label
        }

    var value: String
        get() = this._value.toString()
        set(value) {
            this._value = value
            Value.text = this._value
        }

    constructor(context: Context) : super(context) {
        init(null, 0)
        Label.setOnClickListener {
            val intent = Intent(context, ServiceDetailsActivity::class.java)
            /*
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Label", Label.text.toString())
            clipboard.setPrimaryClip(clip)
            val content = Label.text.toString() + " has been copied to your clipboard"
            Toast.makeText(context, content, Toast.LENGTH_LONG).show()
             */
        }
        Value.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Value", Value.text.toString())
            clipboard.setPrimaryClip(clip)
            val content = Value.text.toString() + " has been copied to your clipboard"
            Toast.makeText(context, content, Toast.LENGTH_LONG).show()
        }
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        LayoutInflater.from(context).inflate(R.layout.line, this, true)
    }
}
