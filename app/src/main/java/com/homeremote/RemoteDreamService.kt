package com.homeremote

import android.service.dreams.DreamService

class RemoteDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(R.layout.dream_remote)
    }
}
