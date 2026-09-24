package br.com.anderson.meuprimeiroprojeto.motorista.db

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lê os pontos de GPS guardados no celular (Room) e calcula quantos km o caminhão rodou.
 *
 * Usa só os pontos locais, então funciona mesmo que eles ainda não tenham subido para o
 * Firestore. O banco local já foi preparado quando o app começou a rodar (ver [AppDatabase]),
 * então as funções não precisam receber um `Context`.
 */
object PontoGpsRepositorio {

    // Raio médio da Terra, em metros. `const val` é uma constante: um valor fixo que nunca muda.
    private const val RAIO_TERRA_METROS = 6_371_000.0

    /**
     * Soma a distância entre cada ponto e o seguinte, do começo ao fim do roteiro, e devolve o
     * total em km. É como ligar os pontos com uma régua e somar os pedacinhos.
     *
     * Com menos de 2 pontos não há trajeto, então devolve 0.
     *
     * A conta é refeita do zero toda vez que é pedida. Isso não pesa: com um ponto a cada 10
     * segundos, mesmo um roteiro de horas tem só algumas centenas de pontos.
     */
    suspend fun calcularKmPercorridos(roteiroId: String): Double {
        val pontos = AppDatabase.obter().pontoGpsDao().buscarPorRoteiro(roteiroId)
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

    /** Devolve os pontos de GPS do roteiro, em ordem de horário, para desenhar o trajeto no mapa. */
    suspend fun buscarPontos(roteiroId: String): List<PontoGps> {
        return AppDatabase.obter().pontoGpsDao().buscarPorRoteiro(roteiroId)
    }

    /**
     * Distância em metros entre duas coordenadas, pela "fórmula de Haversine".
     *
     * Como a Terra é redonda, não dá para usar a régua reta de um mapa plano. Esta fórmula calcula
     * a distância andando por cima da superfície de uma esfera. Os ângulos precisam estar em
     * radianos (por isso o `Math.toRadians`). O resultado é "em linha reta" entre os dois pontos,
     * e não pelas ruas; como os pontos são lidos a cada 10 segundos, a diferença é pequena.
     */
    private fun distanciaEmMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RAIO_TERRA_METROS * c
    }
}
