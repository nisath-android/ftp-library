package com.naminfo.ftpclient.core

import java.util.regex.Pattern
import kotlinx.coroutines.*

class PhoneNumberHandler {

    private val base62Chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    // Function to encode phone number into a shorter string (Base62)
    fun encodePhoneNumber(phoneNumber: String): String? {
        val phoneNumberInt = phoneNumber.toLongOrNull() ?: return null
        return toBase62(phoneNumberInt)
    }

    // Function to decode the shorter string back to the original phone number
    fun decodePhoneNumber(encodedString: String): String? {
        val decodedNumber = fromBase62(encodedString) ?: return null
        return decodedNumber.toString()
    }

    // Helper function to convert an integer to a Base62 string
    private fun toBase62(number: Long): String {
        var num = number
        val base62String = StringBuilder()

        while (num > 0) {
            val remainder = (num % 62).toInt()
            base62String.insert(0, base62Chars[remainder])
            num /= 62
        }

        return base62String.toString()
    }

    // Helper function to convert a Base62 string back to an integer
    private fun fromBase62(base62String: String): Long? {
        var number: Long = 0

        for (char in base62String) {
            val index = base62Chars.indexOf(char)
            if (index == -1) return null  // Invalid character
            number = number * 62 + index
        }

        return number
    }

    // Function to extract phone numbers from the filename asynchronously
    fun extractPhoneNumbers(filename: String, fileType: String, fileExtension: String, completion: (List<String?>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pattern = Pattern.compile("($fileType)_([a-zA-Z0-9]+)_([a-zA-Z0-9]+)_\\d+\\.($fileExtension)")
                val matcher = pattern.matcher(filename)

                if (!matcher.find()) {
                    println("No match found")
                    withContext(Dispatchers.Main) { completion(emptyList()) }
                    return@launch
                }

                val extractedFileType = matcher.group(1)  // Extracted file type
                val encodedPhoneNumber1 = matcher.group(2) // First phone number
                val encodedPhoneNumber2 = matcher.group(3) // Second phone number
                val extractedFileExtension = matcher.group(4) // Extracted file extension

                val decodedPhoneNumber1 = decodePhoneNumber(encodedPhoneNumber1)
                val decodedPhoneNumber2 = decodePhoneNumber(encodedPhoneNumber2)

                println("File Type: $extractedFileType, Extension: $extractedFileExtension")  // Debugging

                withContext(Dispatchers.Main) {
                    completion(listOf(decodedPhoneNumber1, decodedPhoneNumber2))
                }
            } catch (e: Exception) {
                println("Error processing filename: ${e.message}")
                withContext(Dispatchers.Main) { completion(emptyList()) }
            }
        }
    }


}
