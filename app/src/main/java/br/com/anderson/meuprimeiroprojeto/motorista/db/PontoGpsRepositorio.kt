package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context

/** Contagem de pontos de GPS gravados localmente (fila offline) — usado pela tela "viagem em andamento". */
object PontoGpsRepositorio {

    /** Retorna (total de pontos, pontos ainda não sincronizados) da viagem. */
    suspend fun contarTotalEPendentes(context: Context, viagemId: String): Pair<Int, Int> {
        val dao = AppDatabase.obter(context).pontoGpsDao()
        val total = dao.contarPorViagem(viagemId)
        val pendentes = dao.contarPendentesPorViagem(viagemId)
        return total to pendentes
    }
}
