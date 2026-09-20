package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Acesso ao Room para a tabela de pontos de GPS (fila offline). */
@Dao
interface PontoGpsDao {

    /** Grava um novo ponto de GPS localmente (ainda não sincronizado). */
    @Insert
    suspend fun inserir(ponto: PontoGps)

    /** Usado depois (mapa/histórico) para desenhar o trajeto da viagem. */
    @Query("SELECT * FROM pontos_gps WHERE viagemId = :viagemId ORDER BY horario")
    suspend fun buscarPorViagem(viagemId: String): List<PontoGps>

    /** A fila offline: pontos gravados localmente que ainda não subiram para o Firestore. */
    @Query("SELECT * FROM pontos_gps WHERE sincronizado = 0")
    suspend fun buscarPendentes(): List<PontoGps>

    /** Marca o ponto como já sincronizado, tirando-o da fila offline. */
    @Query("UPDATE pontos_gps SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: Long)
}
