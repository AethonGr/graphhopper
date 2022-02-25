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

    public DBConnection() throws ClassNotFoundException, SQLException {
        String host = null;
        String user = null;
        String password = null;
        String db = null;
        String port = null;
        if (System.getenv("DATABASE_HOST") != null || System.getenv("DATABASE_USER") != null ||
                System.getenv("DATABASE_PASSWORD") != null) {
            host = System.getenv("DATABASE_HOST");
            user = System.getenv("DATABASE_USER");
            password = System.getenv("DATABASE_PASSWORD");
            db = System.getenv("JP_DATABASE_NAME");
            port = System.getenv("DATABASE_PORT");
        } else {
            host = "localhost";
            user = "root";
            password = "1234";
            db = "gtfs";
            port = "3306";
        }

        String connection_url = "jdbc:mysql://" + host + ":3306/" + db;

        LOG.info(connection_url);

        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(connection_url, user, password);

        } catch (ClassNotFoundException | SQLException e){

            LOG.error("Exception while connecting to database! Exception : " + e);
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
