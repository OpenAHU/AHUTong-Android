package com.ahu.ahutong.core.common.diagnostics

import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticSanitizerTest {
    @AfterTest fun clearKnownPasswords() = DiagnosticSanitizer.clearKnownPasswords()
    @Test fun detailedLogsHidePasswordFormsAndKeepUsefulCredentials() {
        val examples = listOf(
            "password=secret-value&account=student",
            "password='secret-value with spaces'&account=student",
            "{'password':'secret-value'}",
            "POST https://school.example/login?pwd=secret-value&ticket=keep-ticket",
            "https://school.example/login?%70%61ssword=secret-value&cookie=keep-cookie",
            "{\"payload\":{\"password\":\"secret-value\"},\"token\":\"keep-token\"}",
            "{\"pass\\u0077ord\":\"secret-value\"}",
            "{\"password\":\"secret-value",
            "rsa=secret-value&pl=12&lt=keep-ticket",
            "{\"encrypted_password\":\"secret-value\",\"account\":\"student\"}",
            "<password>secret-value</password>",
            "<input value='secret-value' type='password' name='pwd'>",
            "https://student:secret-value@school.example/"
        )
        examples.forEach { input ->
            val result = DiagnosticSanitizer.passwords(input)
            assertFalse(result.contains("secret-value"), "Password survived in $input")
            assertTrue(result.contains("[已隐藏]"), input)
        }
        val useful = "Cookie: session=keep-cookie\nAuthorization: Bearer keep-token\naccount=student&ticket=keep-ticket"
        assertTrue(DiagnosticSanitizer.passwords(useful).contains("keep-cookie"))
        assertTrue(DiagnosticSanitizer.passwords(useful).contains("keep-token"))
        assertTrue(DiagnosticSanitizer.passwords(useful).contains("account=student"))
        assertTrue(DiagnosticSanitizer.passwords(useful).contains("keep-ticket"))
    }

    @Test fun basicAuthorizationAlwaysHidesItsEmbeddedPassword() {
        val result = DiagnosticSanitizer.passwords("Authorization: Basic dXNlcjpwYXNzd29yZA==")
        assertFalse(result.contains("dXNlcjpwYXNzd29yZA=="))
    }

    @Test fun ordinaryLogsRemoveCredentialsAndIdentityFromHeadersAndNestedData() {
        val input = """
            GET https://school.example/api?ticket=private-ticket&account=private-account
            Cookie: session=private-cookie; extra=private-extra
            Authorization: Bearer private-bearer
            {"payload":{"password":"private-password","username":"private-name","token":"private-token"}}
            {"cookies":["private-array-cookie"]}
            private-address@example.com 1234567890
        """.trimIndent()
        val result = DiagnosticSanitizer.safe(input)
        listOf("private-ticket", "private-account", "private-cookie", "private-extra", "private-bearer",
            "private-password", "private-name", "private-token", "private-array-cookie",
            "private-address@example.com", "1234567890").forEach {
            assertFalse(result.contains(it), "Sensitive value survived: $it\n$result")
        }
        assertTrue(result.contains("school.example/api"))
    }

    @Test fun nestedAndMalformedPasswordValuesAreHiddenAsAWhole() {
        listOf(
            "{\"password\":[\"secret-one\", {\"value\":\"secret-two\"}],\"token\":\"keep-token\"}",
            "{\"password\":{\"value\":\"secret-one\",\"nested\":[\"secret-two\"]},\"token\":\"keep-token\"}",
            "{\"password\":[\"secret-one\", {\"value\":\"secret-two\"}",
            "{\"password_hash\":\"secret-one\",\"confirm_pwd\":\"secret-two\",\"token\":\"keep-token\"}"
        ).forEach {
            val masked = DiagnosticSanitizer.passwords(it)
            assertFalse(masked.contains("secret-one"), masked)
            assertFalse(masked.contains("secret-two"), masked)
        }
        assertTrue(DiagnosticSanitizer.passwords("{\"password\":{},\"token\":\"keep-token\"}").contains("keep-token"))
    }

    @Test fun knownPasswordsAreRemovedFromUnkeyedEchoesAndEncodedCopies() {
        DiagnosticSanitizer.rememberPassword("known secret+value")
        val input = "Echo known secret+value and known+secret%2Bvalue and known%20secret%2Bvalue"
        assertFalse(DiagnosticSanitizer.passwords(input).contains("known"))
        assertFalse(DiagnosticSanitizer.safe(input).contains("known"))
        DiagnosticSanitizer.clearKnownPasswords()
        assertTrue(DiagnosticSanitizer.passwords(input).contains("known secret+value"))
    }
}
