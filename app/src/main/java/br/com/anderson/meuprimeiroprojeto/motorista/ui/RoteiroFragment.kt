package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

/** Aba "Roteiro": roteiro atual do motorista — sem roteiro, planejando ou em rota. */
class RoteiroFragment : Fragment(R.layout.fragment_roteiro), OnMapReadyCallback {

    companion object {
        private const val PADDING_LIMITES_PX = 120

        // Mesmo intervalo do RastreamentoService — não faz sentido checar por pontos novos mais
        // rápido do que eles são gravados.
        private const val INTERVALO_ATUALIZACAO_MAPA_MS = 10_000L
    }

    private val viewModel: RoteiroViewModel by viewModels()

    private lateinit var textRoteiroVazio: TextView
    private lateinit var recyclerViagensRoteiro: RecyclerView
    private lateinit var botaoIniciarRoteiro: Button
    private lateinit var containerMapa: View
    private lateinit var botaoEncerrarRoteiro: Button
    private lateinit var textAvisoPermissao: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView
    private var mapa: GoogleMap? = null
    private var estadoAtual: RoteiroUiState = RoteiroUiState()

    /**
     * Enquanto a aba Roteiro fica aberta com o roteiro em rota, o `RastreamentoService` continua
     * gravando pontos novos no Room em segundo plano, mas nada mais avisa esta tela — sem isso, o
     * mapa fica congelado no trajeto que existia quando a aba foi aberta. Este `Handler` só
     * relê os pontos (local, via [RoteiroViewModel.atualizarPontos]) a cada
     * [INTERVALO_ATUALIZACAO_MAPA_MS], pra manter o desenho do trajeto acompanhando a viagem.
     */
    private val handlerAtualizacaoMapa = Handler(Looper.getMainLooper())
    private val atualizadorDeMapa = object : Runnable {
        override fun run() {
            viewModel.atualizarPontos()
            handlerAtualizacaoMapa.postDelayed(this, INTERVALO_ATUALIZACAO_MAPA_MS)
        }
    }

    /**
     * Pede localização fina/aproximada (e notificações, no Android 13+) pra o rastreamento poder
     * funcionar. Se concedida e o roteiro já estava EM_ROTA ao abrir a tela (ex.: o serviço foi
     * morto pelo sistema enquanto o app estava fechado), retoma o rastreamento na hora.
     */
    private val launcherPermissoesLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultados ->
            val localizacaoConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (localizacaoConcedida) {
                reasserirRastreamentoSeEmRota()
            } else {
                mostrarAvisoPermissaoNegada()
            }
        }

    /**
     * Pede localização em segundo plano ao iniciar o roteiro. Concedida ou não, prossegue: sem
     * ela o rastreamento ainda funciona com o app em primeiro plano, só perde precisão quando o
     * motorista troca de app.
     */
    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.iniciarRoteiro()
        }

    /**
     * Liga as views do layout inflado (o `Fragment(R.layout...)` do construtor já infla, aqui só
     * resta buscar os ids), prepara o mapa embutido (SupportMapFragment) de forma assíncrona e
     * começa a observar o [estado][RoteiroViewModel.estado] da ViewModel.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textRoteiroVazio = view.findViewById(R.id.textRoteiroVazio)
        recyclerViagensRoteiro = view.findViewById(R.id.recyclerViagensRoteiro)
        recyclerViagensRoteiro.layoutManager = LinearLayoutManager(requireContext())
        botaoIniciarRoteiro = view.findViewById(R.id.botaoIniciarRoteiro)
        containerMapa = view.findViewById(R.id.containerMapa)
        botaoEncerrarRoteiro = view.findViewById(R.id.botaoEncerrarRoteiro)
        textAvisoPermissao = view.findViewById(R.id.textAvisoPermissao)
        botaoAjustarBateria = view.findViewById(R.id.botaoAjustarBateria)
        textBateria = view.findViewById(R.id.textBateria)

        botaoIniciarRoteiro.setOnClickListener { confirmarIniciarRoteiro() }
        botaoEncerrarRoteiro.setOnClickListener { confirmarEncerrarRoteiro() }

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }

        pedirPermissaoDeLocalizacao()
        handlerAtualizacaoMapa.postDelayed(atualizadorDeMapa, INTERVALO_ATUALIZACAO_MAPA_MS)
    }

    /** Recarrega o roteiro e reavalia a isenção de bateria sempre que a aba volta a ficar visível. */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
        verificarOtimizacaoDeBateria()
    }

    /**
     * Callback do `SupportMapFragment` chamado quando o Google Maps termina de carregar de forma
     * assíncrona. Antes disso o mapa não existe, então [desenharTrajeto] não pode ser chamado —
     * por isso o trajeto só é desenhado aqui e nas atualizações de estado seguintes.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        desenharTrajeto(estadoAtual)
    }

    /** Solta a referência do mapa e o adapter da lista; a view do fragment está sendo destruída. */
    override fun onDestroyView() {
        super.onDestroyView()
        handlerAtualizacaoMapa.removeCallbacks(atualizadorDeMapa)
        mapa = null
        recyclerViagensRoteiro.adapter = null
    }

    /**
     * Redesenha a aba inteira a partir do estado da ViewModel: sem roteiro esconde tudo, com
     * roteiro mostra a lista de viagens e alterna entre os controles de "planejando" (botão
     * iniciar) e "em rota" (mapa, botão encerrar, aviso de bateria).
     */
    private fun exibirEstado(estado: RoteiroUiState) {
        estadoAtual = estado
        if (!estado.carregado) return

        if (!estado.temRoteiro) {
            textRoteiroVazio.visibility = View.VISIBLE
            recyclerViagensRoteiro.visibility = View.GONE
            botaoIniciarRoteiro.visibility = View.GONE
            containerMapa.visibility = View.GONE
            botaoEncerrarRoteiro.visibility = View.GONE
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            return
        }

        textRoteiroVazio.visibility = View.GONE
        recyclerViagensRoteiro.visibility = View.VISIBLE
        recyclerViagensRoteiro.adapter = ViagemDoRoteiroAdapter(estado.viagens) { viagemId ->
            viewModel.marcarEntregue(viagemId)
        }

        val emRota = estado.status == StatusRoteiro.EM_ROTA.name
        botaoIniciarRoteiro.visibility = if (!emRota) View.VISIBLE else View.GONE
        botaoIniciarRoteiro.isEnabled = estado.podeIniciar && !estado.carregando
        containerMapa.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.isEnabled = estado.podeEncerrar && !estado.carregando

        if (emRota) {
            // Garante o rastreamento ativo mesmo se o serviço tiver sido morto pelo sistema
            // (bateria, memória) e o motorista reabrir o roteiro em rota: iniciar() é idempotente
            // (RastreamentoService só reinicia o GPS se ainda não estava ligado para este roteiro).
            reasserirRastreamentoSeEmRota()
            verificarOtimizacaoDeBateria()
        } else {
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
        }

        desenharTrajeto(estado)

        estado.erro?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
        }
        estado.roteiroEncerrado?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(requireContext(), R.string.roteiro_encerrado_sucesso, Toast.LENGTH_SHORT).show()
        }
    }

    /** Desenha a Polyline do trajeto percorrido até agora (só faz sentido com o roteiro em rota). */
    private fun desenharTrajeto(estado: RoteiroUiState) {
        val mapaAtual = mapa ?: return
        mapaAtual.clear()
        if (estado.status != StatusRoteiro.EM_ROTA.name || estado.pontos.isEmpty()) return

        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }

    /** Confirma com o motorista antes de iniciar o roteiro (liga o GPS), usando AlertDialog. */
    private fun confirmarIniciarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_iniciar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> iniciarRoteiroComPermissoes() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    /**
     * Sem localização concedida não adianta nem tentar (o serviço se mataria sozinho ao checar a
     * permissão) — mostra o aviso e não inicia nada. Com localização mas sem segundo plano, pede
     * segundo plano antes de iniciar (o callback do launcher chama iniciarRoteiro independente da
     * resposta). Com tudo concedido, inicia direto.
     */
    private fun iniciarRoteiroComPermissoes() {
        val temLocalizacao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!temLocalizacao) {
            mostrarAvisoPermissaoNegada()
            return
        }

        val temSegundoPlano = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (temSegundoPlano) {
            viewModel.iniciarRoteiro()
        } else {
            launcherPermissaoSegundoPlano.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    /** Confirma com o motorista antes de encerrar o roteiro (desliga o GPS), usando AlertDialog. */
    private fun confirmarEncerrarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_encerrar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.encerrarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun mostrarAvisoPermissaoNegada() {
        textAvisoPermissao.apply {
            text = getString(R.string.andamento_permissao_negada)
            visibility = View.VISIBLE
        }
    }

    /**
     * Pede localização fina e aproximada assim que a aba abre (se ainda não concedidas), e no
     * Android 13+ pede também a permissão de notificações — exigida ali para mostrar a notificação
     * fixa do foreground service de rastreamento.
     */
    private fun pedirPermissaoDeLocalizacao() {
        val jaTemLocalizacao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
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

    /**
     * Chama [RastreamentoService.iniciar] de novo se o roteiro atual estiver EM_ROTA e a
     * localização estiver concedida — idempotente, seguro chamar sempre. É o que recupera o
     * rastreamento se o serviço tiver sido morto pelo sistema (bateria, memória) enquanto o app
     * estava fechado.
     */
    private fun reasserirRastreamentoSeEmRota() {
        val roteiroId = estadoAtual.roteiroId ?: return
        if (estadoAtual.status != StatusRoteiro.EM_ROTA.name) return
        val temPermissao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (temPermissao) {
            RastreamentoService.iniciar(requireContext(), roteiroId)
        }
    }

    /**
     * Mostra o botão de ajustar a otimização de bateria enquanto o roteiro está EM_ROTA e o app
     * ainda não está isento dela (Android pode matar o serviço de rastreamento pra economizar
     * bateria). Some fora do EM_ROTA ou quando não há roteiro algum.
     */
    private fun verificarOtimizacaoDeBateria() {
        if (estadoAtual.status != StatusRoteiro.EM_ROTA.name || !estadoAtual.temRoteiro) {
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            return
        }
        val powerManager = requireContext().getSystemService(PowerManager::class.java)
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) View.GONE else View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
        botaoAjustarBateria.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
    }
}
