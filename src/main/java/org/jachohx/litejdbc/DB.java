package org.jachohx.litejdbc;

import org.jachohx.litejdbc.exception.DBException;
import org.jachohx.litejdbc.exception.InitException;
import org.jachohx.litejdbc.util.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * This class provides a number of convenience methods for opening/closing database connections, running various 
 * types of queries, and executing SQL statements. This class differs from {@link Base} such that in this class you
 * can provide a logical name for a current connection. Use this class when you have more than one database in the system.
 *  
 *
 * @author Igor Polevoy
 */
public class DB {

    private String dbName;
    private final static Logger logger = LoggerFactory.getLogger(DB.class);

    /**
     * Creates a new DB object representing a connection to a DB.
     *
     * @param dbName logical name for a database.
     */
    public DB(String dbName){
        this.dbName = dbName;
    }


    /**
     * Attaches a database connection to current thread under a name provided to constructor.
     *
     * @param connection instance of connection to attach to current thread.
     */
    public void attach(Connection connection){
        ConnectionsAccess.attach(dbName, connection);
    }

    /**
     * Detaches a connection from current thread and returns an instance of it. This method does not close a connection.
     * Use it for cases of advanced connection management, such as integration with Spring Framework.
     *
     * @return instance of a connection detached from current thread by name passed to constructor.
     */
    public Connection detach() {

        Connection connection = ConnectionsAccess.getConnection(dbName);
        try {
            if(connection == null){
                throw new DBException("cannot detach connection '" + dbName + "' because it is not available");
            }
            ConnectionsAccess.detach(dbName);// lets free the thread from connection
            StatementCache.instance().cleanStatementCache(connection);
        } catch (Exception e) {
            logger.warn("Could not close connection! MUST INVESTIGATE POTENTIAL CONNECTION LEAK!", e);
        }
        return connection;
    }

    public void open(String driver, String url, String user, String password) {
    	checkExistingConnection(dbName);
    	try {
    		Class.forName(driver);
    		Connection connection = DriverManager.getConnection(url, user, password);
    		ConnectionsAccess.attach(dbName, connection);
    	} catch (Exception e) {
    		throw new InitException("Failed to connect to JDBC URL: " + url, e);
    	}
    }

    public void open(DataSource datasource){
        checkExistingConnection(dbName);
        try {
            Connection connection = datasource.getConnection();
            ConnectionsAccess.attach(dbName, connection);
        } catch (Exception e) {
            throw new InitException(e);
        }
    }

    private void checkExistingConnection(String dbName){
        if( null != ConnectionsAccess.getConnection(dbName)){
            throw new DBException("Cannot open a new connection because existing connection is still on current thread, dbName: " + dbName + ", connection instance: " + connection()
            + ". This might indicate a logical error in your application.");
        }
    }


    /**
     * Closes connection and detaches it from current thread.
     */
    public void close() {
        close(false);
    }

    /**
     * Closes connection and detaches it from current thread.
     * @param suppressWarning true to not display a warning in case of a problem (connection not there)
     */
    public void close(boolean suppressWarning) {
        try {
            Connection connection = ConnectionsAccess.getConnection(dbName);
            if(connection == null){
                throw new DBException("cannot close connection '" + dbName + "' because it is not available");
            }
            StatementCache.instance().cleanStatementCache(connection);
            connection.close();
            LogFilter.log(logger, "Closed connection: {}", connection);
        } catch (Exception e) {
            if (!suppressWarning) {
                logger.warn("Could not close connection! MUST INVESTIGATE POTENTIAL CONNECTION LEAK!", e);
            }
        }finally{
            ConnectionsAccess.detach(dbName);// lets free the thread from connection
        }
    }


    /**
     * Returns count of rows in table.
     *
     * @param table name of table.
     * @return count of rows in table.
     */
    public Long count(String table){
        String sql = "SELECT COUNT(*) FROM " + table ;
        return Convert.toLong(firstCell(sql));
    }

    /**
     * Runs a count query, returns a number of matching records.
     *
     * @param table table in which to count rows.
     * @param query this is a filtering query for the count. If '*' provided, all records will be counted. Example:
     * <code>"age > 65 AND department = 'accounting'"</code>
     * @param params parameters for placeholder substitution.
     * @return count number of records found in a table.
     */
    public Long count(String table, String query, Object... params) {

        if(query.trim().equals("*") && params.length == 0){
            return count(table);
        }
        if(query.trim().equals("*") && params.length != 0){
            throw new IllegalArgumentException("cannot use '*' and parameters");
        }

        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + query;
        return Convert.toLong(firstCell(sql, params));
    }


    @SuppressWarnings("rawtypes")
	public Object firstCell(String query, Object... params) {

        List<Map> list = findAll(query, params);
        if(list.size() == 0) return null;

        Map map = list.get(0);
        if(map.size() > 1)
            throw new IllegalArgumentException("query: " + query + " selects more than one column");

        return map.get(map.keySet().toArray()[0]);
    }


    /**
     * Alias to {@link #findAll(String, Object...)}
     */
    @SuppressWarnings("rawtypes")
	public List<Map> all(String query, Object ... params) {
        return findAll(query, params);
    }

    /**
     * This method returns entire resultset as one list. Do not use it for large result sets.
     * Example:
     * <pre>
     * <code>
     * List<Map<String, Object>> people = Base.findAll("select * from people where first_name = ?", "John");
     *  for(Map person: people)
     *      System.out.println(person.get("first_name"));
     * </code>
     * </pre>
     *
     * @param query raw SQL query. This query is parametrized.
     * @param params list of parameters for a parametrized query.
     * @return entire result set corresponding to the query.
     */
    @SuppressWarnings("rawtypes")
	public List<Map> findAll(String query, Object ... params) {

        long start = System.currentTimeMillis();
        final List<Map> results = new ArrayList<Map>();
        find(query, params).with(new RowListenerAdapter() {
            public void onNext(Map<String, Object> row) {
                results.add(row);
            }
        });
        LogFilter.logQuery(logger, query, params, start);
        return results;
    }


    /**
     * This method returns entire resultset as one list. Do not use it for large result sets.
     * Example:
     * <pre>
     *  List ssns = Base.firstColumn("select ssn from people where first_name = ?", "John");
     *  for(Object ssn: ssns)
     *      System.out.println(ssn);
     * </pre>
     *
     * This methods expects a query which selects only one column from a table/view. It will throw an exception if more than one
     * columns are fetched in a result set.
     *
     * @param query raw SQL query. This query is parametrized.
     * @param params list of parameters for a parametrized query.
     * @return entire result set corresponding to the query.
     */
    @SuppressWarnings("rawtypes")
	public List firstColumn(String query, Object ... params) {

        final List results = new ArrayList();
        long start = System.currentTimeMillis();
        find(query, params).with(new RowListenerAdapter() {
            @SuppressWarnings("unchecked")
			public void onNext(Map<String, Object> row) {
                if(row.size() > 1) throw new IllegalArgumentException("Query selects more than one column");

                results.add(row.get(row.keySet().toArray()[0]));
            }
        });

        LogFilter.logQuery(logger, query, params, start);
        return results;
    }

    /**
     * Alias to {@link #findAll(String)}
     */
    @SuppressWarnings("rawtypes")
	public List<Map> all(String query) {
        return findAll(query);
    }

    /**
     * This method returns entire resultset as one list. Do not use it for large result sets.
     *
     * @param query raw SQL query. This query is not parametrized.
     * @return entire result set corresponding to the query.
     */
    @SuppressWarnings("rawtypes")
	public List<Map> findAll(String query) {

        final ArrayList<Map> results = new ArrayList<Map>();
        long start = System.currentTimeMillis();
        find(query).with(new RowListenerAdapter() {
            public void onNext(Map<String, Object> row) {
                results.add(row);
            }
        });

        LogFilter.logQuery(logger, query, null, start);
        return results;
    }

    /**
     * Executes a raw query and returns an instance of {@link RowProcessor}. Use it in the following pattern:
     * <pre>
     * Base.find("select first_name, last_name from really_large_table").with(new RowListenerAdapter() {
            public void onNext(Map row) {
                ///write your code here
                Object o1 = row.get("first_name");
                Object o2 = row.get("last_name");
            }
        });
     </pre>
     *
     * @param query raw SQL.
     * @param params list of parameters if query is parametrized.
     * @return instance of <code>RowProcessor</code> which has with() method for convenience.
     */
    public RowProcessor find(String query, Object ... params) {

        //TODO: count ? signs and number of params, throw exception if do not match


        if(query.indexOf('?') == -1 && params.length != 0) throw new IllegalArgumentException("you passed arguments, but the query does not have placeholders: (?)");
        if(!query.toLowerCase().contains("select"))throw new IllegalArgumentException("query must be 'select' query");

        //TODO: cache prepared statements here too
        PreparedStatement ps;
        ResultSet rs;
        try {
            ps = createStreamingPreparedStatement(query);
            for (int index = 0; index < params.length; index++) {
                Object param = params[index];
                ps.setObject(index + 1, param);
            }

            rs = ps.executeQuery();
            return new RowProcessor(rs, ps);

        } catch (Exception e) { throw new DBException(query, params, e); }
    }

    private PreparedStatement createStreamingPreparedStatement(String query) throws SQLException {
        Connection conn = connection();
        PreparedStatement res;
        if ("mysql".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName())) {
            res = conn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            res.setFetchSize(Integer.MIN_VALUE);
        } else {
            res = conn.prepareStatement(query);
        }
        return res;
    }

    /**
     * Executes DML. Use it for inserts and updates.
     *
     * @param query raw DML.
     * @return number of rows afected by query.
     */
    public int exec(String query){
        long start = System.currentTimeMillis();
        Statement s = null;
        try {
            s = connection().createStatement();
            int count = s.executeUpdate(query);
            LogFilter.logQuery(logger, query, null, start);
            return count;
        } catch (SQLException e) {
            logException("Query failed: " + query, e);
            throw new DBException(query, null, e);
        } finally {
            try { if (s != null) s.close(); } catch (Exception e) {/*ignore*/}
        }
    }


    /**
     * Executes parametrized DML - will contain question marks as placeholders.
     *
     * @param query query to execute - will contain question marks as placeholders.
     * @param params  query parameters.
     * @return number of records affected.
     */
    public  int exec(String query, Object ... params){

        if(query.trim().toLowerCase().startsWith("select")) throw new IllegalArgumentException("expected DML, but got select...");

        if(query.indexOf('?') == -1) throw new IllegalArgumentException("query must be parametrized");

        long start = System.currentTimeMillis();
        PreparedStatement ps = null;
        try {
            ps = connection().prepareStatement(query);
            for (int index = 0; index < params.length; index++) {
                Object param = params[index];
                ps.setObject(index + 1, param);
            }
            int count =  ps.executeUpdate();
            LogFilter.logQuery(logger, query, params, start);
            return count;
        } catch (Exception e) {
            logException("Failed query: " + query, e);
            throw new DBException(query, params, e);
        } finally {
            try { if (ps != null) ps.close(); } catch (Exception e) {/*ignore*/}
        }

    }


    /**
     * This method is specific for inserts.
     *
     * @param query SQL for inserts.
     * @param autoIncrementColumnName name of a column that is auto-incremented.
     * @param params list of parameter values.
     * @return new value of auto-incremented column that is uniquely identifying a new record inserted. May return -1 if this
     * functionality is not supported by DB or driver.
     */
    Object execInsert(String query, String autoIncrementColumnName, Object... params) {

        if (!query.toLowerCase().contains("insert"))
            throw new IllegalArgumentException("this method is only for inserts");

        long start = System.currentTimeMillis();
        PreparedStatement ps;
        try {
            Connection connection = connection();
            ps = StatementCache.instance().getPreparedStatement(connection, query);
            if(ps == null){
                ps = connection.prepareStatement(query, new String[]{autoIncrementColumnName});
                StatementCache.instance().cache(connection, query, ps);
            }
            for (int index = 0; index < params.length; index++) {
                Object param = params[index];
                if (param instanceof byte[]) {
                    byte[] bytes = (byte[]) param;
                    try {
                        Blob b = connection.createBlob();
                        b.setBytes(1, bytes);
                        ps.setBlob(index + 1, b);
                    } catch (AbstractMethodError e) {// net.sourceforge.jtds.jdbc.ConnectionJDBC2.createBlob is abstract :)
                        ps.setObject(index + 1, param);
                    } catch (SQLFeatureNotSupportedException e) {
                        ps.setObject(index + 1, param);
                    } catch (SQLException e) {
                        ps.setObject(index + 1, param);
                    }
                }else{
                    ps.setObject(index + 1, param);
                }
            }
            ps.executeUpdate();

            ResultSet rs = null;
            try{
                rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    Object id = rs.getObject(1);
                    LogFilter.logQuery(logger, query, params, start);
                    return id;
                } else {
                    return -1;
                }
            } catch (Exception e) {
                logger.error("Failed to find out the auto-incremented value, returning -1, query: {}", query, e);
                return -1;
            } finally {
                try { if (rs != null) rs.close(); } catch (Exception e) {/*ignore*/}
            }
        } catch (Exception e) {
            throw new DBException(query, params, e);
        }
    }

    private void logException(String message, Exception e) {
        if (logger.isErrorEnabled() && Convert.toBoolean(System.getProperty("activejdbc.log_exception")))
            logger.error(message, e);
    }

    /**
     * Opens local transaction.
     */
    public  void openTransaction() {
        try {
            Connection c = ConnectionsAccess.getConnection(dbName);
            if(c == null){
                throw new DBException("Cannot open transaction, connection '" + dbName + "' not available");
            }
            c.setAutoCommit(false);
            LogFilter.log(logger, "Transaction opened");
        } catch (SQLException ex) {
            throw new DBException(ex.getMessage(), ex);
        }
    }


    /**
     * Commits local transaction.
     */
    public void commitTransaction() {
        try {
            Connection c= ConnectionsAccess.getConnection(dbName);
            if(c == null){
                throw new DBException("Cannot commit transaction, connection '" + dbName + "' not available");
            }
            c.commit();
            LogFilter.log(logger, "Transaction committed");
        } catch (SQLException ex) {
            throw new DBException(ex.getMessage(), ex);
        }
    }

    /**
     * Rolls back local transaction.
     */
    public void rollbackTransaction() {
        try {
            Connection c = ConnectionsAccess.getConnection(dbName);
            if (c == null) {
                throw new DBException("Cannot rollback transaction, connection '" + dbName + "' not available");
            }
            c.rollback();
            LogFilter.log(logger, "Transaction rolled back");
        } catch (SQLException ex) {
            throw new DBException(ex.getMessage(), ex);
        }
    }

    /**
     * Provides connection from current thread.
     *
     * @return connection from current thread. 
     */
    public Connection connection() {
        Connection connection = ConnectionsAccess.getConnection(dbName);
        if(connection  == null)
            throw new DBException("there is no connection '" + dbName + "' on this thread, are you sure you opened it?");

        return connection;
    }

    /**
     * Use to check if there is a connection present on current thread.
     *
     * @return true if finds connection on current thread, false if not.
     */
    public boolean hasConnection(){
        return null != ConnectionsAccess.getConnection(dbName);
    }

    /**
     * Synonym of {@link #connection()} for people who like getters.
     *
     * @return connection from current thread.
     */
    public Connection getConnection(){
        return connection();
    }


    /**
     * Provides connections available on current thread.
     *
     * @return  connections available on current thread.
     */
    public static Map<String, Connection> connections(){
        return ConnectionsAccess.getConnectionMap();
    }

    /**
     * Creates a <code>java.sql.PreparedStatement</code> to be used in batch executions later.
     *
     * @param parametrizedStatement Example of a statement: <code>INSERT INTO employees VALUES (?, ?)</code>.
     * @return instance of <code>java.sql.PreparedStatement</code> with compiled query.
     */
    public PreparedStatement startBatch(String parametrizedStatement){
        try {
            return connection().prepareStatement(parametrizedStatement);
        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    /**
     * Adds a batch statement using given <code>java.sql.PreparedStatement</code> and parameters.
     * @param ps <code>java.sql.PreparedStatement</code> to add batch to.
     * @param parameters parameters for the query in <code>java.sql.PreparedStatement</code>. Parameters will be
     * set on the statement in the same order as provided here.
     */
    public void addBatch(PreparedStatement ps, Object ... parameters){
        try {

            for (int i = 0; i < parameters.length; i++) {
                ps.setObject((i + 1), parameters[(i)]);
            }
            ps.addBatch();
        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    /**
     * Executes a batch on <code>java.sql.PreparedStatement</code>.
     *
     * @param ps <code>java.sql.PreparedStatement</code> to execute batch on.
     */
    public void executeBatch(PreparedStatement ps){
        try {
            ps.executeBatch();
            ps.clearParameters();
        } catch (SQLException e) {
            throw new DBException(e);
        }
    }
}
