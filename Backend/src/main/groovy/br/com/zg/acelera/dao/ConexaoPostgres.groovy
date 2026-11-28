package br.com.zg.acelera.dao

import br.com.zg.acelera.interfaces.IConexao
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

import groovy.transform.CompileStatic

class ConexaoPostgres implements IConexao {

    private static final String URL = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/LinkeTinder_BD"
    private static final String USUARIO = System.getenv("DB_USER") ?: "postgres"
    private static final String SENHA = System.getenv("DB_PASSWORD") ?: "postgres"

    private static ConexaoPostgres instancia

    private ConexaoPostgres() {}

    static ConexaoPostgres getInstancia() {
        if (instancia == null) {
            instancia = new ConexaoPostgres()
        }
        return instancia
    }

    @CompileStatic
    Connection conectar() throws SQLException {
        try {

            Class.forName("org.postgresql.Driver")

            return DriverManager.getConnection(URL, USUARIO, SENHA)
        } catch (SQLException e) {
            throw new RuntimeException("Falha fatal ao conectar no banco de dados.", e)
        }
    }
}