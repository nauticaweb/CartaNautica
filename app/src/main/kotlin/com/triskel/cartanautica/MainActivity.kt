package com.triskel.cartanautica

import android.os.Bundle
import android.widget.Button
import android.os.Build
import android.os.Process
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var cartaView: CartaView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cartaView = findViewById(R.id.cartaView)

        val btnAgregar = findViewById<Button>(R.id.btnAgregar)
        val btnVectorLibre = findViewById<Button>(R.id.btnVectorLibre)
        val btnDistancia = findViewById<Button>(R.id.btnDistancia)
        val btnBorrar = findViewById<Button>(R.id.btnBorrar)
        val btnSalir = findViewById<Button>(R.id.btnSalir)

        btnAgregar.setOnClickListener {
            showRumboDistanciaDialog()
        }

        btnVectorLibre.setOnClickListener {
            cartaView.activarVectorLibre()
        }

        btnDistancia.setOnClickListener {
            showDistanciaDialog()
        }

        btnBorrar.setOnClickListener {
            cartaView.borrarElementoSeleccionado()
        }

        btnSalir.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
                Process.killProcess(Process.myPid())
            }
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

    private fun showDistanciaDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rumbo_distancia, null)
        val editRumbo = dialogView.findViewById<EditText>(R.id.editRumbo)
        val editDistancia = dialogView.findViewById<EditText>(R.id.editDistancia)

        // Ocultamos rumbo, solo queremos distancia
        editRumbo.visibility = EditText.GONE

        AlertDialog.Builder(this)
            .setTitle("Distancia")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { _, _ ->
                val distancia = editDistancia.text.toString().toFloatOrNull()
                if (distancia != null) {
                    cartaView.prepararCirculo(distancia)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}