package br.com.zg.acelera.cadastro.controller

import br.com.zg.acelera.cadastro.model.CadastroInvalidoException
import br.com.zg.acelera.cadastro.model.ServicoCadastro
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces

import java.sql.SQLException

@Controller('/api')
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CadastroController {

    private final ServicoCadastro servico

    CadastroController(ServicoCadastro servico) {
        this.servico = servico
    }

    @Post('/candidatos')
    HttpResponse cadastrarCandidato(@Body Map<String, Object> dados) {
        Map<String, String> resultado = servico.cadastrarCandidato(dados ?: [:])
        return HttpResponse.status(HttpStatus.CREATED).body(resultado)
    }

    @Post('/empresas')
    HttpResponse cadastrarEmpresa(@Body Map<String, Object> dados) {
        Map<String, String> resultado = servico.cadastrarEmpresa(dados ?: [:])
        return HttpResponse.status(HttpStatus.CREATED).body(resultado)
    }

    @Error(CadastroInvalidoException)
    static HttpResponse tratarCadastroInvalido(CadastroInvalidoException ex) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body([mensagem: ex.message])
    }

    @Error(SQLException)
    static HttpResponse tratarErroSql(SQLException ex) {
        if (ex.SQLState == '23505') {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body([mensagem: 'Ja existe um cadastro com esse identificador ou email.'])
        }
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body([mensagem: 'Falha ao persistir o cadastro.'])
    }
}