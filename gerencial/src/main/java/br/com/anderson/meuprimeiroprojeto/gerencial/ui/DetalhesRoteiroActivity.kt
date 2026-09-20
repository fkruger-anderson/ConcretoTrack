package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Detalhe de um roteiro: mapa com o trajeto inteiro e a tabela de rateio de km por pedido. */
class DetalhesRoteiroActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        /** Chave do Intent extra: id do roteiro a ser mostrado. */
        const val EXTRA_ROTEIRO_ID = "roteiro_id"
        /** Chave do Intent extra, opcional: viagem a destacar na tabela (quando veio de um clique específico). */
        const val EXTRA_VIAGEM_DESTACADA_ID = "viagem_destacada_id"
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: DetalhesRoteiroViewModel by viewModels()

    private var mapa: GoogleMap? = null
    private lateinit var roteiroId: String
    private lateinit var textSemPontos: TextView
    private lateinit var textMotorista: TextView
    private lateinit var textCaminhao: TextView
    private lateinit var textPeriodo: TextView
    private lateinit var textKmTotal: TextView
    private lateinit var recyclerRateio: RecyclerView
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_roteiro)
        title = getString(R.string.detalhes_roteiro_titulo)

        val idRecebido = intent.getStringExtra(EXTRA_ROTEIRO_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        roteiroId = idRecebido
        val viagemDestacadaId = intent.getStringExtra(EXTRA_VIAGEM_DESTACADA_ID)

        textSemPontos = findViewById(R.id.textSemPontos)
        textMotorista = findViewById(R.id.textMotorista)
        textCaminhao = findViewById(R.id.textCaminhao)
        textPeriodo = findViewById(R.id.textPeriodo)
        textKmTotal = findViewById(R.id.textKmTotal)
        recyclerRateio = findViewById(R.id.recyclerRateio)
        recyclerRateio.layoutManager = LinearLayoutManager(this)

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(roteiroId, viagemDestacadaId)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        viewModel.estado.value?.let { exibirEstado(it) }
    }

    private fun exibirEstado(estado: DetalhesRoteiroUiState) {
        if (!estado.carregado) return

        textMotorista.text = getString(R.string.detalhes_roteiro_motorista_formato, estado.motoristaNome)
        textCaminhao.text = getString(R.string.detalhes_roteiro_caminhao_formato, estado.caminhaoPlaca)

        val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
        val fim = estado.dataFim
        textPeriodo.text = if (fim != null) {
            getString(R.string.detalhes_roteiro_periodo_formato, inicio, formatoData.format(Date(fim)))
        } else {
            getString(R.string.detalhes_roteiro_inicio_formato, inicio)
        }

        if (estado.kmTotalRota != null) {
            textKmTotal.text = getString(R.string.detalhes_roteiro_km_total_formato, formatarKm(estado.kmTotalRota))
            textKmTotal.visibility = View.VISIBLE
        } else {
            textKmTotal.visibility = View.GONE
        }

        recyclerRateio.adapter = ItemRateioAdapter(estado.itens)

        val mapaAtual = mapa ?: return

        if (estado.pontos.isEmpty()) {
            textSemPontos.visibility = View.VISIBLE
            return
        }
        textSemPontos.visibility = View.GONE

        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        mapaAtual.addMarker(
            MarkerOptions().position(posicoes.first())
                .title(getString(R.string.rota_marcador_inicio))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        if (posicoes.size > 1) {
            mapaAtual.addMarker(
                MarkerOptions().position(posicoes.last())
                    .title(getString(R.string.rota_marcador_fim))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }
}
