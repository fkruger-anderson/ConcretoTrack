package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CaminhaoRepositorio
import kotlinx.coroutines.launch

class CadastroCaminhaoActivity : AppCompatActivity() {

    companion object {
        // Cobre placa antiga (LLL-DDDD) e Mercosul (LLL-D-L-DD): a 5ª posição pode ser letra ou número.
        private val REGEX_PLACA = Regex("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$")
    }

    private lateinit var editPlaca: EditText
    private lateinit var editMarca: EditText
    private lateinit var editModelo: EditText
    private lateinit var editAno: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_caminhao)

        editPlaca = findViewById(R.id.editPlaca)
        editMarca = findViewById(R.id.editMarca)
        editModelo = findViewById(R.id.editModelo)
        editAno = findViewById(R.id.editAno)
        botaoCriar = findViewById(R.id.botaoCriar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoCriar.setOnClickListener { tentarCriar() }
    }

    private fun tentarCriar() {
        val placa = editPlaca.text.toString().trim().uppercase()
        if (!REGEX_PLACA.matches(placa)) {
            editPlaca.error = getString(R.string.cadastro_caminhao_erro_placa)
            return
        }

        val marca = editMarca.text.toString().trim()
        val modelo = editModelo.text.toString().trim()
        if (marca.isEmpty() || modelo.isEmpty()) {
            mostrarErro(getString(R.string.cadastro_caminhao_erro_marca_modelo))
            return
        }

        val ano = editAno.text.toString().trim().toIntOrNull()
        if (ano == null || ano < 1970 || ano > 2100) {
            editAno.error = getString(R.string.cadastro_caminhao_erro_ano)
            return
        }

        mostrarCarregando(true)
        lifecycleScope.launch {
            try {
                CaminhaoRepositorio.cadastrar(placa, marca, modelo, ano)
                Toast.makeText(
                    this@CadastroCaminhaoActivity, R.string.cadastro_caminhao_sucesso, Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (erro: CaminhaoRepositorio.PlacaJaCadastradaException) {
                mostrarErro(getString(R.string.cadastro_caminhao_erro_placa_duplicada))
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.cadastro_caminhao_erro_generico))
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        botaoCriar.isEnabled = !carregando
        if (carregando) textErro.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
