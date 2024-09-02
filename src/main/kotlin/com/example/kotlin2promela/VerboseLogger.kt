package com.example.kotlin2promela

class VerboseLogger {
    companion object {
        var enabled = true
        fun log(msg: String) {
            if (enabled) println(msg)
        }
    }
}