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

        if (System.getenv("LC_JOURNEY_PLANNING_DB_HOST") != null || System.getenv("LC_JOURNEY_PLANNING_DB_USER") != null || System.getenv("LC_JOURNEY_PLANNING_DB_PASSWORD") != null) {
            host = System.getenv("LC_JOURNEY_PLANNING_DB_HOST");
            user = System.getenv("LC_JOURNEY_PLANNING_DB_USER");
            password = System.getenv("LC_JOURNEY_PLANNING_DB_PASSWORD");
        } else {
            host = "localhost";
            user = "root";
            password = "0112358";
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
