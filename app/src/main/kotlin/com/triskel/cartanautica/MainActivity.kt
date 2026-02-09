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

        // Botones existentes
        val btnAgregar = findViewById<Button>(R.id.btnAgregar)
        val btnVectorLibre = findViewById<Button>(R.id.btnVectorLibre)
        val btnDistancia = findViewById<Button>(R.id.btnDistancia)
        val btnBorrar = findViewById<Button>(R.id.btnBorrar)
        val btnSalir = findViewById<Button>(R.id.btnSalir)

        // Botones nuevos
        val btnCrearPunto = findViewById<Button>(R.id.btnCrearPunto)
        val btnImprimir = findViewById<Button>(R.id.btnImprimir)

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

        btnCrearPunto.setOnClickListener {
            showLatLonDialog()
        }

        btnImprimir.setOnClickListener {
            // Pendiente
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

    // CORREGIDO: diálogo Latitud / Longitud con orden lógico y minutos opcionales
    private fun showLatLonDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lat_lon, null)

        val editLatGrados = dialogView.findViewById<EditText>(R.id.editLatGrados)
        val editLatMinutos = dialogView.findViewById<EditText>(R.id.editLatMinutos)
        val editLonGrados = dialogView.findViewById<EditText>(R.id.editLonGrados)
        val editLonMinutos = dialogView.findViewById<EditText>(R.id.editLonMinutos)

        AlertDialog.Builder(this)
            .setTitle("Crear Punto")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { _, _ ->

                // Latitud
                val latGrados = editLatGrados.text.toString().toDoubleOrNull()
                val latMinutos = editLatMinutos.text.toString().toDoubleOrNull() ?: 0.0

                // Longitud
                val lonGrados = editLonGrados.text.toString().toDoubleOrNull()
                val lonMinutos = editLonMinutos.text.toString().toDoubleOrNull() ?: 0.0

                if (latGrados != null && lonGrados != null) {

                    val latDecimal = latGrados + (latMinutos / 60.0)
                    val lonDecimal = lonGrados + (lonMinutos / 60.0)

                    cartaView.crearPunto(latDecimal, lonDecimal)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
