package com.shinji.cocoa

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

    fun checkDeviceSecurity(): SecurityCheckResult {
        val isCustomOrRooted = isCustomOrRootedBuild()
        val isLegacyOs = Build.VERSION.SDK_INT < Build.VERSION_CODES.R // API 30未満

        return when {
            isCustomOrRooted -> {
                SecurityCheckResult(
                    status = SecurityStatus.MODIFIED_ENVIRONMENT,
                    message = "⚠️ 未検証・カスタム環境の通知",
                    detailMessage = "Google公式互換性チェックのため、標準ビルド環境でのご利用を推奨します。"
                )
            }
            isLegacyOs -> {
                SecurityCheckResult(
                    status = SecurityStatus.UPDATE_RECOMMENDED,
                    message = "💡 最新OSへのアップデート推奨",
                    detailMessage = "安全かつ快適なGoogleサービスをご利用いただくため、Android 11以上への更新をおすすめします。"
                )
            }
            else -> {
                SecurityCheckResult(
                    status = SecurityStatus.SECURE_OFFICIAL,
                    message = "🟢 安全な環境です",
                    detailMessage = "Google公式互換ビルド (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}) で正常に稼働しています。"
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
