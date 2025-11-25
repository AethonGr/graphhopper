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

    public DBConnection(String db) throws  SQLException {
        String host = null;
        String user = null;
        String password = null;
        String port = "3306"; // Default MySQL port

        if (System.getenv("DATABASE_HOST") != null && System.getenv("DATABASE_USER") != null && System.getenv("DATABASE_PASSWORD") != null) {
            host = System.getenv("DATABASE_HOST");
            user = System.getenv("DATABASE_USER");
            password = System.getenv("DATABASE_PASSWORD");
            if (System.getenv("DATABASE_PORT") != null) {
                port = System.getenv("DATABASE_PORT");
            }
            LOG.info("Using database credentials from environment variables");
        } else {
            host = "localhost";
            user = "root";
            password = "0112358";
            LOG.warn("Database environment variables not set. Using default credentials (host: {}, user: {})", host, user);
        }

        // Determine if this is a managed database that requires SSL
        boolean isManagedDatabase = host != null && (
            host.contains("digitalocean.com") || 
            host.contains("amazonaws.com") || 
            host.contains("rds.")
        );

        // Build connection URL with appropriate SSL settings
        StringBuilder connection_url_builder = new StringBuilder("jdbc:mysql://");
        connection_url_builder.append(host).append(":").append(port).append("/").append(db);
        connection_url_builder.append("?connectTimeout=120000");        // 120 second connection timeout
        connection_url_builder.append("&socketTimeout=120000");         // 120 second socket timeout
        connection_url_builder.append("&autoReconnect=true");           // Auto reconnect on connection loss
        
        if (isManagedDatabase) {
            // For managed databases (DigitalOcean, AWS RDS, etc.), SSL is REQUIRED
            // but we don't verify certificates as the connection is already secured through infrastructure
            connection_url_builder.append("&useSSL=true");
            connection_url_builder.append("&requireSSL=true");
            connection_url_builder.append("&verifyServerCertificate=false");
            connection_url_builder.append("&allowPublicKeyRetrieval=true");
            LOG.info("Detected managed database. SSL enabled with certificate verification disabled.");
        } else {
            // For local/development databases, SSL may not be configured
            connection_url_builder.append("&useSSL=false");
            connection_url_builder.append("&allowPublicKeyRetrieval=true");
            LOG.info("Local database detected. SSL disabled.");
        }

        String connection_url = connection_url_builder.toString();

        LOG.info("Attempting database connection to: {}:{}/{}", host, port, db);
        LOG.info("Environment check - DATABASE_HOST: {}, DATABASE_USER: {}, DATABASE_PASSWORD: {}, DATABASE_PORT: {}",
                System.getenv("DATABASE_HOST") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_USER") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_PASSWORD") != null ? "SET" : "NOT SET",
                System.getenv("DATABASE_PORT") != null ? "SET ("+System.getenv("DATABASE_PORT")+")" : "NOT SET (using default 3306)");

        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(connection_url, user, password);
            LOG.info("Successfully connected to database!");

        } catch (ClassNotFoundException e){
            LOG.error("MySQL JDBC Driver not found! Exception: " + e);
            throw new SQLException("MySQL JDBC Driver not found", e);
        } catch (SQLException e){
            LOG.error("Failed to connect to database! Host: {}, Port: {}, Database: {}, User: {}", 
                     host, port, db, user);
            LOG.error("SQLException details - SQLState: {}, ErrorCode: {}, Message: {}", 
                     e.getSQLState(), e.getErrorCode(), e.getMessage());
            throw e;
        }

    }

    // Helper method to retrieve a single page of data
    public ResultSet getPaginatedData(int page, int pageSize, String query) throws SQLException {

        Connection connection = this.getConn();
        PreparedStatement preparedStatement = connection.prepareStatement(query);

        int offset = (page - 1) * pageSize;
        preparedStatement.setInt(1, pageSize);
        preparedStatement.setInt(2, offset);

        return preparedStatement.executeQuery();

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
