package com.conveyal.gtfs.model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.*;

public class DBConnection {
    private Connection conn;

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
            port = "3303";
        }
        try{
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db, user, password);
        } catch (ClassNotFoundException | SQLException e){
            System.out.println("Exception when connection to database! Exception : " + e);
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
