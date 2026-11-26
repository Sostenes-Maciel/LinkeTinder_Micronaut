package linketinder.emails

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class MsEmailsSpec extends Specification {

    @Inject
    EmbeddedApplication application

    void 'deve iniciar a aplicacao ms-emails com sucesso'() {
        expect:
        application.running
    }
}