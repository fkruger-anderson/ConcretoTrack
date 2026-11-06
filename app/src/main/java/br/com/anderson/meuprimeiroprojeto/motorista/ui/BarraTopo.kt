package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.R
import com.google.android.material.appbar.MaterialToolbar

fun AppCompatActivity.configurarBarraPrimeiroNivel() {
    val toolbarTopo = findViewById<MaterialToolbar>(R.id.toolbarTopo)
    toolbarTopo.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.itemFechar) {
            finish()
            true
        } else {
            false
        }
    }
}

fun AppCompatActivity.configurarBarraSegundoNivel() {
    val toolbarTopo = findViewById<MaterialToolbar>(R.id.toolbarTopo)
    toolbarTopo.setNavigationIcon(R.drawable.ic_voltar)
    toolbarTopo.setNavigationContentDescription(R.string.voltar)
    toolbarTopo.setNavigationOnClickListener { finish() }
    toolbarTopo.setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.itemFechar) {
            val intent = Intent(this, MotoristaHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            true
        } else {
            false
        }
    }
}
