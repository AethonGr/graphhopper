package com.conveyal.gtfs.model;

import com.graphhopper.gtfs.GTFSFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.*;

public class DBConnection {
    private Connection conn;
    private static final Logger LOG = LoggerFactory.getLogger(GTFSFeed.class);

    /**
     * Connects to the PostgreSQL database DATABASE_NAME (default "transitool"); {@code schema} is the schema that
     * unqualified table names resolve to (the old MySQL database: "operational_data", "user_data", ...).
     */
    public DBConnection(String schema) throws  SQLException {
        String host = null;
        String user = null;
        String password = null;
        String port = "5432"; // Default PostgreSQL port
        String database = "transitool";

        if (System.getenv("DATABASE_HOST") != null && System.getenv("DATABASE_USER") != null && System.getenv("DATABASE_PASSWORD") != null) {
            host = System.getenv("DATABASE_HOST");
            user = System.getenv("DATABASE_USER");
            password = System.getenv("DATABASE_PASSWORD");
            if (System.getenv("DATABASE_PORT") != null) {
                port = System.getenv("DATABASE_PORT");
            }
            if (System.getenv("DATABASE_NAME") != null) {
                database = System.getenv("DATABASE_NAME");
            }
            LOG.info("Using database credentials from environment variables");
        } else {
            host = "localhost";
            user = "root";
            password = "0112358";
            LOG.warn("Database environment variables not set. Using default credentials (host: {}, user: {})", host, user);
        }

        // Build connection URL: the database is always the local compose service, no SSL
        StringBuilder connection_url_builder = new StringBuilder("jdbc:postgresql://");
        connection_url_builder.append(host).append(":").append(port).append("/").append(database);
        connection_url_builder.append("?currentSchema=").append(schema);
        connection_url_builder.append("&connectTimeout=120");           // 120 second connection timeout
        connection_url_builder.append("&socketTimeout=120");            // 120 second socket timeout
        connection_url_builder.append("&ApplicationName=graphhopper");

        String connection_url = connection_url_builder.toString();

        LOG.info("Attempting database connection to: {}:{}/{} (schema {})", host, port, database, schema);
        LOG.info("Environment check - DATABASE_HOST: {}, DATABASE_USER: {}, DATABASE_PASSWORD: {}, DATABASE_PORT: {}, DATABASE_NAME: {}",
                System.getenv("DATABASE_HOST") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_USER") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_PASSWORD") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_PORT") != null ? "SET ("+System.getenv("DATABASE_PORT")+")" : "NOT SET (using default 5432)",
                System.getenv("DATABASE_NAME") != null ? "SET ("+System.getenv("DATABASE_NAME")+")" : "NOT SET (using default transitool)");

        try{
            Class.forName("org.postgresql.Driver");
            this.conn = DriverManager.getConnection(connection_url, user, password);
            LOG.info("Successfully connected to database!");

        } catch (ClassNotFoundException e){
            LOG.error("PostgreSQL JDBC Driver not found! Exception: " + e);
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        } catch (SQLException e){
            LOG.error("Failed to connect to database! Host: {}, Port: {}, Database: {}, Schema: {}, User: {}",
                     host, port, database, schema, user);
            LOG.error("SQLException details - SQLState: {}, ErrorCode: {}, Message: {}", 
                     e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw e;
        }

    }

    public Connection getConn() {
        return conn;
    }
    public ResultSet ExecuteQuery(String Query) throws SQLException {
        Statement st = this.conn.createStatement();
        // execute the query, and get a java resultset
        return st.executeQuery(Query);
    }

}
