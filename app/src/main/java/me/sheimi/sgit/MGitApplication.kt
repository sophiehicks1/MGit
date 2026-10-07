package me.sheimi.sgit

import android.app.Application
import android.content.Context
import me.sheimi.android.utils.SecurePrefsException
import me.sheimi.android.utils.SecurePrefsHelper
import me.sheimi.sgit.preference.PreferenceHelper
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.eclipse.jgit.transport.SshSessionFactory
import me.sheimi.sgit.ssh.SGitSessionFactory
import timber.log.Timber
import java.security.Security

/**
 * Custom Application Singleton
 */
open class MGitApplication : Application() {
    var securePrefsHelper: SecurePrefsHelper? = null
    var prefenceHelper: PreferenceHelper? = null


    companion object {
        private lateinit var mContext: Context
        val context: Context
            get() = mContext

        @JvmStatic fun getContext(): MGitApplication {
            return mContext as MGitApplication
        }

        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    override fun onCreate() {
        super.onCreate()
        mContext = applicationContext
        setAppVersionPref()
        prefenceHelper = PreferenceHelper(this)
        try {
            securePrefsHelper = SecurePrefsHelper(this)
        } catch (e: SecurePrefsException) {
            Timber.e(e)
        }
        configureJGit()
    }

    override fun attachBaseContext(base:Context) {
        super.attachBaseContext(base)

        initAcra {
            //core configuration:
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            // each plugin you chose above can be configured in a block like this:
            dialog {
                text = getString(R.string.dialog_error_send_report)
                //opening this block automatically enables the plugin.
            }
            mailSender {
                withMailTo(getString(R.string.crash_report_email))
            }
        }
    }

    private fun configureJGit() {
        // Android has no usable home dir; JGit keeps its user-level config and caches there
        System.setProperty("user.home", filesDir.absolutePath)
        // also covers transports opened without SgitTransportCallback, e.g. submodules
        SshSessionFactory.setInstance(SGitSessionFactory())
    }

    private fun setAppVersionPref() {
        val sharedPreference = getSharedPreferences(
            getString(R.string.preference_file_key),
            Context.MODE_PRIVATE)
        val version = BuildConfig.VERSION_NAME
        sharedPreference
            .edit()
            .putString(getString(R.string.preference_key_app_version), version)
            .apply()
    }
}
