package linketinder.vagas.controller

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import linketinder.vagas.model.ServicoVaga
import linketinder.vagas.model.VagaInvalidoException

import java.sql.SQLException

@Controller('/api/vagas')
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class VagaController {

    private final ServicoVaga servico

    VagaController(ServicoVaga servico) {
        this.servico = servico
    }

    @Get
    HttpResponse listar() {
        return HttpResponse.ok(servico.listarVagas())
    }

    @Get('/{idVaga}')
    HttpResponse buscarPorId(@PathVariable int idVaga) {
        return HttpResponse.ok(servico.buscarPorId(idVaga))
    }

    @Get('/empresas/{cnpj}')
    HttpResponse listarPorEmpresa(@PathVariable String cnpj) {
        return HttpResponse.ok(servico.listarPorEmpresa(cnpj))
    }

    @Post
    HttpResponse cadastrar(@Body Map<String, Object> dados) {
        Map<String, Object> criada = servico.cadastrarVaga(dados)
        return HttpResponse.status(HttpStatus.CREATED).body(criada)
    }

    @Put('/{idVaga}')
    HttpResponse atualizar(@PathVariable int idVaga, @Body Map<String, Object> dados) {
        return HttpResponse.ok(servico.atualizarVaga(idVaga, dados))
    }

    @Delete('/{idVaga}')
    HttpResponse excluir(@PathVariable int idVaga) {
        servico.excluirVaga(idVaga)
        return HttpResponse.noContent()
    }

    @Error(exception = Throwable)
    HttpResponse tratarErro(Throwable ex) {

        Throwable causa = ex

        while (causa != null) {

            if (causa instanceof VagaInvalidoException) {
                return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body([mensagem: causa.message])
            }

            if (causa instanceof SQLException) {
                SQLException erroSql = (SQLException) causa
                String estadoSql = erroSql.getSQLState()

                if (estadoSql == '23505') {
                    return HttpResponse.status(HttpStatus.CONFLICT)
                            .body([mensagem: 'Ja existe uma vaga com esses dados.'])
                }

                if (estadoSql == '23503') {
                    return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body([mensagem: 'Empresa informada (cnpjEmpresa) nao encontrada no banco.'])
                }

                return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body([mensagem: 'Falha ao persistir vaga no banco.'])
            }

            causa = causa.getCause()
        }

        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body([mensagem: 'Falha ao processar a requisicao.'])
    }
}