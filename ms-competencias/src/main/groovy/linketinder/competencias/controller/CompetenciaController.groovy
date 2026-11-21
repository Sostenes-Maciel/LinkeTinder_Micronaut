package linketinder.competencias.controller

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Error
import linketinder.competencias.model.CompetenciaInvalidoException
import linketinder.competencias.model.ServicoCompetencia

import java.sql.SQLException

@Controller('/api/competencias')
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CompetenciaController {

    private final ServicoCompetencia servico

    CompetenciaController(ServicoCompetencia servico) {
        this.servico = servico
    }

    @Get
    HttpResponse listar() {
        return HttpResponse.ok(servico.listarCompetencias())
    }

    @Post
    HttpResponse cadastrar(@Body Map<String, Object> dados) {
        Map<String, Object> criada = servico.cadastrarCompetencia(dados)
        return HttpResponse.status(HttpStatus.CREATED).body(criada)
    }

    @Post('/candidatos/consulta-lote')
    HttpResponse listarPorCandidatos(@Body Map<String, Object> dados) {
        return HttpResponse.ok(
                servico.listarPorCandidatos(dados)
        )
    }

    @Get('/candidatos/{cpf}')
    HttpResponse listarCandidato(@PathVariable String cpf) {
        return HttpResponse.ok(servico.listarPorCandidato(cpf))
    }

    @Post('/candidatos')
    HttpResponse vincularCandidato(@Body Map<String, Object> dados) {
        return HttpResponse.status(HttpStatus.CREATED).body(servico.vincularCandidato(dados))
    }

    @Get('/empresas/{cnpj}')
    HttpResponse listarEmpresa(@PathVariable String cnpj) {
        return HttpResponse.ok(servico.listarPorEmpresa(cnpj))
    }

    @Post('/empresas')
    HttpResponse vincularEmpresa(@Body Map<String, Object> dados) {
        return HttpResponse.status(HttpStatus.CREATED).body(servico.vincularEmpresa(dados))
    }

    @Get('/vagas/{idVaga}')
    HttpResponse listarVaga(@PathVariable int idVaga) {
        return HttpResponse.ok(servico.listarPorVaga(idVaga))
    }

    @Post('/vagas')
    HttpResponse vincularVaga(@Body Map<String, Object> dados) {
        return HttpResponse.status(HttpStatus.CREATED).body(servico.vincularVaga(dados))
    }

    @Error(exception = CompetenciaInvalidoException)
    HttpResponse tratarCompetenciaInvalida(CompetenciaInvalidoException ex) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body([mensagem: ex.message])
    }

    @Error(exception = SQLException)
    HttpResponse tratarErroSql(SQLException ex) {
        if (ex.SQLState == '23505') {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body([mensagem: 'Registro duplicado de competencia ou vinculo.'])
        }

        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body([mensagem: 'Falha ao persistir competencia no banco.'])
    }
}