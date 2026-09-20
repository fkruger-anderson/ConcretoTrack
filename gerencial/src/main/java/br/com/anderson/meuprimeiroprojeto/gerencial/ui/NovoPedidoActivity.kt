package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Uf
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.NovoPedidoUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.NovoPedidoViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Cadastro de um novo pedido: dados do destinatário (documento, nome, endereço) e a quantidade total em m³. */
class NovoPedidoActivity : AppCompatActivity() {

    private val viewModel: NovoPedidoViewModel by viewModels()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    private lateinit var editDocumento: EditText
    private lateinit var editNome: EditText
    private lateinit var editCep: EditText
    private lateinit var editRua: EditText
    private lateinit var editNumero: EditText
    private lateinit var editBairro: EditText
    private lateinit var editCidade: EditText
    private lateinit var editUf: AutoCompleteTextView
    private lateinit var editQuantidadeM3: EditText
    private lateinit var botaoDataEntrega: Button
    private lateinit var botaoSalvar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    // Timestamp (meia-noite do dia escolhido) da data de entrega; null enquanto o gerente não escolher.
    private var dataEntregaSelecionada: Long? = null

    /** Liga as Views, o autocompletar de UF, a máscara de CPF/CNPJ e os botões de data/salvar. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novo_pedido)

        editDocumento = findViewById(R.id.editDocumento)
        editNome = findViewById(R.id.editNome)
        editCep = findViewById(R.id.editCep)
        editRua = findViewById(R.id.editRua)
        editNumero = findViewById(R.id.editNumero)
        editBairro = findViewById(R.id.editBairro)
        editCidade = findViewById(R.id.editCidade)
        editUf = findViewById(R.id.editUf)
        editQuantidadeM3 = findViewById(R.id.editQuantidadeM3)
        botaoDataEntrega = findViewById(R.id.botaoDataEntrega)
        botaoSalvar = findViewById(R.id.botaoSalvar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        editUf.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, Uf.siglas))
        editDocumento.addTextChangedListener(MascaraDocumento(editDocumento))

        botaoDataEntrega.setOnClickListener { abrirSeletorDeDataEntrega() }
        botaoSalvar.setOnClickListener {
            viewModel.salvar(
                documentoDigitado = editDocumento.text.toString(),
                nome = editNome.text.toString().trim(),
                cepDigitado = editCep.text.toString(),
                rua = editRua.text.toString().trim(),
                numero = editNumero.text.toString().trim(),
                bairro = editBairro.text.toString().trim(),
                cidade = editCidade.text.toString().trim(),
                ufDigitada = editUf.text.toString(),
                quantidadeDigitada = editQuantidadeM3.text.toString(),
                dataEntrega = dataEntregaSelecionada
            )
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Abre o calendário do Android; ao escolher, guarda o timestamp e atualiza o texto do botão. */
    private fun abrirSeletorDeDataEntrega() {
        val calendario = Calendar.getInstance()
        dataEntregaSelecionada?.let { calendario.timeInMillis = it }

        DatePickerDialog(
            this,
            { _, ano, mes, dia ->
                val escolhida = Calendar.getInstance().apply {
                    set(ano, mes, dia, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                dataEntregaSelecionada = escolhida.timeInMillis
                botaoDataEntrega.text = formatoData.format(escolhida.time)
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Reflete o [NovoPedidoUiState] atual: progresso do salvamento, erros e sucesso. */
    private fun exibirEstado(estado: NovoPedidoUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoSalvar.isEnabled = !estado.carregando

        editDocumento.error = if (estado.erroDocumento) getString(R.string.novo_pedido_erro_documento) else null
        editNome.error = if (estado.erroNome) getString(R.string.novo_pedido_erro_nome) else null
        editQuantidadeM3.error = if (estado.erroQuantidade) getString(R.string.novo_pedido_erro_m3) else null

        val mensagemBanner = when {
            estado.erroEndereco -> getString(R.string.novo_pedido_erro_endereco)
            estado.erroDataEntrega -> getString(R.string.novo_pedido_erro_data_entrega)
            estado.erroSalvar -> getString(R.string.novo_pedido_erro_salvar)
            else -> null
        }
        textErro.visibility = if (mensagemBanner != null) View.VISIBLE else View.GONE
        if (mensagemBanner != null) textErro.text = mensagemBanner

        estado.sucesso?.getConteudoSeNaoTratado()?.let { id ->
            Toast.makeText(this, getString(R.string.novo_pedido_sucesso, id), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /** Reformata como CPF ou CNPJ enquanto o gerente digita, conforme a quantidade de dígitos. */
    private class MascaraDocumento(private val campo: EditText) : TextWatcher {
        private var atualizandoTexto = false

        // Sem uso aqui: só precisamos reagir depois que o texto já mudou (afterTextChanged).
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        /** Reformata o texto digitado; usa [atualizandoTexto] pra não entrar em loop consigo mesmo. */
        override fun afterTextChanged(s: Editable?) {
            if (atualizandoTexto || s == null) return
            val formatado = DocumentoUtils.formatar(s.toString())
            if (formatado == s.toString()) return

            atualizandoTexto = true
            campo.setText(formatado)
            campo.setSelection(formatado.length)
            atualizandoTexto = false
        }
    }
}
