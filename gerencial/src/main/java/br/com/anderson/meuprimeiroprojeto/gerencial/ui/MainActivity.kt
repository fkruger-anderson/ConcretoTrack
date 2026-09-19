package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Tela inicial do app gerencial: menu inferior com Pedidos, Mapa e Configurações. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            mostrarFragmento(PedidosFragment())
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_pedidos -> {
                    mostrarFragmento(PedidosFragment())
                    true
                }
                R.id.nav_mapa -> {
                    mostrarFragmento(MapaFragment())
                    true
                }
                R.id.nav_configuracoes -> {
                    mostrarFragmento(ConfiguracoesFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.containerFragmento, fragmento)
            .commit()
    }
}
