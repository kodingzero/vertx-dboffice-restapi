package exa.vertx.office;

import io.reactiverse.pgclient.*;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class EofficeDBVerticle extends AbstractVerticle {
    public static final String CONFIG_API_JDBC_HOST="postgres.host";
    public static final String CONFIG_API_JDBC_DBNAME = "postgres.database";
    public static final String CONFIG_API_JDBC_PORT = "postgres.port";
    public static final String CONFIG_API_JDBC_USER = "postgres.user";
    public static final String CONFIG_API_JDBC_PASSWORD = "postgres.password";
    public static final String CONFIG_API_JDBC_MAX_POOL_SIZE = "postgres.max_pool_size";
    public static final String CONFIG_API_SQL_QUERIES_RESOURCE_FILE = "api.sqlqueries.resource.file";


    private static String  ONESIGNAL_APP_ID="push.id";
    private static String  ONESIGNAL_API_KEY="push.key";

    public static final String CONFIG_APIDB_QUEUE = "epimdb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(EofficeDBVerticle.class);

    // tag::loadSqlQueries[]

    private enum SqlQuery {
        GET_LOGIN,
        UPDATE_PLAYER,
        GET_USER_PROFILE,
        GET_MAIL,
        GET_MAIL_TO,
        GET_EMP_TREE,
        GET_GRAPH_MAIL,
        GET_STAFF_NOTIF,
        GET_ATTACHMENT,
        GET_REPORT_BY_MAIL,
        GET_REPORT_BY_NIP,
        GET_DRAFT_CHAT,
        GET_SHARE_DOC,
        POST_DELETE_STAFF,
        POST_MAIL_MREQPLAY,
        POST_MAIL_ATTACH,
        /* OLD QUERY BELOW*/
        GET_INBOX,
        GET_DASHBOARD,
        POST_NOTIFIKASI,
        POST_DISPOSISI,
        POST_UPDATE_MANO,
        POST_INSERT_REPORT,
        POST_MAIL_EVIDENCE,
        UPDATE_INBOX,
        UPDATE_STATUS_INBOX,

    }


    private final HashMap<EofficeDBVerticle.SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {



        String queriesFile = config().getString(CONFIG_API_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-eoffice-mobile.properties");
        }

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_LOGIN, queriesProps.getProperty("get-user-login"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.UPDATE_PLAYER, queriesProps.getProperty("update-user-player"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_USER_PROFILE, queriesProps.getProperty("get-user-profile"));


        /* new query remark with prefix mail*/
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_MAIL, queriesProps.getProperty("get-mail"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_MAIL_TO, queriesProps.getProperty("get-mail-to"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_EMP_TREE, queriesProps.getProperty("get-emp-tree"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_GRAPH_MAIL, queriesProps.getProperty("get-graph-mail"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_STAFF_NOTIF, queriesProps.getProperty("get-staff-notif"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_ATTACHMENT, queriesProps.getProperty("get-attachment"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_REPORT_BY_MAIL, queriesProps.getProperty("get-report-by-mail"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_REPORT_BY_NIP, queriesProps.getProperty("get-report-by-nip"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_SHARE_DOC, queriesProps.getProperty("get-share-doc"));

        sqlQueries.put(EofficeDBVerticle.SqlQuery.GET_DRAFT_CHAT, queriesProps.getProperty("get-draft-chat"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_DELETE_STAFF, queriesProps.getProperty("post-delete-staff"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.UPDATE_INBOX, queriesProps.getProperty("update-inbox"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_DISPOSISI, queriesProps.getProperty("post-staff"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_UPDATE_MANO, queriesProps.getProperty("post-update-mano"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_INSERT_REPORT, queriesProps.getProperty("post-mail-report"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_MAIL_MREQPLAY, queriesProps.getProperty("post-mail-reqplay"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.POST_MAIL_ATTACH, queriesProps.getProperty("post-mail-attach"));



        //sqlQueries.put(SqlQuery.UPDATE_INBOX, queriesProps.getProperty("update-inbox"));
        sqlQueries.put(EofficeDBVerticle.SqlQuery.UPDATE_STATUS_INBOX, queriesProps.getProperty("update-status-inbox"));


    }
    // end::loadSqlQueries[]

    // tag::start[]
    private String oneSignalApiId;
    private String onesignalApiKey;
    private PgPool dbPool;
   //private PgConnection dbConn;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        loadSqlQueries();
        //get token onesignal
        oneSignalApiId = config().getJsonObject("OneSignal").getString(ONESIGNAL_APP_ID,"f95f68a6-ed64-4b75-8d9d-79716a73d5ac");
        onesignalApiKey = config().getJsonObject("OneSignal").getString(ONESIGNAL_API_KEY,"MThiOTYzOTEtMjg0YS00NjhiLWJiMTMtYjlmOGJhNmJkMjJi");



        PgPoolOptions options = new PgPoolOptions()
                .setPort(Integer.parseInt(config().getJsonObject("database").getString(CONFIG_API_JDBC_PORT,"5432")))
                .setHost(config().getJsonObject("database").getString(CONFIG_API_JDBC_HOST,"localhost"))
                .setDatabase(config().getJsonObject("database").getString(CONFIG_API_JDBC_DBNAME,"db-office"))
                .setUser(config().getJsonObject("database").getString(CONFIG_API_JDBC_USER,"postgres"))
                .setPassword(config().getJsonObject("database").getString(CONFIG_API_JDBC_PASSWORD,"admin"))
                .setMaxSize(config().getJsonObject("database").getInteger(CONFIG_API_JDBC_MAX_POOL_SIZE, 30));

        // Create the pooled client
        dbPool = PgClient.pool(vertx, options);



        dbPool.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                startFuture.fail(ar.cause());
            } else {
                vertx.eventBus().consumer(config().getString(CONFIG_APIDB_QUEUE, "epimdb.queue"), this::onMessage);
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
            case "get-user-login":
                getUserLogin(message);
                break;
            case "get-user-profile":
                getUserProfile(message);
                break;
            case "get-mail":
                getMail(message);
                break;
            case "get-mail-to":
                getMailTo(message);
                break;
            case "get-emp-tree":
                getEmpTree(message);
                break;
            case "get-graph-mail":
                getGraphMail(message);
                break;
            case "get-attachment":
                getAttachment(message);
                break;
            case "get-report-by-mail":
                getReportByMail(message);
                break;
            case "get-report-by-nip":
                getReportByNip(message);
                break;
            case "get-draft-chat":
                getDraftChat(message);
                break;
            case "get-share-doc":
                getShareDoc(message);
                break;
            /*case "post-disposisi":
                postDisposisi(message);
                break;
            case "post-report":
                postReport(message);
                break;
            case "post-delete-staff":
                disposeStaff(message);
                break;
            case "post-dispos-notif":
                postDisposisi(message);
                break;
            case "post-notif":
                push(message);
                break;
            case "post-mail-mreqplay":
                postMailReqplay(message);
                break;*/
            default:
                message.fail(EofficeDBVerticle.ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }



    /*   GET METHOD */

    private void getMail(Message<JsonObject> message) {

        LOGGER.info("getMail db");

        String username = message.body().getString("username");
        String mailType  = message.body().getString("mailType");
        long limitRow = Long.valueOf(message.body().getString("limitRow"));
        long nextPage = Long.valueOf(message.body().getString("nextPage"));


        Tuple params = Tuple.of(username,mailType,limitRow,nextPage);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_MAIL), params,res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();
                rows.forEach(arr::add);

                message.reply(new JsonObject().put("mail", arr));

                LOGGER.info(arr.toString());

            } else {
                LOGGER.error("Failure: " + res.cause().getMessage());
            }
        });
    }

    private void getMailTo(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        Tuple params = Tuple.of(mailId);


        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_MAIL_TO),params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();

                Json data = null;
                for (Row row : rows) {
                    data = row.getJson("data");
                }

                arr.add(data.value().toString());
                message.reply(new JsonObject().put("mail",arr.toString()));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getShareDoc(Message<JsonObject> message) {
        String share = message.body().getString("share");

        Tuple params = Tuple.of(share);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_SHARE_DOC),params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();

                Json data = null;
                for (Row row : rows) {
                    data = row.getJson("data");
                }

                arr.add(data.value().toString());
                message.reply(new JsonObject().put("doc",arr.toString()));



            } else {
                reportQueryError(message, res.cause());
            }
        });

    }

    private void getEmpTree(Message<JsonObject> message) {


        String pegaId = message.body().getString("pegaId");

        Tuple params = Tuple.of(pegaId);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_EMP_TREE), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();

                Json data = null;
                for (Row row : rows) {
                    data = row.getJson("data");
                }

                arr.add(data.value().toString());
                message.reply(new JsonObject().put("emp",arr.toString()));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getGraphMail(Message<JsonObject> message) {
        String pegaNip = message.body().getString("pegaNip");


        Tuple params = Tuple.of(pegaNip,pegaNip,pegaNip);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_GRAPH_MAIL), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();
                rows.forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getDraftChat(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");

        Tuple params = Tuple.of(mailId);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_DRAFT_CHAT), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();
                rows.forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getReportByNip(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        Integer manoId = message.body().getInteger("manoId");
        LOGGER.info("getReportByNip : "+mailId);
        LOGGER.info("MANO : "+manoId);
        Tuple params = Tuple.of(mailId,manoId);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_REPORT_BY_NIP), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();
                rows.forEach(arr::add);
                message.reply(new JsonObject().put("data", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getReportByMail(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        LOGGER.info("getReportByMail : "+mailId);

        Tuple params = Tuple.of(mailId);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_REPORT_BY_MAIL), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();
                rows.forEach(arr::add);
                message.reply(new JsonObject().put("report", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getAttachment(Message<JsonObject> message) {
        Integer mailId = message.body().getInteger("mailId");
        LOGGER.info("getAttachment : "+mailId);

        Tuple params = Tuple.of(mailId);

        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_ATTACHMENT), params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();


                Json data = null;
                for (Row row : rows) {
                    data = row.getJson("data");
                }

                arr.add(data.value().toString());
                message.reply(new JsonObject().put("attachment",arr.toString()));

                //message.reply(new JsonObject().put("attachment", arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }


    private void getUserProfile(Message<JsonObject> message) {

        String userName = message.body().getString("userName");
        Tuple params = Tuple.of(userName);


        dbPool.preparedQuery(sqlQueries.get(SqlQuery.GET_USER_PROFILE),params, res -> {
            if (res.succeeded()) {
                PgRowSet rows = res.result();
                JsonArray arr = new JsonArray();

                Json data = null;
                for (Row row : rows) {
                    data = row.getJson("data");
                }

                arr.add(data.value().toString());
               // LOGGER.info("hasil : "+arr.toString().replaceAll("\\\\",""));
                message.reply(new JsonObject().put("data",arr));

            } else {
                reportQueryError(message, res.cause());
            }
        });
    }

    private void getUserLogin(Message<JsonObject> message) {
        JsonObject credential = message.body().getJsonObject("session");

        LOGGER.info("/getuserlogin new :" + credential);

        String user = credential.getString("username");
        String pwd  = credential.getString("password");
        String playerId = credential.getString("playerId");
        LOGGER.info("user/pwd db: "+user+"/"+pwd+"/"+playerId);


        Tuple params = Tuple.of(user,pwd);
        Tuple paramsUpdate = Tuple.of(playerId,user);

        dbPool.getConnection(ar1 ->{
            if (ar1.succeeded()){
                PgConnection dbConn = ar1.result();

                dbConn.preparedQuery(sqlQueries.get(SqlQuery.GET_LOGIN),params,res -> {
                    if (res.succeeded()) {
                        PgRowSet rows = res.result();

                        if (rows.size()==1){
                            LOGGER.info("rows: "+rows.size());
                                message.reply(new JsonObject().put("nip",user).put("succeed",true).put("message", "authenticated"));

                                dbConn.preparedQuery(sqlQueries.get(SqlQuery.UPDATE_PLAYER),paramsUpdate,resUpdate ->{
                                    if (resUpdate.succeeded()){
                                        LOGGER.info("PlayerId with Nip : "+user+" has been update");
                                    }else{
                                        LOGGER.info("Error when update PlayerId with Nip : "+user);
                                    }
                                });

                                dbConn.close();

                        } else{
                            message.reply(new JsonObject().put("succeed",false).put("message", "user or password not authenticated."));
                            LOGGER.info("user password not authenticated");
                        }

                    } else {
                        message.reply(new JsonObject().put("nip",user).put("succeed",false).put("message","network connection error."));
                        reportQueryError(message, res.cause());

                    }
                });
            }
        });

    }



    // end tag::get methods[]

    // tag::onMessage[]
    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void reportQueryError(Message<JsonObject> message, Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(EofficeDBVerticle.ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}
