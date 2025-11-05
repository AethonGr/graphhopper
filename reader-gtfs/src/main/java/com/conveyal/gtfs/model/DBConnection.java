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

        if (System.getenv("DATABASE_HOST") != null || System.getenv("DATABASE_USER") != null || System.getenv("DATABASE_PASSWORD") != null) {
            host = System.getenv("DATABASE_HOST");
            user = System.getenv("DATABASE_USER");
            password = System.getenv("DATABASE_PASSWORD");
        } else {
            host = "localhost";
            user = "root";
            password = "0112358";
        }

        String connection_url = "jdbc:mysql://" + host + ":3306/" + db;
        if (host != null && (host.contains("digitalocean.com") || host.contains("amazonaws.com") || host.contains("rds."))) {
            connection_url += "?sslMode=REQUIRED";
        }

        LOG.info(connection_url);

        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(connection_url, user, password);

        } catch (ClassNotFoundException | SQLException e){

            LOG.error("Exception while connecting to database! Exception : " + e);
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
