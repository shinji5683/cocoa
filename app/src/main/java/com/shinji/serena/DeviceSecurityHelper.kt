package com.shinji.serena

import android.content.Context
import android.os.Build
import java.io.File

class DeviceSecurityHelper(private val context: Context) {

    enum class SecurityStatus {
        SECURE_OFFICIAL,      // 正常（Google公式・最新OS互換）
        UPDATE_RECOMMENDED,   // OS更新推奨（API 30未満）
        MODIFIED_ENVIRONMENT  // 改造OS・Root化・未検証環境
    }

    data class SecurityCheckResult(
        val status: SecurityStatus,
        val message: String,
        val detailMessage: String
    )

    fun isCanaryOrPreviewBuild(): Boolean {
        val codename = Build.VERSION.CODENAME
        val isCurDevelopmentApi = Build.VERSION.SDK_INT >= 10000 || Build.VERSION.SDK_INT == Build.VERSION_CODES.CUR_DEVELOPMENT
        return isCurDevelopmentApi || (codename != "REL" && (
            codename.equals("Baklava", ignoreCase = true) ||
            codename.startsWith("C", ignoreCase = true) ||
            codename.contains("Canary", ignoreCase = true) ||
            codename.contains("Preview", ignoreCase = true)
        ))
    }

    fun checkDeviceSecurity(): SecurityCheckResult {
        val isCustomOrRooted = isCustomOrRootedBuild()
        val isLegacyOs = Build.VERSION.SDK_INT < Build.VERSION_CODES.R // API 30未満
        val isCanary = isCanaryOrPreviewBuild()

        return when {
            isCustomOrRooted -> {
                SecurityCheckResult(
                    status = SecurityStatus.MODIFIED_ENVIRONMENT,
                    message = context.getString(R.string.security_status_custom_title),
                    detailMessage = context.getString(R.string.security_status_custom_detail)
                )
            }
            isCanary -> {
                SecurityCheckResult(
                    status = SecurityStatus.SECURE_OFFICIAL,
                    message = context.getString(R.string.security_status_canary_title),
                    detailMessage = context.getString(
                        R.string.security_status_canary_detail_fmt,
                        Build.VERSION.CODENAME,
                        Build.VERSION.SDK_INT
                    )
                )
            }
            isLegacyOs -> {
                SecurityCheckResult(
                    status = SecurityStatus.UPDATE_RECOMMENDED,
                    message = context.getString(R.string.security_status_update_title),
                    detailMessage = context.getString(R.string.security_status_update_detail)
                )
            }
            else -> {
                SecurityCheckResult(
                    status = SecurityStatus.SECURE_OFFICIAL,
                    message = context.getString(R.string.security_status_secure_title),
                    detailMessage = context.getString(
                        R.string.security_status_secure_detail_fmt,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT
                    )
                )
            }
        }
    }

    private fun isCustomOrRootedBuild(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        return try {
            val pathEnv = System.getenv("PATH") ?: return false
            val targetBin = String(charArrayOf('s', 'u'))
            pathEnv.split(":").any { dir ->
                val f = File(dir, targetBin)
                f.exists() && f.canExecute()
            }
        } catch (e: Exception) {
            false
        }
    }
}


