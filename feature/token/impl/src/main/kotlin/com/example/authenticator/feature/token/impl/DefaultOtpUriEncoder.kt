package com.example.authenticator.feature.token.impl

import com.example.authenticator.arch.api.EncodingCodec
import com.example.authenticator.arch.api.OtpAlgorithm
import com.example.authenticator.arch.api.SensitiveAccount
import com.example.authenticator.feature.token.api.OtpUriEncoder

/** 将账户序列化为标准 otpauth://totp 明文 URI。 */
internal class DefaultOtpUriEncoder(private val codec: EncodingCodec) : OtpUriEncoder {

    override fun encode(account: SensitiveAccount): String {
        val summary = account.summary
        val params = account.parameters
        val label = if (summary.issuer.isNotEmpty()) "${summary.issuer}:${summary.accountName}" else summary.accountName
        val secret = codec.base32EncodeNoPadding(account.secret.copy())
        val algorithm = when (params.algorithm) {
            OtpAlgorithm.SHA1 -> "SHA1"
            OtpAlgorithm.SHA256 -> "SHA256"
            OtpAlgorithm.SHA512 -> "SHA512"
        }
        val sb = StringBuilder()
        sb.append("otpauth://totp/").append(codec.percentEncode(label))
        sb.append("?secret=").append(secret)
        if (summary.issuer.isNotEmpty()) {
            sb.append("&issuer=").append(codec.percentEncode(summary.issuer))
        }
        sb.append("&algorithm=").append(algorithm)
        sb.append("&digits=").append(params.digits)
        sb.append("&period=").append(params.periodSeconds)
        return sb.toString()
    }
}