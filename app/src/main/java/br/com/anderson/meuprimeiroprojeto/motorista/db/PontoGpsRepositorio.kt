package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Cálculo do km percorrido a partir dos pontos de GPS gravados localmente. */
object PontoGpsRepositorio {

    private const val RAIO_TERRA_METROS = 6_371_000.0

    /**
     * Soma a distância (fórmula de Haversine) entre cada par de pontos consecutivos do roteiro,
     * usando os pontos já salvos localmente (Room) — não depende de terem sincronizado com o
     * Firestore. Recalculado sob demanda: com um ponto a cada 10s, mesmo um roteiro de horas tem
     * no máximo algumas centenas de pontos, então somar tudo de novo é instantâneo.
     */
    suspend fun calcularKmPercorridos(context: Context, roteiroId: String): Double {
        val pontos = AppDatabase.obter(context).pontoGpsDao().buscarPorRoteiro(roteiroId)
        if (pontos.size < 2) return 0.0

        var totalMetros = 0.0
        for (i in 1 until pontos.size) {
            totalMetros += distanciaEmMetros(
                pontos[i - 1].latitude, pontos[i - 1].longitude,
                pontos[i].latitude, pontos[i].longitude
            )
        }
        return totalMetros / 1000.0
    }

    /** Pontos de GPS do roteiro em ordem, pra desenhar o trajeto no mapa. */
    suspend fun buscarPontos(context: Context, roteiroId: String): List<PontoGps> {
        return AppDatabase.obter(context).pontoGpsDao().buscarPorRoteiro(roteiroId)
    }

    /** Distância em linha reta entre duas coordenadas na superfície da Terra (fórmula de Haversine). */
    private fun distanciaEmMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RAIO_TERRA_METROS * c
    }
}
