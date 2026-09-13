package com.example.authenticator.feature.token.api

import com.example.authenticator.arch.api.SensitiveAccount

/** 将账户序列化为标准 otpauth 明文 URI，仅供受控导出用例使用。 */
interface OtpUriEncoder {
    fun encode(account: SensitiveAccount): String
}