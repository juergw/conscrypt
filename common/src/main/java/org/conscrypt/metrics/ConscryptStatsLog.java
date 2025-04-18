/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.conscrypt.metrics;

import org.conscrypt.Internal;
import org.conscrypt.metrics.GeneratedStatsLog;

/**
 * Reimplement with reflection calls the logging class,
 * generated by frameworks/statsd.
 * <p>
 * In case an atom is updated, generate the new wrapper with stats-log-api-gen
 * tool as shown below and update the write methods to use ReflexiveStatsEvent
 * and ReflexiveStatsLog.
 * <p>
 * $ stats-log-api-gen \
 *   --java "common/src/main/java/org/conscrypt/metrics/ConscryptStatsLog.java" \
 *   --module conscrypt \
 *   --javaPackage org.conscrypt.metrics \
 *   --javaClass ConscryptStatsLog
 * <p>
 * This class is swapped with the generated wrapper for GMSCore. For this
 * reason, the methods defined here should be identical to the generated
 * methods from the wrapper. Do not add new method here, do not change the type
 * of the parameters.
 **/
@Internal
public final class ConscryptStatsLog {
    // clang-format off

    // Constants for atom codes.

    /**
     * TlsHandshakeReported tls_handshake_reported<br>
     * Usage: StatsLog.write(StatsLog.TLS_HANDSHAKE_REPORTED, boolean success, int protocol, int cipher_suite, int handshake_duration_millis, int source, int[] uid);<br>
     */
    public static final int TLS_HANDSHAKE_REPORTED = 317;

    /**
     * CertificateTransparencyLogListStateChanged certificate_transparency_log_list_state_changed<br>
     * Usage: StatsLog.write(StatsLog.CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED, int status, int loaded_compat_version, int min_compat_version, int major_version, int minor_version);<br>
     */
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED = 934;

    /**
     * ConscryptServiceUsed conscrypt_service_used<br>
     * Usage: StatsLog.write(StatsLog.CONSCRYPT_SERVICE_USED, int algorithm, int cipher, int mode, int padding);<br>
     */
    public static final int CONSCRYPT_SERVICE_USED = 965;

    /**
     * CertificateTransparencyVerificationReported certificate_transparency_verification_reported<br>
     * Usage: StatsLog.write(StatsLog.CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED, int result, int reason, int policy_compatibility_version, int major_version, int minor_version, int num_cert_scts, int num_ocsp_scts, int num_tls_scts);<br>
     */
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED = 989;

    // Constants for enum values.

    // Values for TlsHandshakeReported.protocol
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__UNKNOWN_PROTO = 0;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__SSL_V3 = 1;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__TLS_V1 = 2;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__TLS_V1_1 = 3;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__TLS_V1_2 = 4;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__TLS_V1_3 = 5;
    public static final int TLS_HANDSHAKE_REPORTED__PROTOCOL__TLS_PROTO_FAILED = 65535;

    // Values for TlsHandshakeReported.cipher_suite
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__UNKNOWN_CIPHER_SUITE = 0;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_RSA_WITH_3DES_EDE_CBC_SHA = 10;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_RSA_WITH_AES_128_CBC_SHA = 47;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_RSA_WITH_AES_256_CBC_SHA = 53;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_PSK_WITH_AES_128_CBC_SHA = 140;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_PSK_WITH_AES_256_CBC_SHA = 141;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_RSA_WITH_AES_128_GCM_SHA256 = 156;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_RSA_WITH_AES_256_GCM_SHA384 = 157;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_AES_128_GCM_SHA256 = 4865;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_AES_256_GCM_SHA384 = 4866;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_CHACHA20_POLY1305_SHA256 = 4867;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA = 49161;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA = 49162;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA = 49171;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA = 49172;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 = 49195;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384 = 49196;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 = 49199;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 = 49200;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA = 49205;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA = 49206;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256 = 52392;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256 = 52393;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256 = 52396;
    public static final int TLS_HANDSHAKE_REPORTED__CIPHER_SUITE__TLS_CIPHER_FAILED = 65535;

    // Values for TlsHandshakeReported.source
    public static final int TLS_HANDSHAKE_REPORTED__SOURCE__SOURCE_UNKNOWN = 0;
    public static final int TLS_HANDSHAKE_REPORTED__SOURCE__SOURCE_MAINLINE = 1;
    public static final int TLS_HANDSHAKE_REPORTED__SOURCE__SOURCE_GMS = 2;
    public static final int TLS_HANDSHAKE_REPORTED__SOURCE__SOURCE_UNBUNDLED = 3;

    // Values for CertificateTransparencyLogListStateChanged.status
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__STATUS__STATUS_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__STATUS__STATUS_SUCCESS = 1;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__STATUS__STATUS_NOT_FOUND = 2;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__STATUS__STATUS_PARSING_FAILED = 3;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__STATUS__STATUS_EXPIRED = 4;

    // Values for CertificateTransparencyLogListStateChanged.loaded_compat_version
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__LOADED_COMPAT_VERSION__COMPAT_VERSION_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__LOADED_COMPAT_VERSION__COMPAT_VERSION_V1 = 1;

    // Values for CertificateTransparencyLogListStateChanged.min_compat_version
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__MIN_COMPAT_VERSION__COMPAT_VERSION_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_LOG_LIST_STATE_CHANGED__MIN_COMPAT_VERSION__COMPAT_VERSION_V1 = 1;

    // Values for ConscryptServiceUsed.algorithm
    public static final int CONSCRYPT_SERVICE_USED__ALGORITHM__UNKNOWN_ALGORITHM = 0;
    public static final int CONSCRYPT_SERVICE_USED__ALGORITHM__CIPHER = 1;
    public static final int CONSCRYPT_SERVICE_USED__ALGORITHM__SIGNATURE = 2;

    // Values for ConscryptServiceUsed.cipher
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__UNKNOWN_CIPHER = 0;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__AES = 1;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__DES = 2;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__DESEDE = 3;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__DSA = 4;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__BLOWFISH = 5;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__CHACHA20 = 6;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__RSA = 7;
    public static final int CONSCRYPT_SERVICE_USED__CIPHER__ARC4 = 8;

    // Values for ConscryptServiceUsed.mode
    public static final int CONSCRYPT_SERVICE_USED__MODE__NO_MODE = 0;
    public static final int CONSCRYPT_SERVICE_USED__MODE__CBC = 1;
    public static final int CONSCRYPT_SERVICE_USED__MODE__CTR = 2;
    public static final int CONSCRYPT_SERVICE_USED__MODE__ECB = 3;
    public static final int CONSCRYPT_SERVICE_USED__MODE__CFB = 4;
    public static final int CONSCRYPT_SERVICE_USED__MODE__CTS = 5;
    public static final int CONSCRYPT_SERVICE_USED__MODE__GCM = 6;
    public static final int CONSCRYPT_SERVICE_USED__MODE__GCM_SIV = 7;
    public static final int CONSCRYPT_SERVICE_USED__MODE__OFB = 8;
    public static final int CONSCRYPT_SERVICE_USED__MODE__POLY1305 = 9;

    // Values for ConscryptServiceUsed.padding
    public static final int CONSCRYPT_SERVICE_USED__PADDING__NO_PADDING = 0;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__OAEP_SHA512 = 1;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__OAEP_SHA384 = 2;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__OAEP_SHA256 = 3;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__OAEP_SHA224 = 4;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__OAEP_SHA1 = 5;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__PKCS1 = 6;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__PKCS5 = 7;
    public static final int CONSCRYPT_SERVICE_USED__PADDING__ISO10126 = 8;

    // Values for CertificateTransparencyVerificationReported.result
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_SUCCESS = 1;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_GENERIC_FAILURE = 2;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_FAILURE_NO_SCTS_FOUND = 3;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_FAILURE_SCTS_NOT_COMPLIANT = 4;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_FAIL_OPEN_NO_LOG_LIST_AVAILABLE = 5;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__RESULT__RESULT_FAIL_OPEN_LOG_LIST_NOT_COMPLIANT = 6;

    // Values for CertificateTransparencyVerificationReported.reason
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__REASON__REASON_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__REASON__REASON_DEVICE_WIDE_ENABLED = 1;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__REASON__REASON_SDK_TARGET_DEFAULT_ENABLED = 2;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__REASON__REASON_NSCONFIG_APP_OPT_IN = 3;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__REASON__REASON_NSCONFIG_DOMAIN_OPT_IN = 4;

    // Values for CertificateTransparencyVerificationReported.policy_compatibility_version
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__POLICY_COMPATIBILITY_VERSION__COMPAT_VERSION_UNKNOWN = 0;
    public static final int CERTIFICATE_TRANSPARENCY_VERIFICATION_REPORTED__POLICY_COMPATIBILITY_VERSION__COMPAT_VERSION_V1 = 1;

    // Write methods
    public static void write(int code, boolean arg1, int arg2, int arg3, int arg4, int arg5, int[] arg6) {
        final ReflexiveStatsEvent.Builder builder = ReflexiveStatsEvent.newBuilder();
        builder.setAtomId(code);
        builder.writeBoolean(arg1);
        builder.writeInt(arg2);
        builder.writeInt(arg3);
        builder.writeInt(arg4);
        builder.writeInt(arg5);
        builder.writeIntArray(null == arg6 ? new int[0] : arg6);

        builder.usePooledBuffer();
        ReflexiveStatsLog.write(builder.build());
    }

    public static void write(int code, int arg1, int arg2, int arg3, int arg4) {
        final ReflexiveStatsEvent.Builder builder = ReflexiveStatsEvent.newBuilder();
        builder.setAtomId(code);
        builder.writeInt(arg1);
        builder.writeInt(arg2);
        builder.writeInt(arg3);
        builder.writeInt(arg4);

        builder.usePooledBuffer();
        ReflexiveStatsLog.write(builder.build());
    }

    public static void write(int code, int arg1, int arg2, int arg3, int arg4, int arg5) {
        final ReflexiveStatsEvent.Builder builder = ReflexiveStatsEvent.newBuilder();
        builder.setAtomId(code);
        builder.writeInt(arg1);
        builder.writeInt(arg2);
        builder.writeInt(arg3);
        builder.writeInt(arg4);
        builder.writeInt(arg5);

        builder.usePooledBuffer();
        ReflexiveStatsLog.write(builder.build());
    }

    public static void write(int code, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, int arg8) {
        final ReflexiveStatsEvent.Builder builder = ReflexiveStatsEvent.newBuilder();
        builder.setAtomId(code);
        builder.writeInt(arg1);
        builder.writeInt(arg2);
        builder.writeInt(arg3);
        builder.writeInt(arg4);
        builder.writeInt(arg5);
        builder.writeInt(arg6);
        builder.writeInt(arg7);
        builder.writeInt(arg8);

        builder.usePooledBuffer();
        ReflexiveStatsLog.write(builder.build());
    }

    // clang-format on
}
