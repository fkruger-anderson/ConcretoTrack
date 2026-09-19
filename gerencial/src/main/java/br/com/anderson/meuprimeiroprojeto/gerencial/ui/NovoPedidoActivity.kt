package br.com.anderson.meuprimeiroprojeto.gerencial.ui

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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViaCepServico
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Uf
import kotlinx.coroutines.launch

class NovoPedidoActivity : AppCompatActivity() {

    private lateinit var editDocumento: EditText
    private lateinit var editNome: EditText
    private lateinit var editCep: EditText
    private lateinit var progressoCep: ProgressBar
    private lateinit var editRua: EditText
    private lateinit var editNumero: EditText
    private lateinit var editBairro: EditText
    private lateinit var editCidade: EditText
    private lateinit var editUf: AutoCompleteTextView
    private lateinit var editQuantidadeM3: EditText
    private lateinit var botaoSalvar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novo_pedido)

        editDocumento = findViewById(R.id.editDocumento)
        editNome = findViewById(R.id.editNome)
        editCep = findViewById(R.id.editCep)
        progressoCep = findViewById(R.id.progressoCep)
        editRua = findViewById(R.id.editRua)
        editNumero = findViewById(R.id.editNumero)
        editBairro = findViewById(R.id.editBairro)
        editCidade = findViewById(R.id.editCidade)
        editUf = findViewById(R.id.editUf)
        editQuantidadeM3 = findViewById(R.id.editQuantidadeM3)
        botaoSalvar = findViewById(R.id.botaoSalvar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        editUf.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, Uf.siglas))
        editDocumento.addTextChangedListener(MascaraDocumento(editDocumento))

        findViewById<Button>(R.id.botaoBuscarCep).setOnClickListener { buscarCep() }
        botaoSalvar.setOnClickListener { tentarSalvar() }
    }

    private fun buscarCep() {
        val cep = DocumentoUtils.apenasDigitos(editCep.text.toString())
        if (cep.length != 8) {
            editCep.error = getString(R.string.novo_pedido_erro_cep)
            return
        }

        progressoCep.visibility = View.VISIBLE
        lifecycleScope.launch {
            val endereco = try {
                ViaCepServico.buscar(cep)
            } catch (erro: Exception) {
                null
            }
            progressoCep.visibility = View.GONE

            if (endereco == null) {
                editCep.error = getString(R.string.novo_pedido_erro_cep)
                return@launch
            }
            editRua.setText(endereco.rua)
            editBairro.setText(endereco.bairro)
            editCidade.setText(endereco.cidade)
            editUf.setText(endereco.uf, false)
        }
    }

    private fun tentarSalvar() {
        val documento = DocumentoUtils.apenasDigitos(editDocumento.text.toString())
        val tipoPessoa = DocumentoUtils.tipoPessoa(documento)
        if (tipoPessoa == null || !DocumentoUtils.documentoValido(documento)) {
            editDocumento.error = getString(R.string.novo_pedido_erro_documento)
            return
        }

        val nome = editNome.text.toString().trim()
        if (nome.isEmpty()) {
            editNome.error = getString(R.string.novo_pedido_erro_nome)
            return
        }

        val cep = DocumentoUtils.apenasDigitos(editCep.text.toString())
        val rua = editRua.text.toString().trim()
        val numero = editNumero.text.toString().trim()
        val bairro = editBairro.text.toString().trim()
        val cidade = editCidade.text.toString().trim()
        val uf = editUf.text.toString().trim().uppercase()
        if (cep.length != 8 || rua.isEmpty() || numero.isEmpty() || bairro.isEmpty() ||
            cidade.isEmpty() || uf !in Uf.siglas
        ) {
            mostrarErro(getString(R.string.novo_pedido_erro_endereco))
            return
        }

        val quantidadeM3 = editQuantidadeM3.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (quantidadeM3 == null || quantidadeM3 <= 0.0) {
            editQuantidadeM3.error = getString(R.string.novo_pedido_erro_m3)
            return
        }

        mostrarCarregando(true)
        lifecycleScope.launch {
            try {
                val id = PedidoRepositorio.cadastrar(
                    destinatarioNome = nome,
                    documento = documento,
                    tipoPessoa = tipoPessoa,
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = quantidadeM3
                )
                Toast.makeText(
                    this@NovoPedidoActivity, getString(R.string.novo_pedido_sucesso, id), Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.novo_pedido_erro_salvar))
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        botaoSalvar.isEnabled = !carregando
        if (carregando) textErro.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }

    /** Reformata como CPF ou CNPJ enquanto o gerente digita, conforme a quantidade de dígitos. */
    private class MascaraDocumento(private val campo: EditText) : TextWatcher {
        private var atualizandoTexto = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

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
