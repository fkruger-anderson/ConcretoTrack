package br.com.anderson.meuprimeiroprojeto.motorista.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * As operações na tabela de pontos de GPS.
 *
 * DAO ("Data Access Object") é uma `interface`: só dizemos o nome de cada função e, nas
 * anotações, o que ela faz no banco. O Room escreve o código de verdade durante o build. As
 * funções são `suspend` porque o Android não deixa usar o banco na thread da tela.
 */
@Dao
interface PontoGpsDao {

    /** Grava um ponto novo no celular (ainda não enviado ao Firestore). */
    @Insert
    suspend fun inserir(ponto: PontoGps)

    /**
     * Todos os pontos de um roteiro, do mais antigo para o mais novo (`ORDER BY horario`). É com
     * eles que o trajeto é desenhado no mapa e os km do roteiro são calculados.
     */
    @Query("SELECT * FROM pontos_gps WHERE roteiroId = :roteiroId ORDER BY horario")
    suspend fun buscarPorRoteiro(roteiroId: String): List<PontoGps>

    /**
     * A "fila offline": pontos que ainda não subiram para o Firestore. No SQLite, `false` é
     * guardado como 0 — por isso `sincronizado = 0`.
     */
    @Query("SELECT * FROM pontos_gps WHERE sincronizado = 0")
    suspend fun buscarPendentes(): List<PontoGps>

    /** Marca o ponto como enviado (`sincronizado = 1`), o que o tira da fila offline. */
    @Query("UPDATE pontos_gps SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: Long)
}
