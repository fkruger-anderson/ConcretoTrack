package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.anderson.meuprimeiroprojeto.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * A tela inicial do motorista, com um menu na parte de baixo (Viagens e Roteiro).
 *
 * Esta Activity é só a "moldura": o conteúdo de cada aba é um `Fragment` (um pedaço de tela
 * reaproveitável) que é trocado dentro de um espaço reservado no layout (`containerFragmento`).
 * A aba Viagens é o [ViagensFragment] e a aba Roteiro é o [RoteiroFragment].
 */
class MotoristaHomeActivity : AppCompatActivity() {

    /**
     * Chamado pelo Android quando a tela é criada.
     *
     * Mostra a aba Viagens na primeira abertura e liga o menu de baixo para trocar de aba.
     *
     * O `savedInstanceState` vem preenchido quando a tela está sendo RECRIADA (por exemplo, depois
     * de girar o celular). Nesse caso o Android já restaura sozinho a aba que estava aberta, então
     * não se coloca a aba Viagens de novo por cima.
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

    /**
     * Troca o conteúdo do espaço reservado pelo fragment recebido. O `supportFragmentManager` é
     * quem cuida dos fragments da tela; `replace` tira o que estava lá e coloca o novo, e `commit`
     * confirma a troca.
     */
    private fun mostrarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.containerFragmento, fragmento)
            .commit()
    }
}
