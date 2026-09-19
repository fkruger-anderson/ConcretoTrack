package br.com.anderson.meuprimeiroprojeto.motorista.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A viagem em si agora mora no Firestore (para o saldo valer entre motoristas
 * diferentes); este banco local só guarda a fila offline de pontos de GPS.
 */
@Database(entities = [PontoGps::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pontoGpsDao(): PontoGpsDao

    companion object {
        @Volatile
        private var instancia: AppDatabase? = null

        fun obter(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "entregas.db"
                )
                    // Ainda não há usuários em produção; recriar o banco a cada mudança de
                    // schema é mais simples do que escrever migrations nesta fase do projeto.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instancia = it }
            }
    }
}
