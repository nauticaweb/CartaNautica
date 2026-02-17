package com.triskel.cartanautica

import android.os.Bundle
import android.widget.Button
import android.os.Build
import android.os.Process
import android.widget.EditText
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var cartaView: CartaView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cartaView = findViewById(R.id.cartaView)

        // Botones
        val btnAgregar = findViewById<Button>(R.id.btnAgregar)
        val btnDemora = findViewById<Button>(R.id.btnDemora)
        val btnVectorLibre = findViewById<Button>(R.id.btnVectorLibre)
        val btnDistancia = findViewById<Button>(R.id.btnDistancia)
        val btnBorrar = findViewById<Button>(R.id.btnBorrar)
        val btnSalir = findViewById<Button>(R.id.btnSalir)
        val btnCrearPunto = findViewById<Button>(R.id.btnCrearPunto)
        val btnImprimir = findViewById<Button>(R.id.btnImprimir)

        btnAgregar.setOnClickListener {
            showRumboDistanciaDialog()
        }

        btnDemora.setOnClickListener {
            showDemoraDialog()
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
            cartaView.imprimirCartaPdf("CartaNautica.pdf")
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
                    cartaView.prepararVector(rumbo, distancia, CartaView.TipoVector.RUMBO)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDemoraDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rumbo_distancia, null)
        val editRumbo = dialogView.findViewById<EditText>(R.id.editRumbo)
        val editDistancia = dialogView.findViewById<EditText>(R.id.editDistancia)

        // Ocultamos distancia
        editDistancia.visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle("Demora")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { _, _ ->

                val demora = editRumbo.text.toString().toFloatOrNull()

                if (demora != null) {

                    // Normalización 0–360
                    val demoraNormalizada = ((demora % 360f) + 360f) % 360f

                    // Invertimos rumbo (demora → línea)
                    val rumboInvertido = (demoraNormalizada + 180f) % 360f

                    // Distancia fija 10 millas
                    cartaView.prepararVector(rumboInvertido, 10f, CartaView.TipoVector.DEMORA)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDistanciaDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_rumbo_distancia, null)
        val editRumbo = dialogView.findViewById<EditText>(R.id.editRumbo)
        val editDistancia = dialogView.findViewById<EditText>(R.id.editDistancia)

        editRumbo.visibility = View.GONE

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

                val latGrados = editLatGrados.text.toString().toDoubleOrNull()
                val latMinutos = editLatMinutos.text.toString().toDoubleOrNull() ?: 0.0
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
