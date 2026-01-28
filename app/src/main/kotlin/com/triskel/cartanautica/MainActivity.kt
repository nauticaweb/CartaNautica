package com.triskel.cartanautica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargamos el layout que contiene el CartaView
        setContentView(R.layout.activity_main)
    }
}
