package com.shinji.cocoa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

object ChromeAuthHelper {

    private const val TAG = "ChromeAuthHelper"
    private const val CHROME_PACKAGE = "com.android.chrome"

    fun openChromeAuth(context: Context, url: String) {
        val uri = Uri.parse(url)
        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(CHROME_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(chromeIntent)
            Log.i(TAG, "Launched Google Chrome for authentication: $url")
        } catch (e: Exception) {
            Log.w(TAG, "Google Chrome package ($CHROME_PACKAGE) not found. Falling back to default browser: ${e.message}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed to launch default browser: ${fallbackEx.message}")
                Toast.makeText(context, "ブラウザを起動できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
