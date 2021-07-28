package com.ysq.persistencebtree.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * This exception wraps a checked exception.
 * It is used in methods where checked exceptions are not supported,
 * for example in a Comparator.
 */
public class DbException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * If the SQL statement contains this text, then it is never added to the
     * SQL exception. Hiding the SQL statement may be important if it contains a
     * passwords, such as a CREATE LINKED TABLE statement.
     */
    public static final String HIDE_SQL = "--hide--";

    private static final Properties MESSAGES = new Properties();

    /**
     * Thrown when OOME exception happens on handle error
     * inside {@link #convert(Throwable)}.
     */
    public static final SQLException SQL_OOME =
            new SQLException("OutOfMemoryError", "HY000", ErrorCode.OUT_OF_MEMORY, new OutOfMemoryError());
    private static final DbException OOME = new DbException(SQL_OOME);

    private Object source;

    private DbException(SQLException e) {
        super(e.getMessage(), e);
    }

    private static String translate(String key, String... params) {
        String message = MESSAGES.getProperty(key);
        if (message == null) {
            message = "(Message " + key + " not found)";
        }
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                String s = params[i];
                if (s != null && s.length() > 0) {
                    params[i] = quote(s);
                }
            }
            message = MessageFormat.format(message, (Object[]) params);
        }
        return message;
    }

    private static String quote(String s) {
        int l = s.length();
        StringBuilder builder = new StringBuilder(l + 2).append('"');
        for (int i = 0; i < l;) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int t = Character.getType(cp);
            if (t == 0 || t >= Character.SPACE_SEPARATOR && t <= Character.SURROGATE && cp != ' ') {
                if (cp <= 0xffff) {
                    StringUtils.appendHex(builder.append('\\'), cp, 2);
                } else {
                    StringUtils.appendHex(builder.append("\\+"), cp, 3);
                }
            } else {
                if (cp == '"' || cp == '\\') {
                    builder.append((char) cp);
                }
                builder.appendCodePoint(cp);
            }
        }
        return builder.append('"').toString();
    }

    /**
     * Get the SQLException object.
     *
     * @return the exception
     */
    public SQLException getSQLException() {
        return (SQLException) getCause();
    }

    /**
     * Get the error code.
     *
     * @return the error code
     */
    public int getErrorCode() {
        return getSQLException().getErrorCode();
    }


    /**
     * Create a database exception for a specific error code.
     *
     * @param errorCode the error code
     * @return the exception
     */
    public static DbException get(int errorCode) {
        return get(errorCode, (String) null);
    }

    /**
     * Create a database exception for a specific error code.
     *
     * @param errorCode the error code
     * @param p1 the first parameter of the message
     * @return the exception
     */
    public static DbException get(int errorCode, String p1) {
        return get(errorCode, new String[] { p1 });
    }

    /**
     * Create a database exception for a specific error code.
     *
     * @param errorCode the error code
     * @param cause the cause of the exception
     * @param params the list of parameters of the message
     * @return the exception
     */
    public static DbException get(int errorCode, Throwable cause,
                                  String... params) {
        return new DbException(getJdbcSQLException(errorCode, cause, params));
    }

    /**
     * Create a database exception for a specific error code.
     *
     * @param errorCode the error code
     * @param params the list of parameters of the message
     * @return the exception
     */
    public static DbException get(int errorCode, String... params) {
        return new DbException(getJdbcSQLException(errorCode, null, params));
    }

    /**
     * Create a database exception for an arbitrary SQLState.
     *
     * @param sqlstate the state to use
     * @param message the message to use
     * @return the exception
     */
    public static DbException fromUser(String sqlstate, String message) {
        // do not translate as sqlstate is arbitrary : avoid "message not found"
        return new DbException(getJdbcSQLException(message, null, sqlstate, 0, null, null));
    }



    /**
     * Gets an internal error.
     *
     * @param s the message
     * @return the RuntimeException object
     */
    public static RuntimeException getInternalError(String s) {
        RuntimeException e = new RuntimeException(s);
        DbException.traceThrowable(e);
        return e;
    }

    /**
     * Gets an internal error.
     *
     * @return the RuntimeException object
     */
    public static RuntimeException getInternalError() {
        return getInternalError("Unexpected code path");
    }

    /**
     * Convert an exception to a SQL exception using the default mapping.
     *
     * @param e the root cause
     * @return the SQL exception object
     */
    public static SQLException toSQLException(Throwable e) {
        if (e instanceof SQLException) {
            return (SQLException) e;
        }
        return convert(e).getSQLException();
    }

    /**
     * Convert a throwable to an SQL exception using the default mapping. All
     * errors except the following are re-thrown: StackOverflowError,
     * LinkageError.
     *
     * @param e the root cause
     * @return the exception object
     */
    public static DbException convert(Throwable e) {
        try {
            if (e instanceof DbException) {
                return (DbException) e;
            } else if (e instanceof SQLException) {
                return new DbException((SQLException) e);
            } else if (e instanceof InvocationTargetException) {
                return convertInvocation((InvocationTargetException) e, null);
            } else if (e instanceof IOException) {
                return get(ErrorCode.IO_EXCEPTION_1, e, e.toString());
            } else if (e instanceof OutOfMemoryError) {
                return get(ErrorCode.OUT_OF_MEMORY, e);
            } else if (e instanceof StackOverflowError || e instanceof LinkageError) {
                return get(ErrorCode.GENERAL_ERROR_1, e, e.toString());
            } else if (e instanceof Error) {
                throw (Error) e;
            }
            return get(ErrorCode.GENERAL_ERROR_1, e, e.toString());
        } catch (OutOfMemoryError ignore) {
            return OOME;
        } catch (Throwable ex) {
            try {
                DbException dbException = new DbException(
                        new SQLException("GeneralError", "HY000", ErrorCode.GENERAL_ERROR_1, e));
                dbException.addSuppressed(ex);
                return dbException;
            } catch (OutOfMemoryError ignore) {
                return OOME;
            }
        }
    }

    /**
     * Convert an InvocationTarget exception to a database exception.
     *
     * @param te the root cause
     * @param message the added message or null
     * @return the database exception object
     */
    public static DbException convertInvocation(InvocationTargetException te,
                                                String message) {
        Throwable t = te.getTargetException();
        if (t instanceof SQLException || t instanceof DbException) {
            return convert(t);
        }
        message = message == null ? t.getMessage() : message + ": " + t.getMessage();
        return get(ErrorCode.EXCEPTION_IN_FUNCTION_1, t, message);
    }

    /**
     * Convert an IO exception to a database exception.
     *
     * @param e the root cause
     * @param message the message or null
     * @return the database exception object
     */
    public static DbException convertIOException(IOException e, String message) {
        if (message == null) {
            Throwable t = e.getCause();
            if (t instanceof DbException) {
                return (DbException) t;
            }
            return get(ErrorCode.IO_EXCEPTION_1, e, e.toString());
        }
        return get(ErrorCode.IO_EXCEPTION_2, e, e.toString(), message);
    }

    /**
     * Gets the SQL exception object for a specific error code.
     *
     * @param errorCode the error code
     * @return the SQLException object
     */
    public static SQLException getJdbcSQLException(int errorCode) {
        return getJdbcSQLException(errorCode, (Throwable)null);
    }

    /**
     * Gets the SQL exception object for a specific error code.
     *
     * @param errorCode the error code
     * @param p1 the first parameter of the message
     * @return the SQLException object
     */
    public static SQLException getJdbcSQLException(int errorCode, String p1) {
        return getJdbcSQLException(errorCode, null, p1);
    }

    /**
     * Gets the SQL exception object for a specific error code.
     *
     * @param errorCode the error code
     * @param cause the cause of the exception
     * @param params the list of parameters of the message
     * @return the SQLException object
     */
    public static SQLException getJdbcSQLException(int errorCode, Throwable cause, String... params) {
        String sqlstate = "foo";
        String message = translate(sqlstate, params);
        return getJdbcSQLException(message, null, sqlstate, errorCode, cause, null);
    }

    /**
     * Creates a SQLException.
     *
     * @param message the reason
     * @param sql the SQL statement
     * @param state the SQL state
     * @param errorCode the error code
     * @param cause the exception that was the reason for this exception
     * @param stackTrace the stack trace
     * @return the SQLException object
     */
    public static SQLException getJdbcSQLException(String message, String sql, String state, int errorCode,
                                                   Throwable cause, String stackTrace) {
        sql = filterSQL(sql);
        // Use SQLState class value to detect type
        return new SQLException();
    }

    private static String filterSQL(String sql) {
        return sql == null || !sql.contains(HIDE_SQL) ? sql : "-";
    }


    /**
     * Prints up to 100 next exceptions for a specified SQL exception.
     *
     * @param e SQL exception
     * @param s print writer
     */
    public static void printNextExceptions(SQLException e, PrintWriter s) {
        // getNextException().printStackTrace(s) would be very slow
        // if many exceptions are joined
        int i = 0;
        while ((e = e.getNextException()) != null) {
            if (i++ == 100) {
                s.println("(truncated)");
                return;
            }
            s.println(e.toString());
        }
    }

    /**
     * Prints up to 100 next exceptions for a specified SQL exception.
     *
     * @param e SQL exception
     * @param s print stream
     */
    public static void printNextExceptions(SQLException e, PrintStream s) {
        // getNextException().printStackTrace(s) would be very slow
        // if many exceptions are joined
        int i = 0;
        while ((e = e.getNextException()) != null) {
            if (i++ == 100) {
                s.println("(truncated)");
                return;
            }
            s.println(e.toString());
        }
    }

    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    /**
     * Write the exception to the driver manager log writer if configured.
     *
     * @param e the exception
     */
    public static void traceThrowable(Throwable e) {
        PrintWriter writer = DriverManager.getLogWriter();
        if (writer != null) {
            e.printStackTrace(writer);
        }
    }

}

