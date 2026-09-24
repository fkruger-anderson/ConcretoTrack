package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * A tela inicial do app gerencial, com um menu na parte de baixo: Pedidos, Mapa e Configurações.
 *
 * Esta Activity é só a "moldura": o conteúdo de cada aba é um `Fragment` (um pedaço de tela
 * reaproveitável) que é trocado dentro de um espaço reservado no layout (`containerFragmento`).
 */
class MainActivity : AppCompatActivity() {

    /**
     * Chamado pelo Android quando a tela é criada. Mostra a aba Pedidos na primeira abertura e liga
     * o menu de baixo para trocar de aba.
     *
     * O `savedInstanceState` vem preenchido quando a tela está sendo RECRIADA (por exemplo, ao
     * girar o celular). Nesse caso o Android já restaura sozinho a aba que estava aberta, então não
     * se coloca a aba Pedidos de novo por cima.
     */
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

    /**
     * Troca o conteúdo do espaço reservado pelo fragment recebido. `replace` tira o que estava lá e
     * coloca o novo; `commit` confirma a troca.
     */
    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.containerFragmento, fragmento)
            .commit()
    }
}
