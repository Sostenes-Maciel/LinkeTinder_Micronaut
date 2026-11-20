package br.com.zg.acelera.service

import br.com.zg.acelera.dao.CompetenciaDAO
import br.com.zg.acelera.dao.VagaDAO
import br.com.zg.acelera.model.Vaga
import br.com.zg.acelera.model.Recrutador
import br.com.zg.acelera.utils.ValidacaoUtils

class GerenciadorDeVagasService {

    private final VagaDAO vagaDAO
    private final CompetenciaDAO competenciaDAO

    GerenciadorDeVagasService(VagaDAO vagaDAO, CompetenciaDAO competenciaDAO) {
        this.vagaDAO = vagaDAO
        this.competenciaDAO = competenciaDAO
    }

    Integer cadastrarVaga(Vaga vaga) {

        ValidacaoUtils.validarCompetencias(vaga.competencia)
        vagaDAO.cadastrar(vaga)
        Integer idVaga = vaga.id

        vaga.competencia.each { competencia ->
            String nomePadronizado = normalizarCompetencia(competencia)
            validarCompetenciaNaoDuplicadaParaVaga(idVaga, nomePadronizado)
            competenciaDAO.garantirCompetencia(nomePadronizado)
            competenciaDAO.adicionarAVaga(idVaga, nomePadronizado)
        }

        return idVaga
    }

    List<Vaga> listarVagas() {
        List<Vaga> vagas = vagaDAO.listarTodos()

        vagas.each { vaga ->
            vaga.competencia = competenciaDAO.listarDaVaga(vaga.id)
        }

        return vagas
    }

    List<Vaga> listarVagasDaEmpresa(String cnpj) {
        List<Vaga> vagas = vagaDAO.listarPorEmpresa(cnpj)

        vagas.each { vaga ->
            vaga.competencia = competenciaDAO.listarDaVaga(vaga.id)
        }

        return vagas
    }

    Vaga buscarVagaPorId(Integer id) {
        return vagaDAO.buscarPorId(id)
    }

    Vaga buscarVaga(Integer id) {
        Vaga vaga = vagaDAO.buscarPorId(id)

        if (vaga != null) {
            vaga.competencia = competenciaDAO.listarDaVaga(vaga.id)
        }

        return vaga
    }

    void atualizarVaga(Vaga vaga) {
        ValidacaoUtils.validarCompetencias(vaga.competencia)
        vagaDAO.atualizar(vaga)

        List<String> competenciasAntigas = competenciaDAO.listarDaVaga(vaga.id)

        competenciasAntigas.each { competencia ->
            competenciaDAO.removerDaVaga(vaga.id, competencia)
        }

        vaga.competencia.each { competencia ->
            String nomePadronizado = normalizarCompetencia(competencia)
            validarCompetenciaNaoDuplicadaParaVaga(vaga.id, nomePadronizado)
            competenciaDAO.garantirCompetencia(nomePadronizado)
            competenciaDAO.adicionarAVaga(vaga.id, nomePadronizado)

        }
    }

    void excluirVaga(Integer id) {
        vagaDAO.excluir(id)
    }

    Integer cadastrarVaga(Vaga vaga, Recrutador recrutador) {
        validarGerenciamento(recrutador, null)
        if (vaga.empresa?.cnpj != recrutador.empresaId) {
            throw new SecurityException("Recrutador só pode gerenciar vagas da própria empresa.")
        }
        cadastrarVaga(vaga)
    }

    void atualizarVaga(Vaga vaga, Recrutador recrutador) {
        validarGerenciamento(recrutador, null)
        if (vaga.empresa?.cnpj != recrutador.empresaId) {
            throw new SecurityException("Recrutador só pode gerenciar vagas da própria empresa.")
        }
        atualizarVaga(vaga)
    }

    void excluirVaga(Integer id, Recrutador recrutador) {
        validarGerenciamento(recrutador, null)
        Vaga vaga = vagaDAO.buscarPorId(id)
        if (vaga == null || vaga.empresa?.cnpj != recrutador.empresaId) {
            throw new SecurityException("Recrutador só pode gerenciar vagas da própria empresa.")
        }
        excluirVaga(id)
    }

    List<Vaga> listarVagas(Recrutador recrutador) {
        if (!recrutador?.ativo) throw new SecurityException("Recrutador inativo.")
        listarVagasDaEmpresa(recrutador.empresaId).findAll { vaga ->
            !vaga.areaAtuacao || vaga.areaAtuacao == recrutador.areaAtuacao
        }
    }

    private static void validarGerenciamento(Recrutador recrutador, String area) {
        if (!recrutador?.ativo) throw new SecurityException("Recrutador inativo.")
        if (recrutador.nivelPermissao != 'Total') {
            throw new SecurityException("Recrutador com permissão Limitada não pode gerenciar vagas.")
        }
        if (area && recrutador.areaAtuacao != area) {
            throw new SecurityException("A vaga está fora da área de atuação do recrutador.")
        }
    }

    static List<String> extrairCompetencias(String entrada) {
        if (!entrada) return []
        return entrada.tokenize(',').collect { it.trim() }.findAll { it }
    }

    /**
     * Retorna o nome da competência já cadastrada no banco (case-insensitive)
     * ou o próprio nome informado (trimado) caso ainda não exista.
     */
    private String normalizarCompetencia(String competencia) {
        String nomeLimpo = competencia?.trim()
        if (!nomeLimpo) {
            return nomeLimpo
        }
        String existente = competenciaDAO.buscarNomePadronizado(nomeLimpo)
        return existente != null ? existente : nomeLimpo
    }

    private void validarCompetenciaNaoDuplicadaParaVaga(Integer idVaga, String nomeCompetencia) {
        List<String> atuais = competenciaDAO.listarDaVaga(idVaga)
        String chave = nomeCompetencia.toLowerCase(Locale.ROOT)
        boolean jaExiste = atuais.any { it?.toLowerCase(Locale.ROOT) == chave }
        if (jaExiste) {
            throw new IllegalArgumentException(
                    "A competência '${nomeCompetencia}' já está cadastrada para esta vaga."
            )
        }
    }
}