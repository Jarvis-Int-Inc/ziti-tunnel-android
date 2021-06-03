/*
 * Copyright (c) 2021 NetFoundry. All rights reserved.
 */

package org.openziti.mobile

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.net.VpnService
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.android.synthetic.main.about.*
import kotlinx.android.synthetic.main.advanced.*
import kotlinx.android.synthetic.main.authenticate.view.*
import kotlinx.android.synthetic.main.configuration.*
import kotlinx.android.synthetic.main.dashboard.*
import kotlinx.android.synthetic.main.detailsmodal.view.*
import kotlinx.android.synthetic.main.growler.view.*
import kotlinx.android.synthetic.main.identities.*
import kotlinx.android.synthetic.main.identity.*
import kotlinx.android.synthetic.main.identity.view.*
import kotlinx.android.synthetic.main.identityitem.view.*
import kotlinx.android.synthetic.main.line.view.*
import kotlinx.android.synthetic.main.log.*
import kotlinx.android.synthetic.main.logs.*
import kotlinx.android.synthetic.main.mfa.view.*
import kotlinx.android.synthetic.main.recovery.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openziti.ZitiContext
import org.openziti.android.Ziti
import org.openziti.api.Service
import org.openziti.identity.Identity
import java.util.*


class ZitiMobileEdgeActivity : AppCompatActivity() {

    lateinit var prefs: SharedPreferences
    val systemId: Int by lazy {
        this.packageManager?.getApplicationInfo("android", PackageManager.GET_META_DATA)?.uid ?: 0
    }
    var isMenuOpen = false

    var ipAddress = "169.254.0.1"
    var subnet = "255.255.255.0"
    var mtu = "4000"
    var dns = "169.254.0.2"
    var state = "startActivity"
    var log_application = ""
    var log_tunneler = ""
    val version = "${BuildConfig.VERSION_NAME}(${BuildConfig.GIT_COMMIT})"
    var page = 1
    var perPage = 100
    var sortBy = "Name"
    var sortHow = "Asc"
    var services:Collection<Service> = emptySet()

    lateinit var contextViewModel: ZitiViewModel
    internal var vpn: ZitiVPNService.ZitiVPNBinder? = null
    internal val serviceConnection = object: ServiceConnection{
        override fun onServiceDisconnected(name: ComponentName?) {
            vpn = null
            updateTunnelState()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            vpn = service as ZitiVPNService.ZitiVPNBinder
            updateTunnelState()
        }
    }

    fun launchUrl(url: String) {
        val openURL = Intent(Intent.ACTION_VIEW)
        openURL.data = Uri.parse(url)
        startActivity(openURL)
    }

    var duration = 300
    var offScreenX = 0
    var offScreenY = 0
    var openY = 0
    var isOpen = false
    var isModal = false
    lateinit var modal:View
    lateinit var _identity:ZitiContext

    fun getScreenWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }

    fun getScreenHeight(): Int {
        return Resources.getSystem().displayMetrics.heightPixels
    }

    private fun toggleMenu() {
        val posTo = getScreenWidth()-(getScreenWidth()/3)
        var animatorSet = AnimatorSet()
        var scaleY = ObjectAnimator.ofFloat(MainArea, "scaleY", .9f, 1.0f).setDuration(duration.toLong())
        var scaleX = ObjectAnimator.ofFloat(MainArea, "scaleX", .9f, 1.0f).setDuration(duration.toLong())
        var fader = ObjectAnimator.ofFloat(FrameArea, "alpha", 1f, 0f).setDuration(duration.toLong())

        var animateTo = ObjectAnimator.ofFloat(MainArea, "translationX", posTo.toFloat(), 0f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator()
        animateTo.interpolator = DecelerateInterpolator()
        scaleY.interpolator = DecelerateInterpolator()
        scaleX.interpolator = DecelerateInterpolator()
        MainMenu.visibility = View.GONE
        state = "startActivity"
        if (!isMenuOpen) {
            state = "menu"
            MainMenu.visibility = View.VISIBLE
            animateTo = ObjectAnimator.ofFloat(MainArea, "translationX", 0f, posTo.toFloat()).setDuration(duration.toLong())
            scaleY = ObjectAnimator.ofFloat(MainArea, "scaleY", 1.0f, 0.9f).setDuration(duration.toLong())
            scaleX = ObjectAnimator.ofFloat(MainArea, "scaleX", 1.0f, 0.9f).setDuration(duration.toLong())
            fader = ObjectAnimator.ofFloat(FrameArea, "alpha", 0f, 1f).setDuration(duration.toLong())
        }
        animatorSet.play(animateTo).with(scaleX).with(scaleY).with(fader)
        animatorSet.start()
        isMenuOpen = !isMenuOpen
    }

    private fun toggleSlide(view: View, newState: String) {
        try {
            val inputManager:InputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.hideSoftInputFromWindow(currentFocus!!.windowToken, InputMethodManager.SHOW_FORCED)
        } catch (e: Exception) {}
        var fader = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).setDuration(duration.toLong())
        var animateTo = ObjectAnimator.ofFloat(view, "translationX", offScreenX.toFloat(), 0f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator()
        animateTo.interpolator = DecelerateInterpolator()
        state = newState
        if (view.x==0f) {
            fader = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).setDuration(duration.toLong())
            animateTo = ObjectAnimator.ofFloat(view, "translationX", 0f, offScreenX.toFloat()).setDuration(duration.toLong())
        }
        var animatorSet = AnimatorSet()
        animatorSet.play(animateTo).with(fader)
        animatorSet.start()
    }

    override fun onBackPressed() {
        if (isModal) hideModal()
        else {
            if (state=="menu") toggleMenu()
            else if (state=="about") toggleSlide(AboutPage, "menu")
            else if (state=="advanced") toggleSlide(AdvancedPage, "menu")
            else if (state=="config") toggleSlide(ConfigPage, "advanced")
            else if (state=="identity") toggleSlide(ConfigPage, "identities")
            else super.onBackPressed()
        }
    }

    private var startPosition = 0f


    fun TurnOff() {
        OnButton.visibility = View.GONE
        OffButton.visibility = View.VISIBLE
        TimeConnected.visibility = View.INVISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        this.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        offScreenX = getScreenWidth()+50
        offScreenY = getScreenHeight()-370
        Version.text = "Version: $version"

        // Setup Screens
        AboutPage.visibility = View.VISIBLE
        AdvancedPage.visibility = View.VISIBLE
        ConfigPage.visibility = View.VISIBLE
        LogsPage.visibility = View.VISIBLE
        LogPage.visibility = View.VISIBLE
        IdentityDetailsPage.visibility = View.VISIBLE
        IdentityPage.visibility = View.VISIBLE
        AboutPage.alpha = 0f
        AdvancedPage.alpha = 0f
        ConfigPage.alpha = 0f
        LogsPage.alpha = 0f
        LogPage.alpha = 0f
        IdentityPage.alpha = 0f
        IdentityDetailsPage.alpha = 0f
        AboutPage.x = offScreenX.toFloat()
        AdvancedPage.x = offScreenX.toFloat()
        ConfigPage.x = offScreenX.toFloat()
        LogsPage.x = offScreenX.toFloat()
        LogPage.x = offScreenX.toFloat()
        IdentityPage.x = offScreenX.toFloat()
        IdentityDetailsPage.x = offScreenX.toFloat()
        openY = offScreenY
        this.startPosition = getScreenHeight().toDp()-130.toDp().toFloat()

        //this.startPosition = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, yLoc, getResources().getDisplayMetrics())
        //IdentityArea.y = 10.toDp().toFloat() //this.startPosition

        IPInput.text = ipAddress
        SubNetInput.text = subnet
        MTUInput.text = mtu
        DNSInput.text = dns

        // Dashboard Button Actions
        OffButton.setOnClickListener {
            val vb = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vb.hasVibrator()) vb.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            val intent = VpnService.prepare(applicationContext)
            if (intent != null) {
                startActivityForResult(intent, 10169)
            } else {
                onActivityResult(10169, RESULT_OK, null)
            }
            OnButton.visibility = View.VISIBLE
            OffButton.visibility = View.GONE
            TimeConnected.visibility = View.VISIBLE
        }
        OnButton.setOnClickListener {
            val vb = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vb.hasVibrator()) vb.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            onActivityResult(10168, RESULT_OK, null)
            TurnOff()
        }

        val timer = Timer()
        val task = object: TimerTask() {
            override fun run() {
                val uptime = vpn?.getUptime()?.format() ?: ""
                TimeConnected.post {
                    TimeConnected.text = uptime
                }
            }
        }
        timer.schedule(task, 0, 1000)

        // Menu Button Actions
        DashboardButton.setOnClickListener {
            toggleMenu()
        }
        MainLogo.setOnClickListener {
            toggleMenu()
        }
        AboutButton.setOnClickListener {
            toggleSlide(AboutPage, "about")
        }
        AdvancedButton.setOnClickListener {
            toggleSlide(AdvancedPage, "advanced")
        }
        FeedbackButton.setOnClickListener {
            startActivity(Intent.createChooser(Ziti.sendFeedbackIntent(), "Send Email"))
        }
        SupportButton.setOnClickListener {
            launchUrl("https://support.netfoundry.io")
        }
        AddIdentityButton.setOnClickListener {
            startActivity(Ziti.getEnrollmentIntent())
        }
        AddIdentityLabel.setOnClickListener {
            startActivity(Ziti.getEnrollmentIntent())
        }
        HamburgerButton.setOnClickListener {
            toggleMenu()
        }
        HamburgerLabel.setOnClickListener {
            toggleMenu()
        }

        // About Button Actions
        PrivacyButton.setOnClickListener {
            launchUrl("https://netfoundry.io/privacy-policy/")
        }
        TermsButton.setOnClickListener {
            launchUrl("https://netfoundry.io/terms/")
        }
        ThirdButton.setOnClickListener {
            launchUrl("https://netfoundry.io/third-party")
        }

        // Back Buttons
        BackButton.setOnClickListener {
            toggleSlide(AboutPage, "menu")
        }
        BackIdentityButton.setOnClickListener {
            toggleSlide(IdentityPage, "menu")
        }
        BackAdvancedButton.setOnClickListener {
            toggleSlide(AdvancedPage, "menu")
        }
        BackConfigButton.setOnClickListener {
            toggleSlide(ConfigPage, "advanced")
        }
        BackConfigButton2.setOnClickListener {
            toggleSlide(ConfigPage, "advanced")
        }
        BackLogsButton.setOnClickListener {
            toggleSlide(ConfigPage, "advanced")
        }
        BackToLogsButton.setOnClickListener {
            toggleSlide(LogPage, "logs")
        }
        BackIdentityDetailsButton.setOnClickListener {
            toggleSlide(IdentityDetailsPage, "identities")
        }
        BackToLogsButton2.setOnClickListener {
            toggleSlide(LogPage, "logs")
        }
        BackLogsButton.setOnClickListener {
            toggleSlide(LogsPage, "advanced")
        }
        CopyLogButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Logs", LogDetails.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Log has been copied to your clipboard", Toast.LENGTH_LONG).show()
        }

        // Advanced Buttons
        TunnelButton.setOnClickListener {
            toggleSlide(ConfigPage, "config")
        }
        LogsButton.setOnClickListener {
            toggleSlide(LogsPage, "logs")
        }
        PacketLogsButton.setOnClickListener {
            LogTypeTitle.text = ("Packet Tunnel Logs")
            LogDetails.text = log_tunneler
            toggleSlide(LogPage, "logdetails")
        }

        DetailModal.CloseDetailsButton.setOnClickListener {
            hideModal()
        }
        ModalBg.setOnClickListener {
            hideModal()
        }
        Growler.CloseGrowlerButton.setOnClickListener {
            hideGrowler();
        }

        // MFA Recovery Actions
        IdentityDetailsPage.ShowRecoverButton.setOnClickListener {
            // Eugene - This should pass the string array of recovery codes
            // need to assign and get _identity somehow or something
            val codes = Array(20) { i -> (i * i).toString() }
            showRecoveryCodes(codes)
        }
        MFARecovery.GenerateCodesButton.setOnClickListener {
            // Eugene - Generate new codes and set them
        }
        MFARecovery.setOnClickListener {
            hideModal()
        }

        // MFA Authenticate
        IdentityDetailsPage.MfaAuthButton.setOnClickListener {
            MFAAuthenticate.AuthCode?.setText("");
            showModal(MFAAuthenticate)
        }
        MFAAuthenticate.AuthButton.setOnClickListener {
            Authenticate()
        }
        MFAAuthenticate.CloseAuthButton.setOnClickListener {
            hideModal()
        }

        MFASetup.CloseSetupButton.setOnClickListener {
            hideModal()
        }
        MFASetup.MFACode.setOnClickListener {
            if (MFASetup.QRCode.visibility==View.VISIBLE) {
                MFASetup.QRCode.visibility = View.GONE
                MFASetup.SecretCode.visibility = View.VISIBLE
                MFASetup.MFACode.text = "Show QR"
            } else {
                MFASetup.QRCode.visibility = View.VISIBLE
                MFASetup.SecretCode.visibility = View.GONE
                MFASetup.MFACode.text = "Show Code"
            }
        }

        LogDetails.movementMethod = ScrollingMovementMethod()
        ApplicationLogsButton.setOnClickListener {
            LogTypeTitle.text = ("Application Logs")
            LogDetails.text = log_application
            GlobalScope.launch(Dispatchers.IO) {
                val p = Runtime.getRuntime().exec("logcat -d -t 200 --pid=${Process.myPid()}")
                val lines = p.inputStream.bufferedReader().readText()

                Log.d("ziti", "log is ${lines.length} bytes")

                LogDetails.post {
                    LogDetails.text = lines
                }
            }
            toggleSlide(LogPage, "logdetails")
        }

        contextViewModel = ViewModelProvider(this).get(ZitiViewModel::class.java)
        contextViewModel.auths().observe(this, { ar ->
            Log.i("Jeremy", "requesting MFA for ${ar.ztx.name()}")

        })

        contextViewModel.contexts().observe(this, { contextList ->
            IdentityListing.removeAllViews()
            // create, remove cards
            var index = 0
            for (ctx in contextList) {

                /**
                 * Setup items in the list iteself, this can use the value of the iterator
                 */
                val ctxModel = ViewModelProvider(this, ZitiContextModel.Factory(ctx)).get(ctx.name(), ZitiContextModel::class.java)
                val identityitem = IdentityItemView(this, ctxModel, ctx)

                ctxModel.services().observe(this, { serviceList ->
                    identityitem.count = serviceList.count()
                })
                ctxModel.status().observe(this, { state ->
                    identityitem.isOn = state != ZitiContext.Status.Disabled
                })
                identityitem.IdToggleSwitch.setOnCheckedChangeListener { _, state ->
                    ctx.setEnabled(state)
                }
                identityitem.server = ctx.controller()

                /**
                 * On Click listener needs to pull from a value inside of the identityitem to access the values
                 */
                identityitem.setOnClickListener {
                    _identity = identityitem.identity
                    SearchFor.setText("")
                    identityitem.identityModel.name().observe(this, { n ->
                        IdIdentityDetailName.text = n
                    })
                    toggleSlide(IdentityDetailsPage, "identity")
                    IdDetailsEnrollment.text = identityitem.identityModel.status().value?.toString()
                    if (identityitem.identity.getStatus() == ZitiContext.Status.Active) {
                        IdOnOffSwitch.isChecked = true
                    } else {
                        IdOnOffSwitch.isChecked = false
                    }
                    IdOnOffSwitch.setOnCheckedChangeListener { _, state ->
                        identityitem.identity.setEnabled(state)
                    }
                    identityitem.identityModel.status().observe(this, { st ->
                        IdDetailsStatus.text = st.toString()
                    })
                    IdDetailsNetwork.text = identityitem.identity.controller()
                    IdDetailsNetwork.setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Network", IdDetailsNetwork.text.toString())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(applicationContext, IdDetailsNetwork.text.toString() + " has been copied to your clipboard", Toast.LENGTH_LONG).show()
                    }

                    IdentityDetailsPage.MFASwitch.isChecked = _identity.isMFAEnrolled()
                    IdentityDetailsPage.MFASwitch.setOnCheckedChangeListener { _, isChecked ->
                        if (_identity.isMFAEnrolled() && !isChecked) {
                            // TODO prompt for code
                            // and
                            // _identity.removeMFA(code)
                            showModal(MFAAuthenticate)
                            // growl("MFA is off, some services may not be available.")
                        } else if (!_identity.isMFAEnrolled() && isChecked) {
                            GlobalScope.launch {
                                val mfaEnrollment = _identity.enrollMFA()
                                IdentityDetailsPage.post {
                                    try {
                                        var mfr = MultiFormatWriter()
                                        val bitMatrix: BitMatrix = mfr.encode(mfaEnrollment.provisioningUrl, BarcodeFormat.QR_CODE, 280, 280)
                                        val barcodeEncoder = BarcodeEncoder()
                                        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
                                        MFASetup.QRCode.setImageBitmap(bitmap)
                                        val secret = UrlQuerySanitizer(mfaEnrollment.provisioningUrl).getValue("secret")
                                        MFASetup.SecretCode.text = secret
                                        MFASetup.MFALink.setOnClickListener {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mfaEnrollment.provisioningUrl))
                                            startActivity(browserIntent)
                                        }
                                        MFASetup.AuthSetupButton.setOnClickListener {
                                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                            imm.hideSoftInputFromWindow(MFASetup.windowToken, 0)
                                            val code = MFASetup.SetupAuthentication.text.toString()
                                            if (code.length==6) {
                                                GlobalScope.launch {
                                                    val statusCode = _identity.verifyMFA(code).toString()
                                                    Toast.makeText(
                                                        applicationContext,
                                                        statusCode + " code",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    hideModal()
                                                    closeKeyboard()
                                                }
                                            } else {
                                                growl("Invalid Code");
                                            }
                                        }
                                    } catch (e: WriterException) {
                                        e.printStackTrace()
                                    }
                                    showModal(MFASetup)
                                }
                            }
                        }
                    }

                    identityitem.identityModel.services().observe(this, { serviceList ->
                        this.services = serviceList
                        updateServiceList()
                    })

                    SearchFor.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable) {}
                        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                            updateServiceList()
                        }
                    })
                    IdDetailForgetButton.setOnClickListener {

                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Confirm")
                        builder.setMessage("Are you sure you want to delete this identity from your device?")
                        builder.setIcon(android.R.drawable.ic_dialog_alert)

                        builder.setPositiveButton("Yes") { _, _ ->
                            ctxModel.delete()
                            Toast.makeText(applicationContext, ctx.name() + " removed", Toast.LENGTH_LONG).show()
                            toggleSlide(IdentityDetailsPage, "identities")
                        }

                        builder.setNeutralButton("Cancel") { _, _ -> }

                        val alertDialog: AlertDialog = builder.create()
                        alertDialog.setCancelable(false)
                        alertDialog.show()
                    }
                }
                IdentityListing.addView(identityitem)
                index++
            }

            if (index == 0) {
                if (OffButton != null) {
                    TurnOff()
                    OffButton.isClickable = false
                    StateButton.imageAlpha = 144
                }
            } else {
                if (OffButton != null) {
                    OffButton.isClickable = true
                    StateButton.imageAlpha = 255
                }
            }
        })

        contextViewModel.stats().observe(this, {
            setSpeed(it.downRate, DownloadSpeed, DownloadMbps)
            setSpeed(it.upRate, UploadSpeed, UploadMbps)
        })

        prefs = getSharedPreferences("ziti-vpn", Context.MODE_PRIVATE)
        //checkAppList()

        bindService(Intent(applicationContext, ZitiVPNService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun updateServiceList() {
        IdDetailServicesList.removeAllViews()

        sortHow = SortHow.text.toString()
        sortBy = SortBy.text.toString()

        val searchFor = SearchFor.text.toString()

        val filtered = if (searchFor == "") services else services.filter {
            it.name.contains(searchFor) || it.interceptConfig.toString().contains(searchFor)
        }

        val sorter: Comparator<Service> = when(sortBy) {
            "Address" -> compareBy { it.interceptConfig?.addresses?.firstOrNull() }
            "Port" -> compareBy { it.interceptConfig?.portRanges?.firstOrNull() }
            "Protocol" -> compareBy{ it.interceptConfig?.protocols?.firstOrNull() }
            else -> compareBy { it.name }
        }

        if (sortHow == "Asc") {
            filtered.sortedWith(sorter)
        } else {
            filtered.sortedWith(sorter.reversed())
        }

        val totalCount = filtered.size
        val servicesPage = filtered; // .drop((page - 1) * perPage ).take(perPage)
        val totalShowing = servicesPage.size

        IdDetailServicesList.adapter = ServiceAdapter(services)

        for (service in servicesPage) {
            val line = LineView(applicationContext)
            line.label = service.name

            service.interceptConfig?.let {
                line.value = "$it"
            }

            val failingPQ = service.failingPostureChecks()
            if (failingPQ.isNotEmpty()) {
                val msg = """Failing Queries ${failingPQ.entries.joinToString{ "${it.key}:${it.value}"} }"""
                line.WarningImage.setOnClickListener {
                    growl(msg)
                }
            }
            line.DetailsImage.setOnClickListener {
                details(service)
            }
            // IdDetailServicesList.addView(line)
        }

        if (page == 1) {
            var totalPages = (totalCount / perPage) + 1;
        }
    }

    override fun onPause() {
        super.onPause()
        unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        // Ziti.resume()
        bindService(Intent(applicationContext, ZitiVPNService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        updateTunnelState()
    }

    private fun updateTunnelState() {
        val on = vpn?.isVPNActive() ?: false
        updateConnectedView(on)
    }

    private fun updateConnectedView(on: Boolean) {
        OnButton.visibility = if (on) View.VISIBLE else View.GONE
        OffButton.visibility = if (on) View.GONE else View.VISIBLE
    }

    val MB = 1024 * 1024
    val KB = 1024

    fun setSpeed(rate: Double, speed: TextView, label: TextView) {
        val r: Double
        val l: String
        when {
            rate * 8 > MB -> {
                r = (rate * 8) / (1024 * 1024)
                l = "Mbps"
            }
            rate * 8 > KB -> {
                r = (rate * 8) / KB
                l = "Kbps"
            }
            else -> {
                r = rate * 8
                l = "bps"
            }
        }

        speed.text = String.format("%.1f", r)
        label.text = l
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            10169 -> {
                if (resultCode == RESULT_OK)
                    startService(Intent(this, ZitiVPNService::class.java).setAction("start"))
            }
            10168 -> {
                if (resultCode == RESULT_OK)
                    startService(Intent(this, ZitiVPNService::class.java).setAction("stop"))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun showRecoveryCodes(codes: Array<String>) {
        for (code in codes) {
            val lparams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            val tv = TextView(this)
            tv.layoutParams = lparams
            tv.text = code
            MFARecovery.RecoveryCodeList.addView(tv)
        }
        showModal(MFARecovery)
    }

    fun setupMfa(identity: Identity) {
        // Eugene I need identity.mfa object for this.
    }

    /**
     * Fade in a View and prevent touch events under the View from firing
     */
    fun fadeIn(obj: View) {
        obj.visibility = View.VISIBLE
        var fader = ObjectAnimator.ofFloat(obj, "alpha", 0f, 1f).setDuration(duration.toLong())
        var animateTo = ObjectAnimator.ofFloat(obj, "margin", 0f, 20f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator();
        animateTo.interpolator = DecelerateInterpolator()
        fader.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                obj.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        var animatorSet = AnimatorSet()
        animatorSet.play(animateTo).with(fader)
        animatorSet.start()
    }

    /**
     * Fade out a View and remove its touch event prevention
     */
    fun fadeOut(obj: View) {
        var fader = ObjectAnimator.ofFloat(obj, "alpha", 1f, 0f).setDuration(duration.toLong())
        var animateTo = ObjectAnimator.ofFloat(obj, "margin", 20f, 0f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator();
        animateTo.interpolator = DecelerateInterpolator()
        fader.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                obj.visibility = View.GONE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        var animatorSet = AnimatorSet()
        animatorSet.play(animateTo).with(fader)
        animatorSet.start()
    }

    /**
     * Show the growler message for 3 seconds
     */
    fun growl(message: String) {
        // Jeremy animate in growler
        Growler.Message?.text = message
        fadeIn(Growler);
        Growler.postDelayed({
            if (Growler.visibility == View.VISIBLE) {
                fadeOut(Growler)
            }
        }, 3000)
    }

    /**
     * Manually hide the growler from view
     */
    fun hideGrowler() {
        fadeOut(Growler)
    }

    /**
     * Show the details for the service line
     */
    fun details(service: Service) {
        // Eugene I need real values from identity
        DetailModal.NameValue.text = service.name
        DetailModal.UrlValue.text = service.interceptConfig?.addresses?.joinToString() ?: ""
        DetailModal.AddressValue.text = "192.168.1.1"
        DetailModal.PortsValue.text = service.interceptConfig?.portRanges?.joinToString()
        DetailModal.ProtocolsValue.text = service.interceptConfig?.protocols?.joinToString()
        showModal(DetailModal)
    }

    /**
     * Show the modal passed with a faded background and prevent touch events under the modal from occurring
     */
    private fun showModal(modal: View) {
        isModal = true
        modal.visibility = View.VISIBLE
        modal.isClickable = true
        ModalBg.isClickable = true;
        var faderBg = ObjectAnimator.ofFloat(ModalBg, "alpha", 0f, .98f).setDuration(duration.toLong())
        var fader = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).setDuration(duration.toLong())
        var animateTo = ObjectAnimator.ofFloat(modal, "margin", 0f, 20f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator()
        animateTo.interpolator = DecelerateInterpolator()
        fader.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                modal.visibility = View.VISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        faderBg.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                ModalBg.visibility = View.VISIBLE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        var animatorSet = AnimatorSet()
        animatorSet.play(animateTo).with(fader).with(faderBg)
        animatorSet.start()
    }

    /**
     * Determine which model is open and hide it from view, remove background and remove touch stop events
     */
    private fun hideModal() {
        var modal = DetailModal
        if (MFARecovery.visibility==View.VISIBLE) modal = MFARecovery
        if (MFASetup.visibility==View.VISIBLE) modal = MFASetup
        if (MFAAuthenticate.visibility==View.VISIBLE) modal = MFAAuthenticate
        isModal = false
        isModal = true
        ModalBg.isClickable = false;
        modal.isClickable = false
        var faderBg = ObjectAnimator.ofFloat(ModalBg, "alpha", .98f, 0f).setDuration(duration.toLong())
        var fader = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).setDuration(duration.toLong())
        var animateTo = ObjectAnimator.ofFloat(modal, "margin", 20f, 0f).setDuration(duration.toLong())
        fader.interpolator = DecelerateInterpolator()
        animateTo.interpolator = DecelerateInterpolator()
        fader.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                modal.visibility = View.GONE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        fader.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                ModalBg.visibility = View.GONE
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        var animatorSet = AnimatorSet()
        animatorSet.play(animateTo).with(fader).with(faderBg)
        animatorSet.start()
        modal.visibility = View.GONE
    }

    fun Authenticate() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(MFAAuthenticate.windowToken, 0)
        var code = MFAAuthenticate.AuthCode.text.toString()
        if (code.length!=6&&code.length!=8) {
            growl("Invalid Code");
        } else {
            // Eugene - Authenticate the value using auth or recovery based on length
            hideModal()
        }
    }

    fun AuthenticateAtSetup() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(MFASetup.windowToken, 0)
        val code = MFASetup.SetupAuthentication.text.toString()
        if (code.length==6) {
            // Eugene - Authenticate the value
            hideModal()
            closeKeyboard()
        } else {
            growl("Invalid Code");
        }
    }

    fun closeKeyboard()  {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
