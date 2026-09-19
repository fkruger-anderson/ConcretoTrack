package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaComId
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.CaminhaoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.PedidoComStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** O gerente escolhe pedido + motorista + caminhão + quantidade; a viagem nasce "atribuída". */
class AtribuirViagemActivity : AppCompatActivity() {

    companion object {
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    private lateinit var spinnerPedido: Spinner
    private lateinit var spinnerMotorista: Spinner
    private lateinit var spinnerCaminhao: Spinner
    private lateinit var editQuantidadeM3: EditText
    private lateinit var botaoAtribuir: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView
    private lateinit var textSemDados: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_atribuir_viagem)

        spinnerPedido = findViewById(R.id.spinnerPedido)
        spinnerMotorista = findViewById(R.id.spinnerMotorista)
        spinnerCaminhao = findViewById(R.id.spinnerCaminhao)
        editQuantidadeM3 = findViewById(R.id.editQuantidadeM3)
        botaoAtribuir = findViewById(R.id.botaoAtribuir)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)
        textSemDados = findViewById(R.id.textSemDados)

        botaoAtribuir.setOnClickListener { tentarAtribuir() }

        carregarListas()
    }

    private fun carregarListas() {
        lifecycleScope.launch {
            val pedidos = try {
                PedidoRepositorio.listarComStatus().filter { it.saldo > 0.0 }
            } catch (erro: Exception) {
                emptyList()
            }
            val motoristas = try {
                MotoristaRepositorio.listar()
            } catch (erro: Exception) {
                emptyList()
            }
            val caminhoes = try {
                CaminhaoRepositorio.listar()
            } catch (erro: Exception) {
                emptyList()
            }

            if (pedidos.isEmpty() || motoristas.isEmpty() || caminhoes.isEmpty()) {
                textSemDados.text = getString(R.string.atribuir_sem_dados)
                textSemDados.visibility = View.VISIBLE
                botaoAtribuir.isEnabled = false
                return@launch
            }

            spinnerPedido.adapter = ArrayAdapter(
                this@AtribuirViagemActivity, android.R.layout.simple_spinner_dropdown_item, pedidos
            )
            spinnerMotorista.adapter = ArrayAdapter(
                this@AtribuirViagemActivity, android.R.layout.simple_spinner_dropdown_item, motoristas
            )
            spinnerCaminhao.adapter = ArrayAdapter(
                this@AtribuirViagemActivity, android.R.layout.simple_spinner_dropdown_item, caminhoes
            )
        }
    }

    private fun tentarAtribuir() {
        val pedido = spinnerPedido.selectedItem as? PedidoComStatus ?: return
        val motorista = spinnerMotorista.selectedItem as? MotoristaComId ?: return
        val caminhao = spinnerCaminhao.selectedItem as? Caminhao ?: return

        val quantidadeM3 = editQuantidadeM3.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (quantidadeM3 == null || quantidadeM3 <= 0.0) {
            editQuantidadeM3.error = getString(R.string.atribuir_erro_m3)
            return
        }

        botaoAtribuir.isEnabled = false
        textErro.visibility = View.GONE
        lifecycleScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    ViagemRepositorio.atribuir(
                        pedidoId = pedido.id,
                        motoristaUid = motorista.uid,
                        motoristaNome = motorista.perfil.nome,
                        motoristaCpf = motorista.perfil.cpf,
                        caminhaoPlaca = caminhao.placa,
                        metrosCubicos = quantidadeM3
                    )
                }
                Toast.makeText(this@AtribuirViagemActivity, R.string.atribuir_sucesso, Toast.LENGTH_LONG).show()
                finish()
            } catch (erro: ViagemRepositorio.SaldoInsuficienteException) {
                mostrarErro(getString(R.string.atribuir_erro_saldo))
            } catch (erro: TimeoutCancellationException) {
                mostrarErro(getString(R.string.atribuir_erro_conexao))
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.atribuir_erro_generico))
            } finally {
                botaoAtribuir.isEnabled = true
            }
        }
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
