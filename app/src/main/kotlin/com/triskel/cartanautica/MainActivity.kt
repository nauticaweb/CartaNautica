package com.triskel.cartanautica

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var cartaView: CartaView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cartaView = findViewById(R.id.cartaView)
        val btnAgregar = findViewById<Button>(R.id.btnAgregar) // coincide con XML

        btnAgregar.setOnClickListener {
            showRumboDistanciaDialog()
        }
    }

    private fun showRumboDistanciaDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rumbo_distancia, null)
        val editRumbo = dialogView.findViewById<EditText>(R.id.editRumbo)
        val editDistancia = dialogView.findViewById<EditText>(R.id.editDistancia)

        AlertDialog.Builder(this)
            .setTitle("Rumbo y Distancia")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { _, _ ->
                val rumbo = editRumbo.text.toString().toFloatOrNull()
                val distancia = editDistancia.text.toString().toFloatOrNull()
                if (rumbo != null && distancia != null) {
                    cartaView.prepararVector(rumbo, distancia)

                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
