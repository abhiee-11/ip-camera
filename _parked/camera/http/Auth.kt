package com.freecam.camera.http

object Auth {
    fun verifyToken(token: String, providedToken: String): Boolean {
        if (token.length != providedToken.length) return false
        var result = 0
        for (i in token.indices) {
            result = result or (token[i].toInt() xor providedToken[i].toInt())
        }
        return result == 0
    }
}