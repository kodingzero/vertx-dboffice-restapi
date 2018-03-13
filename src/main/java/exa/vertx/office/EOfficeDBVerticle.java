package exa.vertx.office;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Properties;

public class EOfficeDBVerticle extends AbstractVerticle {
    public static final String CONFIG_EPIMDB_JDBC_URL = "epimdb.jdbc.url";
    public static final String CONFIG_EPIMDB_JDBC_DRIVER_CLASS = "epimdb.jdbc.driver_class";
    public static final String CONFIG_EPIMDB_JDBC_USER = "postgres";
    public static final String CONFIG_EPIMDB_JDBC_PASSWORD = "admin";
    public static final String CONFIG_EPIMDB_JDBC_MAX_POOL_SIZE = "epimdb.jdbc.max_pool_size";
    public static final String CONFIG_EPIMDB_SQL_QUERIES_RESOURCE_FILE = "epimdb.sqlqueries.resource.file";

    public static final String CONFIG_EPIMDB_QUEUE = "epimdb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeDatabaseVerticle.class);

    // (...)
    // end::preamble[]

    // tag::loadSqlQueries[]
    private enum SqlQuery {
        GET_USER
    }


    private final HashMap<EOfficeDBVerticle.SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {

        String queriesFile = config().getString(CONFIG_EPIMDB_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/eoffice-db-sql.properties");
        }

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(EOfficeDBVerticle.SqlQuery.GET_USER, queriesProps.getProperty("get-user"));



    }
    // end::loadSqlQueries[]

    // tag::start[]
    private JDBCClient dbClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        loadSqlQueries();

        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:postgresql://localhost:5432/db_office")
                .put("user", "postgres")
                .put("password", "admin")
                .put("max_pool_size", 30)
        );

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
                vertx.eventBus().consumer(config().getString(CONFIG_EPIMDB_QUEUE, "epimdb.queue"), this::onMessage);
                startFuture.complete();
                LOGGER.info("Connection established");

            }
        });


    }

    public void onMessage(Message<JsonObject> message) {

        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified for message with headers {} and body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(OfficeDatabaseVerticle.ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }
        String action = message.headers().get("action");

        switch (action) {
            case "get-user":
                getUser(message);
                break;
            default:
                message.fail(OfficeDatabaseVerticle.ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }



    private void getUser(Message<JsonObject> message) {
        String user = message.body().getString("username");
        String pwd  = message.body().getString("password");

        JsonArray params = new JsonArray().add(user);


        dbClient.queryWithParams(sqlQueries.get(EOfficeDBVerticle.SqlQuery.GET_USER),params, res -> {
            if (res.succeeded()) {
                String candidate = res.result().getRows().get(0).getString("password").replaceFirst("2y", "2a");

                //   System.out.println("candidate : " + pwd + "/" + candidate);

                if (BCrypt.checkpw(pwd,candidate)) {

                    /*List<String> profile = res.result()
                            .getResults()
                            .stream()
                            .map(json -> json.toString())
                            .sorted()
                            .collect(Collectors.toList());*/
                    JsonArray arr = new JsonArray();
                    res.result().getRows().forEach(arr::add);

                    message.reply(new JsonObject().put("profile", arr));
                }else{
                    message.reply(new JsonObject().put("user-reply","user or password doesn't matched."));
                }

            } else {
                reportQueryError(message, res.cause());
            }
        });

    }
    // end::onMessage[]






    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(OfficeDatabaseVerticle.ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}
