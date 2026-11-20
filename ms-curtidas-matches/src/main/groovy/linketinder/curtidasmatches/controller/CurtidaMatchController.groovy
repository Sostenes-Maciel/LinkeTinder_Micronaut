package linketinder.curtidasmatches.controller

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue

import linketinder.curtidasmatches.model.CurtidaMatchInvalidoException
import linketinder.curtidasmatches.model.ServicoCurtidaMatch

import java.sql.SQLException

@Controller('/api')
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CurtidaMatchController {

    private final ServicoCurtidaMatch servico

    CurtidaMatchController(ServicoCurtidaMatch servico) {
        this.servico = servico
    }

    @Get('/curtidas')
    HttpResponse listarCurtidas() {
        return HttpResponse.ok(servico.listarCurtidas())
    }

    @Post('/curtidas')
    HttpResponse registrarCurtida(@Body Map<String, Object> dados) {
        return processarCurtida(dados)
    }

    @Get('/matches')
    HttpResponse listarMatches(
            @QueryValue('cpf') @Nullable String cpf,
            @QueryValue('cnpj') @Nullable String cnpj,
            @QueryValue('completo') @Nullable Boolean completo
    ) {

        if (cpf != null && !cpf.trim().isEmpty()) {

            if (Boolean.TRUE == completo) {
                return HttpResponse.ok(
                        servico.listarMatchesCompletosCandidato(cpf)
                )
            }

            return HttpResponse.ok(
                    servico.listarStatusCurtidasCandidato(cpf)
            )
        }

        if (cnpj != null && !cnpj.trim().isEmpty()) {

            if (Boolean.TRUE == completo) {
                return HttpResponse.ok(
                        servico.listarMatchesCompletosEmpresa(cnpj)
                )
            }

            return HttpResponse.ok(
                    servico.listarStatusCurtidasEmpresa(cnpj)
            )
        }

        return HttpResponse.ok(servico.listarMatches(null, null))
    }

    @Post('/matches')
    HttpResponse registrarCurtidaViaMatches(
            @Body Map<String, Object> dados
    ) {
        return processarCurtida(dados)
    }

    @Delete('/matches')
    HttpResponse removerCurtida(@Body Map<String, Object> dados) {

        String autor = dados.autor?.toString()?.trim()?.toUpperCase()
        String cpf = dados.cpfCandidato?.toString()?.replaceAll(/\D/, '')
        String cnpj = dados.cnpjEmpresa?.toString()?.replaceAll(/\D/, '')
        Integer idVaga = dados.idVaga != null
                ? dados.idVaga.toString().toInteger()
                : null

        if (!cpf || idVaga == null) {
            throw new CurtidaMatchInvalidoException(
                    'CPF e ID da vaga são obrigatórios.'
            )
        }

        boolean removida

        if (autor == 'CANDIDATO') {
            removida = servico.removerCurtidaCandidato(cpf, idVaga)

        } else if (autor == 'EMPRESA') {

            if (!cnpj) {
                throw new CurtidaMatchInvalidoException(
                        'CNPJ da empresa é obrigatório.'
                )
            }

            removida = servico.removerCurtidaEmpresa(
                    cnpj,
                    cpf,
                    idVaga
            )

        } else {
            throw new CurtidaMatchInvalidoException(
                    'Informe um autor válido: CANDIDATO ou EMPRESA.'
            )
        }

        if (!removida) {
            return HttpResponse.badRequest([
                    mensagem: 'Não foi possível remover a curtida. Verifique se existe um match ou se a curtida está registrada.'
            ])
        }

        return HttpResponse.ok([
                mensagem: 'Curtida removida com sucesso.'
        ])
    }

    private HttpResponse processarCurtida(Map<String, Object> dados) {

        String autor = dados.autor?.toString()?.trim()?.toUpperCase()

        if (!(autor in ['CANDIDATO', 'EMPRESA'])) {
            throw new CurtidaMatchInvalidoException(
                    'Informe um autor válido: CANDIDATO ou EMPRESA.'
            )
        }

        Map<String, Object> payload = new LinkedHashMap<>(dados)

        // Recrutador não participa desta versão.
        payload.remove('recrutadorId')

        if (autor == 'CANDIDATO') {
            payload.cnpjEmpresa = null
        }

        Map<String, Object> registrada =
                servico.registrarCurtida(payload)

        boolean temMatch = registrada.match == true

        return HttpResponse.status(HttpStatus.CREATED).body([
                mensagem: temMatch
                        ? 'MATCH ENCONTRADO!'
                        : 'Curtida registrada com sucesso!',
                match: temMatch,
                dados: registrada
        ])
    }

    @Error(CurtidaMatchInvalidoException)
    static HttpResponse tratarInvalido(
            CurtidaMatchInvalidoException ex
    ) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body([
                mensagem: ex.message
        ])
    }

    @Error(SQLException)
    static HttpResponse tratarErroSql(SQLException ex) {

        if (ex.SQLState == '23505') {
            return HttpResponse.status(HttpStatus.CONFLICT).body([
                    mensagem: 'Registro duplicado em curtida ou match_vaga.'
            ])
        }

        if (ex.SQLState == '23503') {
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body([
                    mensagem: 'Chave estrangeira inválida (CPF, ID da vaga ou CNPJ).'
            ])
        }

        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body([
                mensagem: 'Falha ao persistir curtida ou match no banco.'
        ])
    }
}