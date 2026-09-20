package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.formatarKm
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.DetalhesViagemViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela única de detalhe de uma viagem: mostra os dados do pedido e um mapa — em branco com a
 * posição atual se a viagem ainda não começou, ou com o trajeto todo se já está em rota — e um
 * botão que confirma "Iniciar" ou "Encerrar" conforme o status.
 */
class DetalhesViagemActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_VIAGEM_ID = "viagem_id"
        private val LOCAL_PADRAO = LatLng(-27.05083, -49.62278)
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: DetalhesViagemViewModel by viewModels()
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private val clienteLocalizacao: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private lateinit var viagemId: String
    private lateinit var textPeriodo: TextView
    private lateinit var textKm: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView
    private lateinit var botaoAcao: Button
    private lateinit var textErro: TextView
    private lateinit var textSemPontos: TextView
    private var mapa: GoogleMap? = null
    private var estadoAtual: DetalhesViagemUiState = DetalhesViagemUiState()

    private val launcherPermissoesLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultados ->
            val localizacaoConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (localizacaoConcedida) {
                mostrarPosicaoAtualSeNaoIniciada()
            } else {
                findViewById<TextView>(R.id.textAvisoPermissao).apply {
                    text = getString(R.string.andamento_permissao_negada)
                    visibility = View.VISIBLE
                }
            }
        }

    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            RastreamentoService.iniciar(this, viagemId)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_viagem)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        textPeriodo = findViewById(R.id.textPeriodo)
        textKm = findViewById(R.id.textKm)
        botaoAjustarBateria = findViewById(R.id.botaoAjustarBateria)
        textBateria = findViewById(R.id.textBateria)
        botaoAcao = findViewById(R.id.botaoAcao)
        textErro = findViewById(R.id.textErro)
        textSemPontos = findViewById(R.id.textSemPontos)

        botaoAcao.setOnClickListener { confirmarAcao() }

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        pedirPermissaoDeLocalizacao()

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(viagemId)
    }

    override fun onResume() {
        super.onResume()
        verificarOtimizacaoDeBateria()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        mapa?.moveCamera(CameraUpdateFactory.newLatLngZoom(LOCAL_PADRAO, 12f))
        desenharMapa(estadoAtual)
    }

    private fun exibirEstado(estado: DetalhesViagemUiState) {
        estadoAtual = estado
        if (estado.erroAoCarregar) {
            mostrarErro(getString(R.string.iniciar_transito_erro_carregar))
            return
        }
        if (!estado.carregado) return

        findViewById<TextView>(R.id.textDestinatario).text = estado.destinatarioNome
        findViewById<TextView>(R.id.textEndereco).text = estado.enderecoResumido
        findViewById<TextView>(R.id.textMetrosCubicos).text = getString(
            R.string.iniciar_transito_m3_formato, formatarM3(estado.metrosCubicos)
        )
        findViewById<TextView>(R.id.textCaminhao).text = getString(
            R.string.iniciar_transito_caminhao_formato, estado.caminhaoPlaca
        )

        val emRota = estado.status == StatusViagem.EM_ROTA.name
        if (emRota) {
            val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
            textPeriodo.text = getString(R.string.detalhes_viagem_inicio_formato, inicio)
            textPeriodo.visibility = View.VISIBLE
            textKm.text = getString(R.string.detalhes_viagem_km_formato, formatarKm(estado.kmPercorridos))
            textKm.visibility = View.VISIBLE
            botaoAcao.text = getString(R.string.detalhes_viagem_botao_encerrar)
            verificarOtimizacaoDeBateria()
            // Garante o rastreamento ativo mesmo se o serviço tiver sido morto pelo sistema
            // (bateria, memória) e o motorista reabrir a viagem em rota: iniciar() é idempotente
            // (RastreamentoService só reinicia o GPS se ainda não estava ligado para esta viagem).
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                RastreamentoService.iniciar(this, viagemId)
            }
        } else {
            textPeriodo.visibility = View.GONE
            textKm.visibility = View.GONE
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            botaoAcao.text = getString(R.string.iniciar_transito_botao)
        }

        desenharMapa(estado)

        estado.cancelamento?.getConteudoSeNaoTratado()?.let { motivo -> tratarCancelamentoRemoto(motivo) }
        estado.encerrada?.getConteudoSeNaoTratado()?.let { tratarEncerramentoConfirmado() }
    }

    /** Desenha o trajeto (viagem em rota) ou a posição atual (viagem ainda não iniciada). */
    private fun desenharMapa(estado: DetalhesViagemUiState) {
        val mapaAtual = mapa ?: return
        if (!estado.carregado) return

        if (estado.status == StatusViagem.EM_ROTA.name) {
            textSemPontos.visibility = if (estado.pontos.isEmpty()) View.VISIBLE else View.GONE
            if (estado.pontos.isEmpty()) return

            mapaAtual.clear()
            val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
            mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
            if (posicoes.size == 1) {
                mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
            } else {
                val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
                mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
            }
        } else {
            textSemPontos.visibility = View.GONE
            mostrarPosicaoAtualSeNaoIniciada()
        }
    }

    /** Marca a posição atual no mapa (leitura pontual, sem ligar o rastreamento em segundo plano). */
    private fun mostrarPosicaoAtualSeNaoIniciada() {
        val mapaAtual = mapa ?: return
        if (estadoAtual.status == StatusViagem.EM_ROTA.name) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        clienteLocalizacao.lastLocation.addOnSuccessListener { localizacao ->
            if (localizacao == null) return@addOnSuccessListener
            val posicao = LatLng(localizacao.latitude, localizacao.longitude)
            mapaAtual.clear()
            mapaAtual.addMarker(MarkerOptions().position(posicao).title(getString(R.string.detalhes_viagem_posicao_atual)))
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicao, 15f))
        }
    }

    private fun confirmarAcao() {
        val emRota = estadoAtual.status == StatusViagem.EM_ROTA.name
        val tituloDialogo = if (emRota) {
            R.string.detalhes_viagem_confirmar_encerrar
        } else {
            R.string.detalhes_viagem_confirmar_iniciar
        }
        AlertDialog.Builder(this)
            .setTitle(tituloDialogo)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ ->
                if (emRota) {
                    RastreamentoService.encerrar(this, viagemId)
                    viewModel.confirmarEncerramento(viagemId)
                } else {
                    val temLocalizacao = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!temLocalizacao) {
                        findViewById<TextView>(R.id.textAvisoPermissao).apply {
                            text = getString(R.string.andamento_permissao_negada)
                            visibility = View.VISIBLE
                        }
                    } else {
                        viewModel.confirmarInicio(viagemId)
                        pedirPermissaoDeSegundoPlanoEIniciarServico()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun tratarEncerramentoConfirmado() {
        Toast.makeText(this, R.string.andamento_viagem_finalizada, Toast.LENGTH_SHORT).show()
        val intent = Intent(this, ViagensAbertasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun tratarCancelamentoRemoto(motivo: String) {
        RastreamentoService.encerrar(this, viagemId)
        Toast.makeText(this, getString(R.string.andamento_viagem_cancelada, motivo), Toast.LENGTH_LONG).show()
        val intent = Intent(this, ViagensAbertasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun pedirPermissaoDeLocalizacao() {
        val jaTemLocalizacao = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (jaTemLocalizacao) return

        val permissoes = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissoes.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcherPermissoesLocalizacao.launch(permissoes.toTypedArray())
    }

    private fun pedirPermissaoDeSegundoPlanoEIniciarServico() {
        val jaTemSegundoPlano = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (jaTemSegundoPlano) {
            RastreamentoService.iniciar(this, viagemId)
        } else {
            launcherPermissaoSegundoPlano.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun verificarOtimizacaoDeBateria() {
        if (estadoAtual.status != StatusViagem.EM_ROTA.name) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) View.GONE else View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
        botaoAjustarBateria.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
