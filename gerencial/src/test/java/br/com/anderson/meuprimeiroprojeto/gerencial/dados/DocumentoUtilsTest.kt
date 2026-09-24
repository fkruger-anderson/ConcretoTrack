package br.com.anderson.meuprimeiroprojeto.gerencial.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da validação e da máscara de CPF/CNPJ ([DocumentoUtils]).
 *
 * Os documentos usados aqui foram conferidos à mão (fora deste código) com a conta do "módulo 11":
 * - 529.982.247-25 é um CPF válido;
 * - 11.222.333/0001-81 é um CNPJ válido.
 * Para testar a recusa, basta trocar um dos dois últimos dígitos (os verificadores).
 *
 * `assertTrue`/`assertFalse` conferem se o resultado é verdadeiro/falso; `assertNull` confere se
 * é vazio (`null`).
 */
class DocumentoUtilsTest {

    /** Um CPF com os dois dígitos verificadores certos é aceito, com ou sem máscara. */
    @Test
    fun cpfValidoEhAceito() {
        assertTrue(DocumentoUtils.documentoValido("529.982.247-25"))
        assertTrue(DocumentoUtils.documentoValido("52998224725"))
    }

    /** Errar o primeiro dígito verificador (o penúltimo número) faz o CPF ser recusado. */
    @Test
    fun cpfComPrimeiroDigitoVerificadorErradoEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("529.982.247-35"))
    }

    /** Errar o segundo dígito verificador (o último número) faz o CPF ser recusado. */
    @Test
    fun cpfComSegundoDigitoVerificadorErradoEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("529.982.247-24"))
    }

    /**
     * 111.111.111-11 passa na conta dos dígitos verificadores, mas não é um CPF de verdade. Só a
     * regra "todos os números iguais" o recusa.
     */
    @Test
    fun cpfComTodosOsNumerosIguaisEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("111.111.111-11"))
    }

    /** Um CNPJ com os dois dígitos verificadores certos é aceito. */
    @Test
    fun cnpjValidoEhAceito() {
        assertTrue(DocumentoUtils.documentoValido("11.222.333/0001-81"))
    }

    /** Errar o primeiro dígito verificador do CNPJ faz ele ser recusado. */
    @Test
    fun cnpjComPrimeiroDigitoVerificadorErradoEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("11.222.333/0001-91"))
    }

    /** Errar o segundo dígito verificador do CNPJ faz ele ser recusado. */
    @Test
    fun cnpjComSegundoDigitoVerificadorErradoEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("11.222.333/0001-80"))
    }

    /** 00.000.000/0000-00 passa na conta, mas é recusado pela regra "todos os números iguais". */
    @Test
    fun cnpjComTodosOsNumerosIguaisEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("00.000.000/0000-00"))
    }

    /** Uma quantidade de números que não é nem 11 nem 14 nunca é um documento válido. */
    @Test
    fun documentoComTamanhoErradoEhRecusado() {
        assertFalse(DocumentoUtils.documentoValido("5299822472"))
    }

    /** 11 números = pessoa física; 14 = jurídica; outra quantidade = nenhum dos dois. */
    @Test
    fun tipoDePessoaVemDaQuantidadeDeNumeros() {
        assertEquals(DocumentoUtils.FISICA, DocumentoUtils.tipoPessoa("529.982.247-25"))
        assertEquals(DocumentoUtils.JURIDICA, DocumentoUtils.tipoPessoa("11.222.333/0001-81"))
        assertNull(DocumentoUtils.tipoPessoa("529.982.247-2"))
    }

    /** A limpeza tira pontos, traço e barra, deixando só os números. */
    @Test
    fun apenasDigitosTiraAMascara() {
        assertEquals("52998224725", DocumentoUtils.apenasDigitos("529.982.247-25"))
    }

    /** Enquanto o gerente digita um CPF pela metade, a máscara vai aparecendo aos poucos. */
    @Test
    fun mascaraDeCpfIncompletoEhParcial() {
        assertEquals("123.4", DocumentoUtils.formatar("1234"))
    }

    /** Com os 11 números, a máscara de CPF fica completa. */
    @Test
    fun mascaraDeCpfCompleto() {
        assertEquals("529.982.247-25", DocumentoUtils.formatar("52998224725"))
    }

    /** Passando de 11 números, a máscara vira a de CNPJ. */
    @Test
    fun mascaraDeCnpjCompleto() {
        assertEquals("11.222.333/0001-81", DocumentoUtils.formatar("11222333000181"))
    }

    /** Números digitados além dos 14 do CNPJ são cortados. */
    @Test
    fun mascaraCortaNumerosAlemDoCnpj() {
        assertEquals("11.222.333/0001-81", DocumentoUtils.formatar("112223330001819"))
    }
}
