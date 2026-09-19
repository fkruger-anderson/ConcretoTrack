package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PontoGpsDao {

    @Insert
    suspend fun inserir(ponto: PontoGps)

    /** Usado depois (mapa/histórico) para desenhar o trajeto da viagem. */
    @Query("SELECT * FROM pontos_gps WHERE viagemId = :viagemId ORDER BY horario")
    suspend fun buscarPorViagem(viagemId: String): List<PontoGps>

    @Query("SELECT COUNT(*) FROM pontos_gps WHERE viagemId = :viagemId")
    suspend fun contarPorViagem(viagemId: String): Int

    /** A fila offline: pontos gravados localmente que ainda não subiram para o Firestore. */
    @Query("SELECT * FROM pontos_gps WHERE sincronizado = 0")
    suspend fun buscarPendentes(): List<PontoGps>

    @Query("SELECT COUNT(*) FROM pontos_gps WHERE viagemId = :viagemId AND sincronizado = 0")
    suspend fun contarPendentesPorViagem(viagemId: String): Int

    @Query("UPDATE pontos_gps SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: Long)
}
