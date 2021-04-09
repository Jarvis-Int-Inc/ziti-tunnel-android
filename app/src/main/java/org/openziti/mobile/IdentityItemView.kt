/*
 * Copyright (c) 2021 NetFoundry. All rights reserved.
 */

package org.openziti.mobile
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.identityitem.view.*
import org.openziti.ZitiContext

/**
 * TODO: document your custom view class.
 */
class IdentityItemView(context: AppCompatActivity, ctxModel: ZitiContextModel, ctx: ZitiContext) : RelativeLayout(context) {

    private var _ctxModel: ZitiContextModel = ctxModel
    private var _ctx: ZitiContext = ctx
    private var _name: String? = ""
    private var _server: String? = ""
    private var _count: Int = 0
    private var _isOn: Boolean = false


    var identity: ZitiContext
        get() = this._ctx
        set(value) {
            this._ctx = value;
        }

    var identityModel: ZitiContextModel
        get() = this._ctxModel
        set(value) {
            this._ctxModel = value;
        }

    var idname: String
        get() = this._name.toString()
        set(value) {
            this._name = value
            IdentityName.text = this._name
        }

    var server: String
        get() = this._server.toString()
        set(value) {
            this._server = value
            IdentityServer.text = this._server
        }

    var isOn:Boolean
        get() = this._isOn
        set(value) {
            this._isOn = value
            IdToggleSwitch.isChecked = this._isOn
            if (this._isOn) StatusLabel.text = "enabled"
            else StatusLabel.text = "disabled"
        }

    var count: Int
        get() = this._count
        set(value) {
            this._count = value
            ServiceCount.text = this._count.toString()
        }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.IdentityView, defStyle, 0)
        LayoutInflater.from(context).inflate(R.layout.identityitem, this, true)
        a.recycle()
    }

    init {
        init(null, 0)

        ctxModel.name().observe(context, Observer {
            IdentityName.text = it
        })

    }
}
