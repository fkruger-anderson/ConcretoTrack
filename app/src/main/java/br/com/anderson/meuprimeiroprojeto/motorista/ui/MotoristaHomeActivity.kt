package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.anderson.meuprimeiroprojeto.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Tela inicial do motorista: menu inferior com Viagens e Roteiro. */
class MotoristaHomeActivity : AppCompatActivity() {

    /**
     * Mostra o fragmento de Viagens ao abrir e liga a navegação inferior. O `savedInstanceState`
     * evita trocar o fragmento de novo numa recriação (ex.: giro de tela) — o FragmentManager já
     * restaura sozinho o que estava sendo exibido.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motorista_home)

        if (savedInstanceState == null) {
            mostrarFragmento(ViagensFragment())
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_viagens -> {
                    mostrarFragmento(ViagensFragment())
                    true
                }
                R.id.nav_roteiro -> {
                    mostrarFragmento(RoteiroFragment())
                    true
                }
                else -> false
            }
        }
    }

    /** Troca o fragmento exibido no container, usado pelos itens do menu inferior. */
    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.containerFragmento, fragmento)
            .commit()
    }
}
