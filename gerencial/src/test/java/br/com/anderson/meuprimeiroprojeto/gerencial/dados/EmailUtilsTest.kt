package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da conferência do formato de email ([EmailUtils]).
 *
 * Os exemplos foram conferidos à mão contra a mesma regra que o Android usa
 * (`android.util.Patterns.EMAIL_ADDRESS`): algo antes do @, um domínio depois do @ e pelo menos um
 * ponto no domínio.
 */
class EmailUtilsTest {

    /** Emails bem formados são aceitos, inclusive com ponto e "+" antes do @. */
    @Test
    fun emailsBemFormadosSaoAceitos() {
        assertTrue(EmailUtils.valido("ana@usina.com.br"))
        assertTrue(EmailUtils.valido("joao.silva+obra@concreto.io"))
    }

    /** Sem @ não é email. */
    @Test
    fun textoSemArrobaEhRecusado() {
        assertFalse(EmailUtils.valido("ana"))
        assertFalse(EmailUtils.valido("carlos.usina.com.br"))
    }

    /** Falta o que vem depois do @, ou o que vem antes dele. */
    @Test
    fun emailSemUmaDasPartesEhRecusado() {
        assertFalse(EmailUtils.valido("ana@"))
        assertFalse(EmailUtils.valido("@usina.com"))
    }

    /** O domínio precisa ter pelo menos um ponto ("usina" sozinho não serve). */
    @Test
    fun dominioSemPontoEhRecusado() {
        assertFalse(EmailUtils.valido("ana@usina"))
    }

    /** Espaço no meio não é permitido. */
    @Test
    fun emailComEspacoEhRecusado() {
        assertFalse(EmailUtils.valido("ana silva@usina.com"))
    }
}
