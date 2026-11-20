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

    @Post('/matches')
    HttpResponse registrarCurtidaMatch(@Body Map<String, Object> dados) {
        return processarCurtida(dados)
    }

    private HttpResponse processarCurtida(Map<String, Object> dados) {

        Map<String, Object> payload = dados ?: [:]

        String autor = payload.autor?.toString()?.trim()?.toUpperCase()

        switch (autor) {

            case 'CANDIDATO':
                // Curtida do candidato não recebe CNPJ.
                payload.cnpjEmpresa = null
                break

            case 'EMPRESA':
                if (!payload.cnpjEmpresa) {
                    throw new CurtidaMatchInvalidoException(
                            'CNPJ da empresa é obrigatório.'
                    )
                }
                break

            default:
                throw new CurtidaMatchInvalidoException(
                        'Autor inválido. Informe CANDIDATO ou EMPRESA.'
                )
        }

        Map<String, Object> registrada = servico.registrarCurtida(payload)

        boolean deuMatch = registrada.match == true

        Map<String, Object> resposta = [
                mensagem: deuMatch
                        ? 'MATCH ENCONTRADO!'
                        : 'Curtida registrada com sucesso!',
                match: deuMatch,
                dados: registrada
        ]

        return HttpResponse.status(HttpStatus.CREATED).body(resposta)
    }


    @Delete('/matches')
    HttpResponse removerCurtida(@Body Map<String, Object> dados) {

        Map<String, Object> payload = dados ?: [:]

        String autor = payload.autor?.toString()?.trim()?.toUpperCase()
        String cpfCandidato = payload.cpfCandidato?.toString()
        Integer idVaga = obterInteiro(payload.idVaga, 'idVaga')

        boolean removida

        switch (autor) {

            case 'CANDIDATO':
                removida = servico.removerCurtidaCandidato(
                        cpfCandidato,
                        idVaga
                )
                break

            case 'EMPRESA':
                String cnpjEmpresa = payload.cnpjEmpresa?.toString()

                removida = servico.removerCurtidaEmpresa(
                        cnpjEmpresa,
                        cpfCandidato,
                        idVaga
                )
                break

            default:
                throw new CurtidaMatchInvalidoException(
                        'Autor inválido. Informe CANDIDATO ou EMPRESA.'
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


    @Get('/matches')
    HttpResponse listarMatches(
            @QueryValue @Nullable String cpf,
            @QueryValue @Nullable String cnpj
    ) {

        if (cpf && cnpj) {
            throw new CurtidaMatchInvalidoException(
                    'Informe CPF ou CNPJ, não ambos.'
            )
        }

        if (cpf) {
            return HttpResponse.ok(
                    servico.listarStatusCurtidasCandidato(cpf)
            )
        }

        if (cnpj) {
            return HttpResponse.ok(
                    servico.listarStatusCurtidasEmpresa(cnpj)
            )
        }

        return HttpResponse.ok(servico.listarMatches())
    }


    private static Integer obterInteiro(Object valor, String campo) {

        try {
            Integer numero = valor instanceof Number
                    ? (valor as Number).intValue()
                    : Integer.parseInt(valor?.toString()?.trim())

            if (numero <= 0) {
                throw new NumberFormatException()
            }

            return numero

        } catch (Exception ignored) {
            throw new CurtidaMatchInvalidoException(
                    "${campo} deve ser um número inteiro maior que zero."
            )
        }
    }


    @Error(CurtidaMatchInvalidoException)
    static HttpResponse tratarInvalido(CurtidaMatchInvalidoException ex) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body([mensagem: ex.message])
    }

    @Error(SQLException)
    static HttpResponse tratarErroSql(SQLException ex) {

        if (ex.SQLState == '23505') {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body([mensagem: 'Registro duplicado em curtida ou match_vaga.'])
        }

        if (ex.SQLState == '23503') {
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body([mensagem: 'Chave estrangeira inválida. Verifique candidato, vaga e empresa.'])
        }

        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body([mensagem: 'Falha ao persistir curtida ou match no banco.'])
    }
}