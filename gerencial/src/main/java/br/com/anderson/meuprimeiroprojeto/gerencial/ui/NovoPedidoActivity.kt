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
import java.util.Date
import java.util.Locale

/**
 * A tela de cadastro de um pedido novo: dados do destinatário (CPF/CNPJ, nome, endereço), a
 * quantidade total em m³, a distância da obra e a data de entrega.
 *
 * A tela só junta o que foi digitado e entrega ao [NovoPedidoViewModel], que confere e salva.
 */
class NovoPedidoActivity : AppCompatActivity() {

    private val viewModel: NovoPedidoViewModel by viewModels()
    // Formato de data, por exemplo "21/09/2026".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    // Componentes da tela, preenchidos no onCreate com findViewById.
    private lateinit var editDocumento: EditText
    private lateinit var editNome: EditText
    private lateinit var editCep: EditText
    private lateinit var editRua: EditText
    private lateinit var editNumero: EditText
    private lateinit var editBairro: EditText
    private lateinit var editCidade: EditText
    private lateinit var editUf: AutoCompleteTextView
    private lateinit var editQuantidadeM3: EditText
    private lateinit var editDistanciaKm: EditText
    private lateinit var botaoDataEntrega: Button
    private lateinit var botaoSalvar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    // Timestamp (meia-noite do dia escolhido) da data de entrega; null enquanto o gerente não escolher.
    private var dataEntregaSelecionada: Long? = null

    companion object {
        // Nome usado para guardar a data escolhida no Bundle quando a tela é recriada.
        private const val CHAVE_DATA_ENTREGA = "data_entrega_selecionada"
    }

    /**
     * Chamado pelo Android quando a tela é criada. Liga os componentes e:
     * - recupera a data de entrega escolhida, se a tela está sendo recriada (ver [onSaveInstanceState]);
     * - liga as sugestões do campo UF (`AutoCompleteTextView` + a lista de [Uf]);
     * - liga a máscara de CPF/CNPJ no campo documento ([MascaraDocumento]);
     * - liga o botão da data (abre o calendário) e o botão Salvar (entrega tudo ao ViewModel);
     * - começa a observar o estado do ViewModel.
     *
     * Os textos digitados nos campos não precisam de cuidado ao girar o celular: o Android já guarda
     * e devolve sozinho o conteúdo de campos de texto que têm id.
     */
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
        editDistanciaKm = findViewById(R.id.editDistanciaKm)
        botaoDataEntrega = findViewById(R.id.botaoDataEntrega)
        botaoSalvar = findViewById(R.id.botaoSalvar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        // Restaura a data de entrega escolhida antes de girar a tela (senão ela se perde).
        dataEntregaSelecionada = savedInstanceState?.getLong(CHAVE_DATA_ENTREGA)?.takeIf { it != 0L }
        dataEntregaSelecionada?.let { botaoDataEntrega.text = formatoData.format(Date(it)) }

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
                distanciaDigitada = editDistanciaKm.text.toString(),
                dataEntrega = dataEntregaSelecionada
            )
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Chamado pelo Android antes de destruir a tela para recriá-la (por exemplo, ao girar o
     * celular). O `Bundle` é uma "mochila" onde se guardam valores simples para recuperar depois, no
     * [onCreate]. A data escolhida não está em nenhum campo de texto, então o Android não a guarda
     * sozinho — por isso ela é colocada na mochila aqui.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dataEntregaSelecionada?.let { outState.putLong(CHAVE_DATA_ENTREGA, it) }
    }

    /**
     * Abre o calendário do Android (`DatePickerDialog`), já na data escolhida antes, se houver.
     * Quando o gerente escolhe um dia, guarda a data (à meia-noite daquele dia) e mostra no botão.
     *
     * `Calendar` é a classe do Java para trabalhar com datas; atenção que nela os meses começam em
     * 0 (janeiro = 0).
     */
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

    /**
     * Desenha o estado atual: rodinha de carregando, marca em vermelho os campos com erro (com o
     * texto do `strings.xml`), mostra a mensagem geral de erro e, se deu certo, mostra o número do
     * pedido criado num Toast e fecha a tela.
     */
    private fun exibirEstado(estado: NovoPedidoUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoSalvar.isEnabled = !estado.carregando

        editDocumento.error = if (estado.erroDocumento) getString(R.string.novo_pedido_erro_documento) else null
        editNome.error = if (estado.erroNome) getString(R.string.novo_pedido_erro_nome) else null
        editQuantidadeM3.error = if (estado.erroQuantidade) getString(R.string.novo_pedido_erro_m3) else null
        editDistanciaKm.error = if (estado.erroDistanciaKm) getString(R.string.novo_pedido_erro_distancia_km) else null

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

    /**
     * Coloca a máscara de CPF ou CNPJ enquanto o gerente digita (conforme a quantidade de números).
     *
     * Um `TextWatcher` é um "vigia" do campo de texto: o Android chama as funções dele antes,
     * durante e depois de cada mudança no texto. `private class` dentro da Activity quer dizer que só
     * esta tela usa esta classe.
     */
    private class MascaraDocumento(private val campo: EditText) : TextWatcher {
        // Evita que a própria troca de texto feita aqui dispare o vigia de novo (um laço sem fim).
        private var atualizandoTexto = false

        // Sem uso aqui: só precisamos reagir depois que o texto já mudou (afterTextChanged).
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        /**
         * Chamado depois de cada mudança no texto. Refaz a máscara e, se o texto mudou, troca o
         * conteúdo do campo e coloca o cursor no fim.
         */
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
