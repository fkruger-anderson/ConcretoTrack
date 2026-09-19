package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import kotlinx.coroutines.launch

/**
 * Mostra os dados da viagem que o gerente atribuiu (pedido, endereço, m³, caminhão — nada disso
 * é editável aqui) e deixa o motorista confirmar o início do trânsito.
 */
class IniciarTransitoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
    }

    private lateinit var viagemId: String
    private lateinit var botaoIniciar: Button
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iniciar_transito)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        botaoIniciar = findViewById(R.id.botaoIniciar)
        textErro = findViewById(R.id.textErro)
        botaoIniciar.setOnClickListener { tentarIniciar() }

        carregarDadosDaViagem()
    }

    private fun carregarDadosDaViagem() {
        lifecycleScope.launch {
            val viagem = try {
                ViagemRepositorio.buscarPorId(viagemId)
            } catch (erro: Exception) {
                null
            }
            if (viagem == null) {
                mostrarErro(getString(R.string.iniciar_transito_erro_carregar))
                return@launch
            }
            exibirViagem(viagem)
        }
    }

    private suspend fun exibirViagem(viagem: ViagemRemota) {
        val pedido = try {
            PedidoRepositorio.buscarPorId(viagem.pedidoId)
        } catch (erro: Exception) {
            null
        }
        findViewById<TextView>(R.id.textDestinatario).text = pedido?.destinatarioNome ?: ""
        findViewById<TextView>(R.id.textEndereco).text = pedido?.enderecoResumido() ?: ""
        findViewById<TextView>(R.id.textMetrosCubicos).text = getString(
            R.string.iniciar_transito_m3_formato, formatarM3(viagem.metrosCubicos)
        )
        findViewById<TextView>(R.id.textCaminhao).text = getString(
            R.string.iniciar_transito_caminhao_formato, viagem.caminhaoPlaca
        )
    }

    private fun tentarIniciar() {
        // Não espera confirmação do servidor — funciona mesmo sem sinal (ver o comentário em
        // ViagemRepositorio.iniciarTransito).
        ViagemRepositorio.iniciarTransito(viagemId)

        Toast.makeText(this, R.string.iniciar_transito_sucesso, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, ViagemEmAndamentoActivity::class.java)
            .putExtra(ViagemEmAndamentoActivity.EXTRA_VIAGEM_ID, viagemId)
        startActivity(intent)
        finish()
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
